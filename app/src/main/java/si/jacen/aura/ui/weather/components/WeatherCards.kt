package si.jacen.aura.ui.weather.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import si.jacen.aura.domain.model.CurrentWeather
import si.jacen.aura.domain.model.DailyItem
import si.jacen.aura.domain.model.HourlyItem
import si.jacen.aura.domain.model.TempUnit
import si.jacen.aura.util.WeatherVisuals

@Composable
fun NowCard(current: CurrentWeather, unit: TempUnit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                WeatherVisuals.emoji(current.iconCode),
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    WeatherVisuals.temp(current.temperature, unit),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    current.text,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            val details = buildList {
                current.feelsLike?.let { add("体感 ${WeatherVisuals.temp(it, unit)}") }
                current.humidity?.let { add("湿度 $it%") }
                if (current.windDir != null) {
                    add("风 ${current.windDir}${current.windScale?.let { " $it 级" } ?: ""}")
                }
                current.precip?.let { add("降水 ${it}mm") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            WeatherVisuals.dateTimeLabel(current.observedAt).takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "观测于 $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                )
            }
        }
    }
}

@Composable
fun HourlyStrip(hourly: List<HourlyItem>, unit: TempUnit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(hourly, key = { it.time }) { item ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        WeatherVisuals.hourLabel(item.time),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                    )
                    Text(WeatherVisuals.emoji(item.iconCode), style = MaterialTheme.typography.titleLarge)
                    Text(
                        WeatherVisuals.temp(item.temperature, unit),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        item.precipProbability?.let { "$it%" } ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 7 天预报：每天一张独立卡片。 */
@Composable
fun DailyList(daily: List<DailyItem>, unit: TempUnit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        daily.forEach { day ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        WeatherVisuals.dateLabel(day.date),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(72.dp),
                    )
                    Text(WeatherVisuals.emoji(day.iconDay), modifier = Modifier.width(34.dp))
                    Text(
                        day.textDay,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    day.precipProbability?.let {
                        Text(
                            "$it%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(40.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                    Text(
                        WeatherVisuals.tempRange(day.tempMin, day.tempMax, unit),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}
