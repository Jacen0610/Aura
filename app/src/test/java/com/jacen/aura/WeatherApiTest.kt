package com.jacen.aura

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class WeatherApiTest {
    @Test
    fun testWeatherForecastApi() = runBlocking {
        // 测试时请手动填入你的 WeatherAPI Key
        val apiKey = "c94aeffa7f924478a5690952262301"
        val location = "Nanjing" // 测试城市

        val json = Json { 
            ignoreUnknownKeys = true 
            coerceInputValues = true
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.weatherapi.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val api = retrofit.create(WeatherApi::class.java)

        try {
            val response = api.getWeatherForecast(apiKey, location)
            println("--- WeatherAPI 测试成功 ---")
            println("城市: ${response.location.name}")
            println("当前温度: ${response.current.temp_c}°C")
            println("天气描述: ${response.current.condition.text}")
            println("预报天数: ${response.forecast?.forecastday?.size ?: 0}")
        } catch (e: Exception) {
            println("--- WeatherAPI 测试失败 ---")
            println("错误原因: ${e.message}")
            e.printStackTrace()
        }
    }
}
