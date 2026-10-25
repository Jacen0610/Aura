package si.jacen.aura.domain.usecase

import si.jacen.aura.domain.model.CalendarEvent
import si.jacen.aura.domain.model.CalendarFinding
import si.jacen.aura.domain.model.CalendarInsight
import si.jacen.aura.domain.model.WeatherBundle
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 日历 × 天气匹配引擎——纯函数、全本地、无 LLM。
 * 输入日程与天气数据，输出每条日程值得提醒的发现；日历数据不出手机。
 */
object CalendarInsightEngine {

    /** 标题含这些关键词视为户外活动（温度/空气敏感） */
    private val OUTDOOR_KEYWORDS = listOf(
        "户外", "露营", "烧烤", "爬山", "登山", "徒步", "骑行", "跑", "马拉松",
        "球", "羽毛球", "篮球", "足球", "网球", "钓鱼", "野餐", "摄影", "拍摄",
    )

    /**
     * @param homeCityName 家城市名（判断跨城）
     * @param weatherFor 返回事件地点对应的天气（地点反查失败/同城时回传家城市天气）
     */
    suspend fun match(
        events: List<CalendarEvent>,
        homeCityName: String,
        weatherFor: suspend (CalendarEvent) -> Pair<String, WeatherBundle>?,
    ): List<CalendarInsight> = events.mapNotNull { event ->
        val pair = weatherFor(event) ?: return@mapNotNull null
        val (cityName, weather) = pair
        val useDailyTier = event.allDay ||
            event.startMillis > System.currentTimeMillis() + 24 * 3_600_000L
        val findings = buildList {
            if (useDailyTier) {
                addAll(checkDailyRules(event, weather))
            } else {
                addAll(checkRain(event, weather))
                addAll(checkTempSwing(event, weather))
                addAll(checkOutdoorHeatOrCold(event, weather))
            }
            addAll(checkCrossCity(event, cityName, homeCityName))
        }
        if (findings.isEmpty()) return@mapNotNull null
        CalendarInsight(event = event, cityWeather = weather, cityName = cityName, findings = findings)
    }

    /** 日级规则：全天事件或 24 小时以外的日程，用 7 天日预报做粗粒度匹配。 */
    private fun checkDailyRules(event: CalendarEvent, weather: WeatherBundle): List<CalendarFinding> {
        val date = java.time.Instant.ofEpochMilli(event.startMillis)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        val day = weather.daily.firstOrNull { it.date == date } ?: return emptyList()
        return buildList {
            val pop = day.precipProbability ?: 0
            if (pop >= 50) add(CalendarFinding("🌧️", "当天降水概率 $pop%，如有外出记得带伞"))
            val swing = (day.tempMax ?: return@buildList) - (day.tempMin ?: return@buildList)
            if (swing >= 10) add(CalendarFinding("🌡️", "当天温差 ${swing}°（${day.tempMin}°~${day.tempMax}°），注意增减衣物"))
            if (isOutdoor(event.title) && (day.tempMax ?: 0) >= 32) {
                add(CalendarFinding("🥵", "户外安排遇 ${day.tempMax}° 高温，注意补水防晒"))
            }
        }
    }

    private fun overlappingHours(event: CalendarEvent, weather: WeatherBundle) =
        weather.hourly.mapNotNull { hour ->
            val time = parseTime(hour.time) ?: return@mapNotNull null
            val hourStart = time.toInstant().toEpochMilli()
            val hourEnd = hourStart + 3_600_000L
            if (hourStart < event.endMillis && event.startMillis < hourEnd) hour else null
        }

    private fun checkRain(event: CalendarEvent, weather: WeatherBundle): List<CalendarFinding> {
        val rainy = overlappingHours(event, weather).filter { (it.precipProbability ?: 0) >= 50 }
        if (rainy.isEmpty()) return emptyList()
        val peak = rainy.maxOf { it.precipProbability ?: 0 }
        return listOf(
            CalendarFinding("🌧️", "时段降水概率最高 ${peak}%，记得带伞"),
        )
    }

    private fun checkTempSwing(event: CalendarEvent, weather: WeatherBundle): List<CalendarFinding> {
        val temps = overlappingHours(event, weather).mapNotNull { it.temperature }
        if (temps.size < 2) return emptyList()
        val swing = (temps.max() - temps.min())
        if (swing < 6) return emptyList()
        return listOf(
            CalendarFinding("🌡️", "期间温差 ${swing}°（${temps.min()}°→${temps.max()}°），备件外套"),
        )
    }

    private fun checkOutdoorHeatOrCold(event: CalendarEvent, weather: WeatherBundle): List<CalendarFinding> {
        if (!isOutdoor(event.title)) return emptyList()
        val temps = overlappingHours(event, weather).mapNotNull { it.temperature }
        if (temps.isEmpty()) return emptyList()
        return buildList {
            if (temps.max() >= 32) add(CalendarFinding("🥵", "户外活动遇 ${temps.max()}° 高温，注意补水防晒"))
            if (temps.min() <= 0) add(CalendarFinding("🥶", "户外活动气温低至 ${temps.min()}°，注意保暖"))
        }
    }

    /** 跨城：事件城市与家城市不同时，对比日最高/最低温 */
    private fun checkCrossCity(
        event: CalendarEvent,
        cityName: String,
        homeCityName: String,
    ): List<CalendarFinding> {
        if (cityName == homeCityName || event.location == null) return emptyList()
        return listOf(
            CalendarFinding("🧳", "地点在 $cityName（异地），出门前看一眼当地天气"),
        )
    }

    fun isOutdoor(title: String): Boolean = OUTDOOR_KEYWORDS.any { title.contains(it) }

    private fun parseTime(fxTime: String): OffsetDateTime? = runCatching {
        OffsetDateTime.parse(fxTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }.getOrNull()
}

/** 时间格式化（引擎与 UI 共用） */
fun Long.toEventTimeLabel(): String {
    val time = Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault())
    return time.format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.getDefault()))
}
