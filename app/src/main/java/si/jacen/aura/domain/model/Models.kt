package si.jacen.aura.domain.model

import java.util.Locale

/**
 * 统一坐标类型。和风各接口的坐标顺序不一致，全部归一到这里，
 * 由本类的格式化方法负责正确的顺序与精度，UI/ViewModel 不接触原始顺序：
 * - 天气与城市接口：location=经度,纬度（逗号分隔，经度在前）
 * - 空气质量 v1：路径 /{纬度}/{经度}（斜线分隔，纬度在前）
 */
data class Coordinate(val latitude: Double, val longitude: Double) {

    /** 天气/城市接口的 location 参数：经度,纬度 */
    val weatherQuery: String
        get() = "${fmt(longitude)},${fmt(latitude)}"

    /** 空气质量 v1 的路径片段：纬度/经度 */
    val airPath: String
        get() = "${fmt(latitude)}/${fmt(longitude)}"

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)
}

data class GeoCity(
    val id: String,
    val name: String,
    val adm1: String?,
    val adm2: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val coordinate: Coordinate get() = Coordinate(latitude, longitude)

    /** 展示名：东城 · 北京市 */
    val displayName: String
        get() = listOfNotNull(adm2?.takeIf { it != name }, name).distinct().joinToString(" · ")
}

data class CurrentWeather(
    val observedAt: String?,
    val temperature: Int?,
    val feelsLike: Int?,
    val text: String,
    val iconCode: String,
    val windDir: String?,
    val windScale: String?,
    val humidity: Int?,
    val precip: Double?,
    val pressure: Int?,
    val visibilityKm: Double?,
    val cloud: Int?,
)

data class HourlyItem(
    val time: String,
    val temperature: Int?,
    val text: String,
    val iconCode: String,
    val precipProbability: Int?,
)

data class DailyItem(
    val date: String,
    val textDay: String,
    val textNight: String?,
    val iconDay: String,
    val tempMax: Int?,
    val tempMin: Int?,
    val precipProbability: Int?,
    val uvIndex: Int?,
    val sunrise: String?,
    val sunset: String?,
)

data class WeatherBundle(
    val updatedAt: String?,
    val current: CurrentWeather,
    val hourly: List<HourlyItem>,
    val daily: List<DailyItem>,
)

data class AirIndex(
    val code: String,
    val name: String,
    val aqi: Int,
    val display: String,
    val level: String?,
    val category: String,
    val primaryPollutantName: String?,
    val red: Int,
    val green: Int,
    val blue: Int,
    val effect: String?,
    val generalAdvice: String?,
    val sensitiveAdvice: String?,
)

data class AirPollutant(
    val code: String,
    val name: String,
    val value: Double,
    val unit: String,
)

data class AirHourlyItem(
    val time: String,
    val aqi: Int,
    val category: String,
)

data class AirQuality(
    val indexes: List<AirIndex>,
    val pollutants: List<AirPollutant>,
    val hourly: List<AirHourlyItem>,
) {
    /** UI 主展示：优先中国国标，否则第一个 */
    val primaryIndex: AirIndex?
        get() = indexes.firstOrNull { it.code == "cn-mee" } ?: indexes.firstOrNull()
}

enum class TempUnit { CELSIUS, FAHRENHEIT }

// ---------- 日历感知 ----------

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String?,
) {
    val spanHours: Double get() = (endMillis - startMillis) / 3_600_000.0
}

data class CalendarFinding(
    val emoji: String,
    val text: String,
)

data class CalendarInsight(
    val event: CalendarEvent,
    /** 事件所在城市的天气（事件地点反查失败时为家城市天气） */
    val cityWeather: WeatherBundle?,
    val cityName: String?,
    val findings: List<CalendarFinding>,
)
