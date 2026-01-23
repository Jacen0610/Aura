package com.jacen.aura

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface WeatherApi {
    @GET("v1/forecast.json")
    suspend fun getWeatherForecast(
        @Query("key") apiKey: String,
        @Query("q") query: String,
        @Query("days") days: Int = 3,
        @Query("lang") lang: String = "zh"
    ): WeatherResponse

    @GET("v1/search.json")
    suspend fun searchCities(
        @Query("key") apiKey: String,
        @Query("q") query: String,
        @Query("lang") lang: String = "zh"
    ): List<SearchSuggestion>
}

interface AiApi {
    // 智谱 GLM 官方标准对话端点
    @POST("chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") bearerToken: String,
        @Body request: ChatRequest
    ): ChatResponse
}
