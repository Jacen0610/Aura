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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.jacen.aura.domain.model.MinutelyPrecip
import si.jacen.aura.domain.model.WeatherWarning
import si.jacen.aura.util.WeatherVisuals

/** 官方气象预警横幅：有预警时显示在天气内容最顶部，点击查看详情。 */
@Composable
fun WarningBanner(warning: WeatherWarning, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
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

/** 未来两小时分钟级降水卡：概括语 + 24 根 5 分钟柱状时间轴。 */
@Composable
fun MinutelyRainCard(minutely: MinutelyPrecip, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🌧️ 未来两小时降水", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                val rainyMinutes = minutely.points.count { it.precip > 0.0 }
                if (rainyMinutes > 0) {
                    Text(
                        "预计降雨约 ${rainyMinutes * 5} 分钟",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                minutely.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrecipTimeline(minutely.points.map { it.precip })
            Row(Modifier.fillMaxWidth()) {
                Text("现在", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.weight(1f))
                Text("1 小时", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.weight(1f))
                Text("2 小时", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** 24 根柱：整条时间轴用主色浅 tint 保证可见，有雨时段为实心主色、高度∝降水量。 */
@Composable
private fun PrecipTimeline(values: List<Double>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val idleColor = barColor.copy(alpha = 0.18f)
    Canvas(modifier = modifier.fillMaxWidth().height(64.dp)) {
        if (values.isEmpty()) return@Canvas
        val n = values.size
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val h = if (v <= 0.0) {
                size.height * 0.16f
            } else {
                // 0~1.5mm 线性映射到 20%~100% 高度
                (size.height * (0.20 + 0.80 * (v / 1.5).coerceAtMost(1.0))).toFloat()
            }
            drawRoundRect(
                color = if (v <= 0.0) idleColor else barColor,
                topLeft = Offset(i * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}
