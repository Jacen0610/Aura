package si.jacen.aura.ui.weather.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import si.jacen.aura.domain.model.MinutelyPrecip
import si.jacen.aura.domain.model.MinutelyPoint
import si.jacen.aura.domain.model.WeatherWarning
import si.jacen.aura.util.MinutelyRainAnalyzer
import si.jacen.aura.util.WeatherVisuals

/** 官方气象预警横幅：有预警时显示在天气内容最顶部，点击查看详情。 */
@Composable
fun WarningBanner(warning: WeatherWarning, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 横幅底色 = errorContainer 与官方预警色轻混（有官方色时），语义与视觉双对齐
    val official = androidx.compose.ui.graphics.Color(
        warning.red / 255f, warning.green / 255f, warning.blue / 255f,
    )
    val container = if (warning.red + warning.green + warning.blue > 0) {
        androidx.compose.ui.graphics.lerp(
            MaterialTheme.colorScheme.errorContainer, official, 0.25f,
        )
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⚠️", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${warning.typeName}预警 · ${warning.level}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    warning.description.take(38) + if (warning.description.length > 38) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            Text("详情 ›", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** 预警详情弹层：全文 + 生效时段 + 发布单位。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarningDetailSheet(warning: WeatherWarning, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${warning.typeName}预警（${warning.level}）",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    warning.effectiveTime?.let {
                        InfoRow("生效时间", "${formatTime(it)} ~ ${warning.expireTime?.let(::formatTime) ?: ""}")
                    }
                    warning.issuedTime?.let { InfoRow("发布时间", formatTime(it)) }
                    warning.sender?.let { InfoRow("发布单位", it) }
                }
            }
            Text(warning.description, style = MaterialTheme.typography.bodyMedium)
            warning.instruction?.let {
                Text(
                    "防范措施\n$it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatTime(raw: String): String = WeatherVisuals.dateTimeLabel(raw)

/** 未来两小时降水：文本结论卡（点击弹出分钟级时刻详情层）。 */
@Composable
fun MinutelyRainCard(minutely: MinutelyPrecip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val analysis = remember(minutely) { MinutelyRainAnalyzer.analyze(minutely.points) }
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌧️ 未来两小时降水", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    "详情 ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                analysis.conclusion,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                minutely.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 分钟级降水详情层：加高强度分层图表 + 绝对时刻轴 + 关键数据行。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinutelyDetailSheet(minutely: MinutelyPrecip, onDismiss: () -> Unit) {
    val analysis = remember(minutely) { MinutelyRainAnalyzer.analyze(minutely.points) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(analysis.conclusion, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "数据源概括：" + minutely.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IntensityChart(minutely.points)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    analysis.startLabel?.let { DetailRow("开始时间", it) }
                    analysis.endLabel?.let { DetailRow(analysis.endLabelText(), it) }
                    if (!analysis.noRain) DetailRow("降雨时长", "约 ${analysis.totalMinutes} 分钟")
                    DetailRow("最大雨强", String.format(java.util.Locale.US, "%.2f mm/5min", analysis.maxPrecip5m))
                    minutely.updatedAt?.let { DetailRow("数据更新", WeatherVisuals.dateTimeLabel(it)) }
                }
            }
        }
    }
}

private fun si.jacen.aura.util.MinutelyRainAnalyzer.Analysis.endLabelText(): String =
    if (ongoing) "预计停止" else "结束时间"

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * 强度分层柱状图：高 180dp；横向参考线划分 弱(≤0.25)/中(≤0.75)/强(>0.75) mm/5min；
 * X 轴为绝对钟点（起点 / 1 小时 / 2 小时），每 30 分钟浅色刻度线。
 */
@Composable
private fun IntensityChart(points: List<MinutelyPoint>, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val idleColor = barColor.copy(alpha = 0.15f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline

    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = axisColor)
    val xStart = points.firstOrNull()?.let { WeatherVisuals.shortTime(it.fxTime) } ?: ""
    val xMid = points.getOrNull(points.size / 2)?.let { WeatherVisuals.shortTime(it.fxTime) } ?: ""
    val xEnd = points.lastOrNull()?.let { WeatherVisuals.shortTime(it.fxTime) } ?: ""

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        if (points.isEmpty()) return@Canvas
        val topPad = 14.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val chartH = size.height - topPad - bottomPad
        val chartBottom = size.height - bottomPad

        fun precipToHeight(v: Double): Float =
            (chartH * (0.18 + 0.82 * (v / 1.5).coerceAtMost(1.0))).toFloat()

        // 强度分层线：弱(<0.35)/中(0.35~0.75)/强(>0.75) mm/5min，标签置于各带中部
        listOf(0.175 to "弱", 0.55 to "中", 1.1 to "强").forEach { (bandVal, label) ->
            val y = chartBottom - precipToHeight(bandVal)
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            drawText(textMeasurer, label, topLeft = Offset(0f, y - 14.dp.toPx()), style = labelStyle)
        }

        // 30 分钟刻度竖线（每 6 根柱）
        val n = points.size
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (n - 1)) / n
        for (i in 6 until n step 6) {
            val x = i * (barWidth + gap)
            drawLine(
                gridColor.copy(alpha = 0.4f),
                Offset(x, topPad), Offset(x, chartBottom),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // 柱
        points.forEachIndexed { i, p ->
            val h = if (p.precip <= 0.0) chartH * 0.10f else precipToHeight(p.precip)
            drawRoundRect(
                color = if (p.precip <= 0.0) idleColor else barColor,
                topLeft = Offset(i * (barWidth + gap), chartBottom - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }

        // X 轴绝对时刻
        drawText(textMeasurer, xStart, topLeft = Offset(0f, size.height - 16.dp.toPx()), style = labelStyle)
        val midLayout = textMeasurer.measure(xMid, labelStyle)
        drawText(midLayout, topLeft = Offset((size.width - midLayout.size.width) / 2f, size.height - 16.dp.toPx()))
        val endLayout = textMeasurer.measure(xEnd, labelStyle)
        drawText(endLayout, topLeft = Offset(size.width - endLayout.size.width, size.height - 16.dp.toPx()))
    }
}
