package si.jacen.aura.domain.repository

import kotlinx.coroutines.flow.Flow
import si.jacen.aura.domain.model.AirQuality
import si.jacen.aura.domain.model.CalendarEvent
import si.jacen.aura.domain.model.Coordinate
import si.jacen.aura.domain.model.GeoCity
import si.jacen.aura.domain.model.MinutelyPrecip
import si.jacen.aura.domain.model.TempUnit
import si.jacen.aura.domain.model.WeatherWarning
import si.jacen.aura.domain.model.WeatherBundle

interface WeatherRepository {
    /** 拉取 now + 24h + 7d 并合并；成功时同时持久化快照（离线兜底）。失败抛出异常。 */
    suspend fun fetch(coordinate: Coordinate): WeatherBundle

    /** 官方气象预警（可能为空列表）。 */
    suspend fun fetchWarnings(coordinate: Coordinate): List<WeatherWarning>

    /** 未来两小时分钟级降水。 */
    suspend fun fetchMinutely(coordinate: Coordinate): MinutelyPrecip?
}

interface AirRepository {
    /** 拉取实时 + 逐小时空气质量并合并；成功时同时持久化快照。失败抛出异常。 */
    suspend fun fetch(coordinate: Coordinate): AirQuality
}

interface GeoRepository {
    /** 城市名搜索，或坐标反查（query 直接透传和风 location 参数）。 */
    suspend fun search(query: String): List<GeoCity>

    suspend fun reverseGeocode(coordinate: Coordinate): GeoCity?
}

interface SettingsRepository {
    val unit: Flow<TempUnit>
    val lastCity: Flow<GeoCity?>
    val calendarEnabled: Flow<Boolean>
    val cachedWeather: Flow<WeatherBundle?>
    val cachedAir: Flow<AirQuality?>

    suspend fun setUnit(unit: TempUnit)
    suspend fun setLastCity(city: GeoCity?)
    suspend fun setCalendarEnabled(enabled: Boolean)
}

/** 日历感知：读取系统日历中枢（Calendar Provider），与具体日历 App 无关。 */
interface CalendarEventReader {
    /** 未来 [withinHours] 小时内的日程（过滤已拒绝/已取消/全天跨天） */
    suspend fun upcomingEvents(withinHours: Int): List<CalendarEvent>
}

/** 天气问答。 */
interface AiRepository {
    suspend fun ask(coordinate: Coordinate, question: String): String
}
