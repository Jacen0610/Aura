package com.jacen.aura

import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponse(
    val location: Location,
    val current: CurrentWeather,
    val forecast: Forecast? = null
)

@Serializable
data class Location(
    val name: String,
    val region: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val localtime: String
)

@Serializable
data class CurrentWeather(
    val temp_c: Double,
    val feelslike_c: Double,
    val condition: WeatherCondition,
    val humidity: Int,
    val uv: Double,
    val wind_kph: Double,
    val wind_dir: String,
    val pressure_mb: Double,
    val precip_mm: Double,
    val vis_km: Double
)

@Serializable
data class WeatherCondition(
    val text: String,
    val icon: String,
    val code: Int
)

@Serializable
data class Forecast(val forecastday: List<ForecastDay>)

@Serializable
data class ForecastDay(val date: String, val day: Day, val hour: List<Hour>)

@Serializable
data class Day(val maxtemp_c: Double, val mintemp_c: Double, val condition: WeatherCondition)

@Serializable
data class Hour(val time: String, val temp_c: Double, val condition: WeatherCondition, val chance_of_rain: Int)

@Serializable
data class SearchSuggestion(val name: String, val region: String, val country: String, val lat: Double, val lon: Double)

// --- OpenAI / Zhipu GLM 兼容模型 ---
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatResponse(
    val choices: List<ChatChoice>
)

@Serializable
data class ChatChoice(
    val message: ChatMessage
)
