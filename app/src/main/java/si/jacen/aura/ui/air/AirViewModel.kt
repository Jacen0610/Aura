package si.jacen.aura.ui.air

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import si.jacen.aura.domain.model.AirQuality
import si.jacen.aura.domain.model.GeoCity
import si.jacen.aura.domain.repository.SettingsRepository
import si.jacen.aura.domain.usecase.GetAirQualityUseCase
import si.jacen.aura.util.userMessage
import javax.inject.Inject

@HiltViewModel
class AirViewModel @Inject constructor(
    private val getAirQuality: GetAirQualityUseCase,
    private val settings: SettingsRepository,
) : ViewModel() {

    data class UiState(
        val city: GeoCity? = null,
        val air: AirQuality? = null,
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val isOfflineData: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedOnce = false

    init {
        viewModelScope.launch {
            settings.lastCity.distinctUntilChanged().collectLatest { city ->
                _uiState.update { it.copy(city = city) }
                if (city != null) load()
                else if (!loadedOnce) _uiState.update { it.copy(loading = false) }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.city != null) load()
    }

    private fun load() {
        val city = _uiState.value.city ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { if (loadedOnce) it.copy(refreshing = true) else it.copy(loading = true) }
            try {
                val air = getAirQuality(city.coordinate)
                loadedOnce = true
                _uiState.update {
                    it.copy(air = air, loading = false, refreshing = false, isOfflineData = false, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val cached = runCatching { settings.cachedAir.first() }.getOrNull()
                loadedOnce = true
                _uiState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        air = cached ?: it.air,
                        isOfflineData = cached != null && it.air == null,
                        error = if (cached == null && it.air == null) e.userMessage() else null,
                    )
                }
            }
        }
    }
}
