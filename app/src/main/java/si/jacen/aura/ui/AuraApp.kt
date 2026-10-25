package si.jacen.aura.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import si.jacen.aura.ui.air.AirScreen
import si.jacen.aura.ui.calendar.CalendarInsightScreen
import si.jacen.aura.ui.search.SearchScreen
import si.jacen.aura.ui.settings.SettingsScreen
import si.jacen.aura.ui.weather.WeatherScreen
import si.jacen.aura.ui.weather.WeatherViewModel

/** 顶部导航目的地（底部导航栏）。 */
data class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val topDestinations = listOf(
    TopDestination("weather", "天气", Icons.Filled.WbSunny, Icons.Outlined.WbSunny),
    TopDestination("air", "空气", Icons.Filled.Air, Icons.Outlined.Air),
    TopDestination("ai", "AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
)

object Routes {
    const val WEATHER = "weather"
    const val AIR = "air"
    const val AI = "ai"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
}

@Composable
fun AuraApp() {
    val navController = rememberNavController()
    val activity = LocalContext.current as ComponentActivity

    // 活动级共享：搜索页选完城市要直接驱动天气页刷新
    val weatherViewModel: WeatherViewModel = hiltViewModel(activity)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        // 底部栏在所有页面恒定显示：若按路由显隐，返回转场时内容区高度会突变，
        // 退出中的页面被瞬间压扁，形成“整页缩小残影”的观感。
        bottomBar = {
            NavigationBar {
                topDestinations.forEach { dest ->
                    val selected = currentRoute == dest.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                if (selected) dest.selectedIcon else dest.unselectedIcon,
                                contentDescription = dest.label,
                            )
                        },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WEATHER,
            modifier = Modifier.padding(padding),
            // 错峰过渡：旧页先淡出完成，新页再淡入，杜绝两页同屏叠影
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 100)) },
            exitTransition = { fadeOut(animationSpec = tween(100)) },
            popEnterTransition = { fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 100)) },
            popExitTransition = { fadeOut(animationSpec = tween(100)) },
        ) {
            composable(Routes.WEATHER) {
                WeatherScreen(
                    viewModel = weatherViewModel,
                    onNavigateSearch = { navController.navigate(Routes.SEARCH) },
                    onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.AIR) {
                AirScreen()
            }
            composable(Routes.AI) {
                CalendarInsightScreen()
            }
            composable(
                Routes.SEARCH,
                enterTransition = { slideInHorizontally(tween(220)) { it / 2 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(tween(200)) { it / 2 } + fadeOut(tween(120)) },
            ) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onCitySelected = { city ->
                        weatherViewModel.onCitySelected(city)
                        navController.popBackStack()
                    },
                )
            }
            composable(
                Routes.SETTINGS,
                enterTransition = { slideInHorizontally(tween(220)) { it / 2 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(tween(200)) { it / 2 } + fadeOut(tween(120)) },
            ) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
