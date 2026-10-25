package si.jacen.aura.ui.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val SUGGESTIONS = listOf(
    "今天需要带伞吗",
    "明天适合跑步吗",
    "这周末天气怎么样",
    "现在空气适合开窗吗",
)

/** AI 页：天气问答（数据为锚、模型为嘴）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    onOpenCalendar: () -> Unit,
    viewModel: AskViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.history.size, state.thinking) {
        if (state.history.isNotEmpty()) listState.animateScrollToItem(state.history.size - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = {
                Text(
                    state.city?.displayName?.let { "问天气 · $it" } ?: "问天气",
                    fontWeight = FontWeight.SemiBold,
                )
            },
            actions = {
                IconButton(onClick = onOpenCalendar) {
                    Icon(Icons.Outlined.EventAvailable, contentDescription = "日程天气")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        LazyColumn(
            Modifier.fillMaxSize().weight(1f).padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.history.isEmpty() && !state.thinking) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🌤️", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("问任何天气问题", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "回答基于你所在城市的实时与预报数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.history) { qa ->
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Card(
                        Modifier.fillMaxWidth(0.85f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text(qa.question, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            qa.answer,
                            Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (state.thinking) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("正在结合天气数据思考…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (state.error != null) {
                item {
                    Text(
                        "⚠️ ${state.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // 建议问题
        androidx.compose.foundation.lazy.LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SUGGESTIONS) { s ->
                FilterChip(selected = false, onClick = { viewModel.ask(s) }, label = { Text(s) })
            }
        }

        // 输入行
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("例如：明天几点会停雨？") },
                singleLine = true,
                enabled = !state.thinking,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { viewModel.ask() }),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { viewModel.ask() },
                enabled = !state.thinking && state.input.isNotBlank(),
            ) {
                if (state.thinking) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                }
            }
        }
    }
}
