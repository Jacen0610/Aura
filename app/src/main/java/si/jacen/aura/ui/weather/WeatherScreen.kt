package si.jacen.aura.ui.weather

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.jacen.aura.ui.components.ErrorView
import si.jacen.aura.ui.components.LoadingView
import si.jacen.aura.ui.components.OfflineBanner
import si.jacen.aura.ui.components.SetupCard
import si.jacen.aura.ui.weather.components.DailyList
import si.jacen.aura.ui.weather.components.HourlyStrip
import si.jacen.aura.ui.weather.components.MinutelyRainCard
import si.jacen.aura.ui.weather.components.WarningBanner
import si.jacen.aura.ui.weather.components.WarningDetailSheet
import si.jacen.aura.ui.weather.components.NowCard
import si.jacen.aura.ui.weather.components.SectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel,
    onNavigateSearch: () -> Unit,
    onNavigateSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 首次打开且无历史城市：按需求直接请求定位权限
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        viewModel.onLocationPermission(grants.values.any { it })
    }

    LaunchedEffect(Unit) {
        if (!permissionRequested && state.city == null) {
            permissionRequested = true
            val fine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                viewModel.onLocationPermission(true)
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    state.city?.displayName ?: "Aura",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateSearch() },
                )
            },
            actions = {
                IconButton(onClick = onNavigateSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索城市")
                }
                IconButton(onClick = onNavigateSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
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
                    state.initialLoading -> LoadingView(Modifier.fillMaxWidth(), "获取天气中…")
                    // 无城市且尚未落定（定位中/权限流转中）显示加载态，不闪错误页
                    state.city == null && !state.needsSetup ->
                        LoadingView(
                            Modifier.fillMaxWidth(),
                            if (state.locating) "正在定位…" else "准备中…",
                        )
                    state.needsSetup -> Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.error?.let {
                            Text(
                                "⚠️ $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        SetupCard(
                            locating = state.locating,
                            onUseLocation = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            },
                            onSearchCity = onNavigateSearch,
                        )
                    }
                    state.weather == null -> ErrorView(
                        message = state.error ?: "暂无数据",
                        onRetry = viewModel::retry,
                    )
                    else -> {
                        val weather = state.weather
                        if (weather != null) {
                            if (state.isOfflineData) OfflineBanner(Modifier.fillMaxWidth())
                            state.error?.let {
                                Text(
                                    "⚠️ $it",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            NowCard(current = weather.current, unit = state.unit)
                            state.minutely?.let { MinutelyRainCard(minutely = it) }
                            SectionLabel("24 小时预报")
                            HourlyStrip(hourly = weather.hourly, unit = state.unit)
                            SectionLabel("7 天预报")
                            DailyList(daily = weather.daily, unit = state.unit)
                            WeatherVisualsCredit()
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun WeatherVisualsCredit() {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "数据来源：和风天气",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
