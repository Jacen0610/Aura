package com.jacen.aura

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.jacen.aura.ui.theme.AuraTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Weather : Screen("weather", "天气预报", Icons.Default.Cloud)
    object AIAdvice : Screen("ai_advice", "AI 穿衣建议", Icons.Default.Checkroom)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuraTheme {
                MainContainer()
            }
        }
    }
}

@Composable
fun MainContainer() {
    val navController = rememberNavController()
    var weatherData by remember { mutableStateOf<WeatherResponse?>(null) }
    var cityName by remember { mutableStateOf("正在定位...") }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val items = listOf(Screen.Weather, Screen.AIAdvice)
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = Screen.Weather.route,
            Modifier.padding(innerPadding)
        ) {
            composable(Screen.Weather.route) {
                WeatherHomeScreen(
                    onWeatherDataUpdated = { data, name ->
                        weatherData = data
                        cityName = name
                    },
                    initialWeatherData = weatherData,
                    initialCityName = cityName
                )
            }
            composable(Screen.AIAdvice.route) {
                AIAdviceScreen(weatherData, cityName)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherHomeScreen(
    onWeatherDataUpdated: (WeatherResponse, String) -> Unit,
    initialWeatherData: WeatherResponse?,
    initialCityName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var localWeatherData by remember { mutableStateOf(initialWeatherData) }
    var localCityName by remember { mutableStateOf(initialCityName) }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            delay(300)
            try {
                suggestions = WeatherClient.api.searchCities(WeatherClient.API_KEY, searchQuery)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            suggestions = emptyList()
        }
    }

    fun updateWeather(query: String) {
        scope.launch {
            try {
                val response = WeatherClient.api.getWeatherForecast(WeatherClient.API_KEY, query)
                localWeatherData = response
                localCityName = response.location.name
                onWeatherDataUpdated(response, response.location.name)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            getCurrentLocation(fusedLocationClient) { lat, lon ->
                updateWeather("$lat,$lon")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (localWeatherData == null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation(fusedLocationClient) { lat, lon ->
                    updateWeather("$lat,$lon")
                }
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().zIndex(1f)) {
            DockedSearchBar(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { isSearchActive = false; updateWeather(it) },
                active = isSearchActive,
                onActiveChange = { isSearchActive = it },
                placeholder = { Text("搜索城市...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(suggestions) { suggestion ->
                        ListItem(
                            headlineContent = { Text(suggestion.name) },
                            supportingContent = { Text("${suggestion.region}, ${suggestion.country}") },
                            modifier = Modifier.clickable {
                                searchQuery = suggestion.name
                                isSearchActive = false
                                updateWeather("${suggestion.lat},${suggestion.lon}")
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (localWeatherData != null) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                MainWeatherCard(localCityName, localWeatherData!!)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "24小时预报", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HourlyForecastSection(localWeatherData!!.forecast?.forecastday?.firstOrNull()?.hour ?: emptyList())
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "天气详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                WeatherDetailGrid(localWeatherData!!.current)
                Spacer(modifier = Modifier.height(32.dp))
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun AIAdviceScreen(weatherData: WeatherResponse?, cityName: String) {
    var advice by remember { mutableStateOf("正在为您生成穿衣建议...") }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(weatherData) {
        if (weatherData != null && !isGenerating) {
            isGenerating = true
            scope.launch {
                try {
                    val current = weatherData.current
                    val prompt = """
                        你是一个专业的穿衣助手。请根据以下天气数据提供详细的穿衣建议。
                        当前温度: ${current.temp_c}°C
                        体感温度: ${current.feelslike_c}°C
                        天气状况: ${current.condition.text}
                        湿度: ${current.humidity}%
                        紫外线指数: ${current.uv}
                        风速: ${current.wind_kph} km/h
                        请从上衣、下装、鞋子、配饰（如雨具、太阳镜）等方面给出建议，语气要友好亲切。
                    """.trimIndent()

                    val request = ChatRequest(
                        model = "glm-4.5-flash",
                        messages = listOf(
                            ChatMessage(role = "system", content = "你是一个专业的穿衣建议助手。"),
                            ChatMessage(role = "user", content = prompt)
                        ),
                        temperature = 0.7
                    )

                    val response = WeatherClient.aiApi.getChatCompletion(
                        bearerToken = "Bearer ${BuildConfig.ZHIPU_API_KEY}",
                        request = request
                    )
                    advice = response.choices.firstOrNull()?.message?.content ?: "抱歉，暂时无法生成建议。"
                } catch (e: Exception) {
                    advice = "请求超时或失败，请重试。"
                    e.printStackTrace()
                } finally {
                    isGenerating = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "AI 智能穿衣建议", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text = "基于 $cityName 的当前天气", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                if (isGenerating && advice == "正在为您生成穿衣建议...") {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    Text(text = advice, style = MaterialTheme.typography.bodyLarge, lineHeight = 28.sp)
                }
            }
        }
    }
}

fun translateWindDir(dir: String): String {
    return when (dir.uppercase()) {
        "N" -> "北风"
        "S" -> "南风"
        "E" -> "东风"
        "W" -> "西风"
        "NE" -> "东北风"
        "NW" -> "西北风"
        "SE" -> "东南风"
        "SW" -> "西南风"
        "NNE" -> "偏北风"
        "ENE" -> "偏东风"
        "ESE" -> "偏东风"
        "SSE" -> "偏南风"
        "SSW" -> "偏南风"
        "WSW" -> "偏西风"
        "WNW" -> "偏西风"
        "NNW" -> "偏北风"
        else -> dir
    }
}

@Composable
fun MainWeatherCard(city: String, weather: WeatherResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(text = city, style = MaterialTheme.typography.titleMedium)
            }
            Text(text = "${weather.current.temp_c.toInt()}°", style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, fontWeight = FontWeight.Medium))
            Text(text = weather.current.condition.text, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "体感 ${weather.current.feelslike_c.toInt()}° | UV ${weather.current.uv.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun HourlyForecastSection(hours: List<Hour>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(hours) { hour ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = hour.time.split(" ")[1], style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(text = "${hour.temp_c.toInt()}°", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(text = "${hour.chance_of_rain}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun WeatherDetailGrid(current: CurrentWeather) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailCard(Modifier.weight(1f), "体感温度", "${current.feelslike_c}°C", Icons.Default.WbSunny)
            DetailCard(Modifier.weight(1f), "湿度", "${current.humidity}%", Icons.Default.WaterDrop)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailCard(Modifier.weight(1f), "风速", "${current.wind_kph} km/h", Icons.Default.Air)
            DetailCard(Modifier.weight(1f), "紫外线", "${current.uv}", Icons.Default.WbSunny)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailCard(Modifier.weight(1f), "降水量", "${current.precip_mm} mm", Icons.Default.WaterDrop)
            DetailCard(Modifier.weight(1f), "能见度", "${current.vis_km} km", Icons.Default.Visibility)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailCard(Modifier.weight(1f), "气压", "${current.pressure_mb} mb", Icons.Default.Compress)
            DetailCard(Modifier.weight(1f), "风向", translateWindDir(current.wind_dir), Icons.Default.Air)
        }
    }
}

@Composable
fun DetailCard(modifier: Modifier, label: String, value: String, icon: ImageVector) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun getCurrentLocation(client: com.google.android.gms.location.FusedLocationProviderClient, onLocationFound: (Double, Double) -> Unit) {
    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).addOnSuccessListener { it?.let { onLocationFound(it.latitude, it.longitude) } }
}
