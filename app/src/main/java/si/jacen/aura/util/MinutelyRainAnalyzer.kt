package si.jacen.aura.util

import si.jacen.aura.domain.model.MinutelyPoint

/**
 * 分钟级降水雨窗分析——纯函数推导"几点下雨/下多久"，供卡片结论行与详情层数据行使用。
 */
object MinutelyRainAnalyzer {

    data class Analysis(
        /** 卡片主结论行，如 "14:35 开始下雨 · 持续约 25 分钟" */
        val conclusion: String,
        val startLabel: String?,
        val endLabel: String?,
        /** 降雨总分钟数（每点 5 分钟） */
        val totalMinutes: Int,
        /** 5 分钟最大雨量 mm */
        val maxPrecip5m: Double,
        /** 当前正在下雨（首点即有雨） */
        val ongoing: Boolean,
        val noRain: Boolean,
    )

    fun analyze(points: List<MinutelyPoint>): Analysis {
        val maxPrecip = points.maxOfOrNull { it.precip } ?: 0.0
        val segments = rainSegments(points)
        val totalMinutes = segments.sumOf { it.second - it.first + 1 } * 5

        if (segments.isEmpty()) {
            return Analysis(
                conclusion = "未来两小时无降雨",
                startLabel = null, endLabel = null,
                totalMinutes = 0, maxPrecip5m = maxPrecip,
                ongoing = false, noRain = true,
            )
        }

        val timeOf = { index: Int -> WeatherVisuals.shortTime(points[index].fxTime) }
        val first = segments.first()

        // 正在下雨：雨从窗口起点就开始
        if (first.first == 0) {
            val endLabel = if (first.second < points.lastIndex) timeOf(first.second + 1) else null
            return Analysis(
                conclusion = if (endLabel != null) {
                    "正在下雨 · 预计 $endLabel 停止"
                } else {
                    "正在下雨 · 未来两小时内持续"
                },
                startLabel = null,
                endLabel = endLabel,
                totalMinutes = totalMinutes,
                maxPrecip5m = maxPrecip,
                ongoing = true,
                noRain = false,
            )
        }

        // 将下雨：单段或两段以上
        val startLabel = timeOf(first.first)
        val conclusion = when (segments.size) {
            1 -> {
                val dur = (first.second - first.first + 1) * 5
                "$startLabel 开始下雨 · 持续约 $dur 分钟"
            }
            2 -> {
                val secondStart = timeOf(segments[1].first)
                "$startLabel 和 $secondStart 两波 · 共约 $totalMinutes 分钟"
            }
            else -> "$startLabel 起有 ${segments.size} 波降雨 · 共约 $totalMinutes 分钟"
        }
        return Analysis(
            conclusion = conclusion,
            startLabel = startLabel,
            endLabel = timeOf(segments.last().second),
            totalMinutes = totalMinutes,
            maxPrecip5m = maxPrecip,
            ongoing = false,
            noRain = false,
        )
    }

    /** 连续有雨区段 [start..end]（inclusive，索引） */
    private fun rainSegments(points: List<MinutelyPoint>): List<Pair<Int, Int>> {
        val segments = mutableListOf<Pair<Int, Int>>()
        var start = -1
        points.forEachIndexed { i, p ->
            if (p.precip > 0.0) {
                if (start < 0) start = i
            } else if (start >= 0) {
                segments += start to (i - 1)
                start = -1
            }
        }
        if (start >= 0) segments += start to points.lastIndex
        return segments
    }
}
