package si.jacen.aura.util

import si.jacen.aura.domain.model.TempUnit
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** 展示层格式化与图标映射（纯函数，便于单测）。 */
object WeatherVisuals {

    /** 和风图标代码 → emoji。按量级近似映射即可满足展示。 */
    fun emoji(iconCode: String): String = when (iconCode.toIntOrNull()) {
        100 -> "☀️"
        101 -> "🌤️"
        102, 103 -> "⛅"
        104, 153 -> "☁️"
        150 -> "🌙"
        151, 152 -> "🌙"
        in 300..303 -> "🌦️"
        304, 302 -> "⛈️"
        in 305..399 -> "🌧️"
        in 400..499 -> "❄️"
        in 500..515 -> "🌫️"
        507, 508 -> "💨"
        900 -> "🥵"
        901 -> "🥶"
        else -> "🌡️"
    }

    /** fxTime（2026-10-19T01:00+08:00）→ "01时"；解析失败原样返回。 */
    fun hourLabel(fxTime: String): String = runCatching {
        OffsetDateTime.parse(fxTime).format(DateTimeFormatter.ofPattern("HH时"))
    }.getOrDefault(fxTime)

    /** yyyy-MM-dd → "今天 / 明天 / 10-21 周三"。 */
    fun dateLabel(date: String): String = runCatching {
        val d = LocalDate.parse(date)
        val today = LocalDate.now()
        when (d) {
            today -> "今天"
            today.plusDays(1) -> "明天"
            else -> d.format(DateTimeFormatter.ofPattern("MM-dd")) + " " +
                dayOfWeekLabel(d.dayOfWeek.value)
        }
    }.getOrDefault(date)

    private fun dayOfWeekLabel(value: Int): String =
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[value - 1]

    /** 温度显示：6° / 43°（华氏换算）。 */
    fun temp(celsius: Int?, unit: TempUnit): String {
        if (celsius == null) return "--"
        val value = when (unit) {
            TempUnit.CELSIUS -> celsius
            TempUnit.FAHRENHEIT -> celsius * 9 / 5 + 32
        }
        return "$value°"
    }

    fun tempRange(min: Int?, max: Int?, unit: TempUnit): String =
        "${temp(min, unit)} ~ ${temp(max, unit)}"

    /** fxTime → "HH:mm"。 */
    fun shortTime(fxTime: String): String = runCatching {
        OffsetDateTime.parse(fxTime).format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault(fxTime)

    /** obsTime/updateTime → "MM-dd HH:mm"。 */
    fun dateTimeLabel(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return runCatching {
            OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        }.getOrDefault(raw)
    }
}
