package si.jacen.aura.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import si.jacen.aura.domain.model.CalendarInsight
import si.jacen.aura.domain.repository.CalendarEventReader
import si.jacen.aura.domain.repository.GeoRepository
import si.jacen.aura.domain.repository.SettingsRepository
import si.jacen.aura.domain.repository.WeatherRepository
import si.jacen.aura.domain.usecase.CalendarInsightEngine
import si.jacen.aura.domain.usecase.toEventTimeLabel
import javax.inject.Inject

/**
 * 日历感知：日程 × 天气全本地匹配（日历数据不上传）。
 * 事件地点含城市名时经和风 Geo 反查该城市天气（仅传地点文字，不传日程标题）。
 */
@HiltViewModel
class CalendarInsightViewModel @Inject constructor(
    private val calendarReader: CalendarEventReader,
    private val weatherRepository: WeatherRepository,
    private val geoRepository: GeoRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    data class UiState(
        val enabled: Boolean = false,
        val hasCity: Boolean = false,
        val loading: Boolean = false,
        val insights: List<CalendarInsight> = emptyList(),
        /** 未触发提醒的日程（展示明细，让用户感知"读到了日程"） */
        val quietEvents: List<si.jacen.aura.domain.model.CalendarEvent> = emptyList(),
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val city = settings.lastCity.first()
            _uiState.update { it.copy(enabled = settings.calendarEnabled.first(), hasCity = city != null) }
            if (_uiState.value.enabled && city != null) load()
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setCalendarEnabled(enabled)
            _uiState.update { it.copy(enabled = enabled) }
            if (enabled) load() else _uiState.update { it.copy(insights = emptyList(), quietEvents = emptyList()) }
        }
    }

    fun retry() = load()

    /** 地点文字 → 城市天气；反查失败或与家同城时返回家城市天气。 */
    private suspend fun resolveCityWeather(
        location: String,
        home: si.jacen.aura.domain.model.GeoCity,
        homeWeather: si.jacen.aura.domain.model.WeatherBundle,
    ): Pair<String, si.jacen.aura.domain.model.WeatherBundle> {
        val target = runCatching { geoRepository.search(location).firstOrNull() }.getOrNull()
        return if (target != null && target.id != home.id) {
            runCatching { target.name to weatherRepository.fetch(target.coordinate) }.getOrDefault(home.name to homeWeather)
        } else {
            home.name to homeWeather
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val city = settings.lastCity.first() ?: run {
                    _uiState.update { it.copy(loading = false, hasCity = false) }
                    return@launch
                }
                val events = calendarReader.upcomingEvents(withinHours = 7 * 24)
                android.util.Log.d("AuraCalendar", "读取到 ${events.size} 条日程")
                if (events.isEmpty()) {
                    _uiState.update { it.copy(loading = false, insights = emptyList(), quietEvents = emptyList()) }
                    return@launch
                }
                val homeWeather = weatherRepository.fetch(city.coordinate)
                // 异地事件：用地点文字反查城市并取当地天气（反查只传地点，不传日程标题）
                val cityWeatherCache = mutableMapOf<String, Pair<String, si.jacen.aura.domain.model.WeatherBundle>>()
                val insights = CalendarInsightEngine.match(
                    events = events,
                    homeCityName = city.name,
                    weatherFor = { event ->
                        val location = event.location
                        if (location == null) {
                            city.name to homeWeather
                        } else {
                            cityWeatherCache.getOrPut(location) {
                                resolveCityWeather(location, city, homeWeather)
                            }
                        }
                    },
                )
                _uiState.update {
                    it.copy(
                        loading = false,
                        insights = insights,
                        quietEvents = events.filter { e -> insights.none { i -> i.event.id == e.id } },
                        hasCity = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = "日程天气分析失败：${e.message ?: "未知错误"}") }
            }
        }
    }
}
