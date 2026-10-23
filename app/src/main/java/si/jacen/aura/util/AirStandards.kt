package si.jacen.aura.util

import si.jacen.aura.domain.model.AirPollutant
import kotlin.math.roundToInt

/**
 * 单项污染物浓度分级，依据中国《环境空气质量指数（AQI）技术规定（试行）》
 * HJ 633-2012（污染物浓度限值对应 GB 3095-2012）。
 *
 * 说明：API 返回的是实时浓度，此处按标准中对应平均时限的浓度区间对照，
 * 用于让用户理解"这个数字处在什么水平"，非官方实时 AQI。
 */
object AirStandards {

    private data class Spec(
        /** 浓度分级边界（升序），相邻边界构成一个级别区间 */
        val boundaries: List<Double>,
        val expectedUnit: String,
    )

    // IAQI 边界：0,50,100,150,200,300,400,500，与各级浓度边界一一对应
    private val iaqiBoundaries = listOf(0, 50, 100, 150, 200, 300, 400, 500)

    private val categories = listOf("优", "良", "轻度污染", "中度污染", "重度污染", "严重污染", "严重污染")

    // 各级别官方推荐色（与生态环境部 AQI 配色一致）
    val bandColors = listOf(
        0xFF00E400, 0xFFFFFF00, 0xFFFF7E00, 0xFFFF0000, 0xFF8F3F97, 0xFF7E0023, 0xFF7E0023,
    )

    private val specs = mapOf(
        "pm2p5" to Spec(listOf(0.0, 35.0, 75.0, 115.0, 150.0, 250.0, 350.0, 500.0), "μg/m³"),
        "pm10" to Spec(listOf(0.0, 50.0, 150.0, 250.0, 350.0, 420.0, 500.0, 600.0), "μg/m³"),
        "so2" to Spec(listOf(0.0, 50.0, 150.0, 475.0, 800.0, 1600.0, 2080.0), "μg/m³"),
        "no2" to Spec(listOf(0.0, 40.0, 80.0, 180.0, 280.0, 565.0, 750.0, 940.0), "μg/m³"),
        // O3 采用 8 小时滑动平均区间（标准中 265~800 对应 IAQI 201~300，封顶）
        "o3" to Spec(listOf(0.0, 100.0, 160.0, 215.0, 265.0, 800.0), "μg/m³"),
        "co" to Spec(listOf(0.0, 2.0, 4.0, 14.0, 24.0, 36.0, 48.0, 60.0), "mg/m³"),
    )

    data class BandDisplay(
        val category: String,
        val colorHex: Long,
        val rangeText: String,
        val iaqiRangeText: String,
        val isCurrent: Boolean,
    )

    data class Level(
        val iaqi: Int,
        val category: String,
        val colorHex: Long,
        val bands: List<BandDisplay>,
    )

    /** 无对应国标分级（如 NO、NMHC）或单位不符时返回 null。 */
    fun evaluate(pollutant: AirPollutant): Level? {
        val spec = specs[pollutant.code] ?: return null
        if (!unitMatches(pollutant.unit, spec.expectedUnit)) return null

        val value = pollutant.value
        val boundaries = spec.boundaries
        val last = boundaries.lastIndex

        // 定位所在区间：i 满足 boundaries[i] < v <= boundaries[i+1]；超出上限取最后一级并封顶 IAQI
        var segment = -1
        for (i in 0 until last) {
            if (value <= boundaries[i + 1]) {
                segment = i
                break
            }
        }
        if (segment == -1) segment = last - 1

        val iaqi = if (value >= boundaries.last()) {
            iaqiBoundaries.last()
        } else {
            val cLo = boundaries[segment]
            val cHi = boundaries[segment + 1]
            val iLo = iaqiBoundaries[segment]
            val iHi = iaqiBoundaries[segment + 1]
            ((iHi - iLo) / (cHi - cLo) * (value - cLo) + iLo).roundToInt()
        }

        val bands = (0 until last).map { i ->
            BandDisplay(
                category = categories[i],
                colorHex = bandColors[i],
                rangeText = formatRange(boundaries[i], boundaries[i + 1]),
                iaqiRangeText = "${iaqiBoundaries[i]}~${iaqiBoundaries[i + 1]}",
                isCurrent = i == segment,
            )
        }

        return Level(
            iaqi = iaqi,
            category = categories[segment],
            colorHex = bandColors[segment],
            bands = bands,
        )
    }

    /** 官方标准单位与 API 返回单位的宽松比对（μ/micro、³/3 等写法差异）。 */
    private fun unitMatches(actual: String, expected: String): Boolean {
        fun normalize(u: String) = u.lowercase().replace("µ", "μ").replace("³", "3").replace(" ", "")
        return normalize(actual) == normalize(expected)
    }

    private fun formatRange(lo: Double, hi: Double): String {
        fun fmt(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
        return "${fmt(lo)}~${fmt(hi)}"
    }
}
