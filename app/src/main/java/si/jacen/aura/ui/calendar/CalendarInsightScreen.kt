package si.jacen.aura.ui.calendar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.jacen.aura.domain.model.CalendarInsight
import si.jacen.aura.domain.usecase.toEventTimeLabel

/** AI 页：日历感知（日程 × 天气，全本地计算）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarInsightScreen(viewModel: CalendarInsightViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setEnabled(granted)
    }

    val requestEnable: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.setEnabled(true) else permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (state.enabled && !granted) {
            // 曾开启但权限被收回：关闭开关避免空转
            viewModel.setEnabled(false)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("日程天气", fontWeight = FontWeight.SemiBold) },
            actions = {
                Text(
                    "日历数据仅在本机处理",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 16.dp),
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.EventAvailable,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text("日历感知", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = state.enabled,
                onCheckedChange = { enabled ->
                    if (enabled) requestEnable() else viewModel.setEnabled(false)
                },
            )
        }

        when {
            !state.enabled -> EnableHint(onEnable = requestEnable)
            !state.hasCity -> SimpleHint("先在天气页选好城市，再来开启日程提醒")
            state.loading -> SimpleHint("正在分析未来 48 小时的日程与天气…")
            state.error != null -> Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = viewModel::retry) { Text("重试") }
            }
            state.insights.isEmpty() && state.quietEvents.isEmpty() -> SimpleHint(
                "未来 48 小时没有日程。\n在任意日历 App 里建几个日程试试（写入系统日历的都行）",
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.insights, key = { it.event.id }) { insight ->
                    InsightCard(insight)
                }
                if (state.quietEvents.isNotEmpty()) {
                    item {
                        Text(
                            "天气平稳的日程",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    items(state.quietEvents, key = { it.id }) { event ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (event.allDay) "${event.startMillis.toEventTimeLabel().substringBefore(" ")} 全天" else event.startMillis.toEventTimeLabel(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(110.dp),
                                )
                                Text(
                                    event.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "✓ 平稳",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightCard(insight: CalendarInsight) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(insight.event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                buildString {
                    if (insight.event.allDay) {
                        append(insight.event.startMillis.toEventTimeLabel().substringBefore(" "))
                        append(" · 全天")
                    } else {
                        append(insight.event.startMillis.toEventTimeLabel())
                        append(" ~ ")
                        append(
                            java.time.Instant.ofEpochMilli(insight.event.endMillis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                        )
                    }
                    insight.cityName?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            insight.findings.forEach {
                Text("${it.emoji} ${it.text}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EnableHint(onEnable: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("📅", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text("打开开关，提前知道日程撞上坏天气", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "读取系统日历未来 48 小时的日程，与天气交叉分析：\n雨天带伞 · 温差备衣 · 户外高温 · 异地提醒\n\n日历数据只在本机处理，永不联网",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onEnable) { Text("开启日历感知") }
    }
}

@Composable
private fun SimpleHint(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
