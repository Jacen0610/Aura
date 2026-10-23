package si.jacen.aura.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import si.jacen.aura.domain.model.GeoCity
import si.jacen.aura.domain.model.TempUnit
import si.jacen.aura.domain.model.WeatherBundle
import si.jacen.aura.domain.repository.SettingsRepository
import si.jacen.aura.domain.usecase.GetWeatherUseCase
import si.jacen.aura.domain.usecase.LocateAndResolveCityUseCase
import si.jacen.aura.util.userMessage
import javax.inject.Inject

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val getWeather: GetWeatherUseCase,
    private val locateAndResolveCity: LocateAndResolveCityUseCase,
    private val settings: SettingsRepository,
) : ViewModel() {

    data class UiState(
        val city: GeoCity? = null,
        val weather: WeatherBundle? = null,
        val unit: TempUnit = TempUnit.CELSIUS,
        val initialLoading: Boolean = true,
        val refreshing: Boolean = false,
        val locating: Boolean = false,
        val needsSetup: Boolean = false,
        val isOfflineData: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            launch {
                settings.unit.collect { unit -> _uiState.update { it.copy(unit = unit) } }
            }
            val lastCity = settings.lastCity.first()
            if (lastCity != null) {
                _uiState.update { it.copy(city = lastCity) }
                load(initial = true)
            } else {
                // 无历史城市：等 UI 层定位授权回调（onLocationPermission）驱动
                _uiState.update { it.copy(initialLoading = false) }
            }
        }
    }

    /** UI 权限回调：已授权尝试定位反查；拒绝则引导手动搜索。 */
    fun onLocationPermission(granted: Boolean) {
        if (!granted) {
            _uiState.update { it.copy(needsSetup = true, locating = false) }
            return
        }
        if (_uiState.value.locating) return
        viewModelScope.launch {
            _uiState.update { it.copy(locating = true, error = null) }
            // 反查城市要走网络，后端不可达会抛异常——必须吞掉并降级，否则未捕获异常直接闪退
            val city = try {
                locateAndResolveCity()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (city == null) {
                _uiState.update {
                    it.copy(
                        locating = false,
                        needsSetup = true,
                        error = "定位或网络失败：请检查网络后重试，或手动搜索城市",
                    )
                }
            } else {
                settings.setLastCity(city)
                _uiState.update { it.copy(city = city, locating = false, needsSetup = false) }
                load(initial = true)
            }
        }
    }

    /** 搜索页选定的城市。 */
    fun onCitySelected(city: GeoCity) {
        viewModelScope.launch {
            settings.setLastCity(city)
            _uiState.update { it.copy(city = city, needsSetup = false, error = null) }
            load(initial = true)
        }
    }

    fun refresh() {
        if (_uiState.value.city != null) load(initial = false)
    }

    fun retry() {
        if (_uiState.value.city != null) load(initial = true)
    }

    private fun load(initial: Boolean) {
        val city = _uiState.value.city ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                when {
                    initial && it.weather == null -> it.copy(initialLoading = true, error = null)
                    else -> it.copy(refreshing = true)
                }
            }
            try {
                val bundle = getWeather(city.coordinate)
                _uiState.update {
                    it.copy(
                        weather = bundle,
                        initialLoading = false,
                        refreshing = false,
                        isOfflineData = false,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val cached = runCatching { settings.cachedWeather.first() }.getOrNull()
                _uiState.update {
                    it.copy(
                        initialLoading = false,
                        refreshing = false,
                        weather = cached ?: it.weather,
                        isOfflineData = cached != null && it.weather == null,
                        error = if (cached == null && it.weather == null) e.userMessage() else null,
                    )
                }
            }
        }
    }
}
