package si.jacen.aura.ui.air

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.jacen.aura.domain.model.AirHourlyItem
import si.jacen.aura.domain.model.AirIndex
import si.jacen.aura.domain.model.AirPollutant
import si.jacen.aura.ui.components.ErrorView
import si.jacen.aura.ui.components.LoadingView
import si.jacen.aura.ui.components.OfflineBanner
import si.jacen.aura.ui.weather.components.SectionLabel
import si.jacen.aura.util.AirStandards
import si.jacen.aura.util.WeatherVisuals

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirScreen(viewModel: AirViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedPollutant by remember { mutableStateOf<AirPollutant?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    state.city?.displayName?.let { "空气 · $it" } ?: "空气",
                    fontWeight = FontWeight.SemiBold,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                when {
                    state.loading -> LoadingView(Modifier.fillMaxWidth(), "获取空气质量中…")
                    state.air == null -> ErrorView(message = state.error ?: "暂无数据")
                    else -> {
                        val air = state.air
                        if (air != null) {
                            if (state.isOfflineData) OfflineBanner(Modifier.fillMaxWidth())
                            air.primaryIndex?.let { primary ->
                                AqiCard(
                                    index = primary,
                                    onOpenPrimaryPollutant = {
                                        air.pollutants.firstOrNull { p -> p.name == primary.primaryPollutantName }
                                            ?.let { selectedPollutant = it }
                                    },
                                )
                            }
                            air.pollutants.takeIf { it.isNotEmpty() }?.let {
                                SectionLabel("污染物浓度")
                                PollutantGrid(it, onClick = { selectedPollutant = it })
                            }
                            if (air.hourly.isNotEmpty()) {
                                SectionLabel("未来 24 小时 AQI")
                                AirHourlyRow(air.hourly)
                            }
                            HealthAdviceCard(air.primaryIndex)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    selectedPollutant?.let { pollutant ->
        PollutantDetailSheet(
            pollutant = pollutant,
            onDismiss = { selectedPollutant = null },
        )
    }
}

/** 污染物详情底部弹层：当前等级 + 单项 IAQI + 国标分级区间表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PollutantDetailSheet(pollutant: AirPollutant, onDismiss: () -> Unit) {
    val level = remember(pollutant) { AirStandards.evaluate(pollutant) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(pollutant.name, style = MaterialTheme.typography.titleLarge)
            if (level == null) {
                Text(
                    "当前浓度 ${formatConcentration(pollutant.value)} ${pollutant.unit}\n该污染物暂无国标分级对照",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else {
                val levelColor = Color(level.colorHex)
                val lightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
                Text(
                    "${formatConcentration(pollutant.value)} ${pollutant.unit}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = levelColor.asTextColor(lightTheme),
                )
                Text(
                    "${level.category} · 单项指数 IAQI ${level.iaqi}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    level.bands.forEach { band ->
                        val bandColor = Color(band.colorHex)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (band.isCurrent) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(bandColor)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                band.category,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${band.rangeText} ${pollutant.unit}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(16.dp))
                            if (band.isCurrent) {
                                Text(
                                    "当前",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
                Text(
                    "分级依据：生态环境部《环境空气质量指数（AQI）技术规定》HJ 633-2012。\n实时浓度按标准对应时限的浓度区间对照，供理解数值水平参考。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AqiCard(index: AirIndex, onOpenPrimaryPollutant: () -> Unit) {
    val aqiColor = Color(index.red / 255f, index.green / 255f, index.blue / 255f)
    val lightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    // 容器底色 = surface 与 AQI 色轻混（16%），柔和而非整块纯色
    val container = lerp(MaterialTheme.colorScheme.surface, aqiColor, 0.16f)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpenPrimaryPollutant),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                index.display,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = aqiColor.asTextColor(lightTheme),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                index.category,
                style = MaterialTheme.typography.titleMedium,
                color = aqiColor.onBandColor(),
                modifier = Modifier
                    .background(aqiColor, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                buildString {
                    append(index.name)
                    index.primaryPollutantName?.let { append(" · 首要污染物：").append(it) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "点按污染物卡片查看国标分级 ›",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun PollutantGrid(pollutants: List<AirPollutant>, onClick: (AirPollutant) -> Unit) {
    pollutants.chunked(3).forEach { rowItems ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowItems.forEach { p ->
                // 按国标等级着色：容器 = surface 与等级官方色 16% 轻混，数值/等级用等级色
                val level = remember(p) { AirStandards.evaluate(p) }
                val lightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
                val levelColor = level?.let { Color(it.colorHex) }
                val textColor = levelColor?.asTextColor(lightTheme)
                Card(
                    Modifier.weight(1f).clickable { onClick(p) },
                    colors = if (levelColor != null) {
                        CardDefaults.cardColors(
                            containerColor = lerp(MaterialTheme.colorScheme.surface, levelColor, 0.16f),
                        )
                    } else {
                        CardDefaults.cardColors()
                    },
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            p.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatConcentration(p.value),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor ?: Color.Unspecified,
                        )
                        if (level != null && textColor != null) {
                            Text(
                                "${p.unit} · ${level.category}",
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor,
                            )
                        } else {
                            Text(
                                p.unit,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Text(
                            "查看分级 ›",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * 官方等级色做文字色时按背景明暗双向自适应，保证可读性：
 * - 浅色主题：过亮的色（黄/亮绿/橙）向黑压暗；
 * - 深色主题：过暗的色（严重污染的褐红）向白提亮。
 */
private fun Color.asTextColor(lightTheme: Boolean): Color {
    val lum = 0.299f * red + 0.587f * green + 0.114f * blue
    return if (lightTheme) {
        if (lum <= 0.45f) this
        else lerp(this, Color.Black, ((lum - 0.42f) / lum).coerceIn(0f, 0.6f))
    } else {
        if (lum >= 0.18f) this
        else lerp(this, Color.White, ((0.20f - lum) / 0.20f).coerceIn(0f, 0.5f))
    }
}

/** 等级色胶囊的文字色：亮底黑字、暗底白字（官方 AQI 配色的通行做法）。 */
private fun Color.onBandColor(): Color =
    if (0.299f * red + 0.587f * green + 0.114f * blue > 0.6f) Color.Black else Color.White

private fun formatConcentration(value: Double): String =
    if (value >= 100) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)

@Composable
private fun AirHourlyRow(hourly: List<AirHourlyItem>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(hourly, key = { it.time }) { item ->
            Card {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        WeatherVisuals.shortTime(item.time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        item.aqi.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        item.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthAdviceCard(index: AirIndex?) {
    if (index == null) return
    val advices = buildList {
        index.effect?.let { add("健康影响" to it) }
        index.generalAdvice?.let { add("一般人群" to it) }
        index.sensitiveAdvice?.let { add("敏感人群" to it) }
    }
    if (advices.isEmpty()) return

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            advices.forEach { (title, body) ->
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
