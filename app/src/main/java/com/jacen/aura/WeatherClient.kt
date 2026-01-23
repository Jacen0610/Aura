package com.jacen.aura

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object WeatherClient {
    const val API_KEY = BuildConfig.WEATHER_API_KEY
    private const val WEATHER_BASE_URL = "https://api.weatherapi.com/"
    
    // 智谱 GLM 基础端点 (必须以 / 结尾以便与 AiApi 中的 chat/completions 拼接)
    private const val ZHIPU_BASE_URL = "https://open.bigmodel.cn/api/paas/v4/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // 显著提升超时限制以应对 AI 生成延迟
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val weatherRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(WEATHER_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val zhipuRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(ZHIPU_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val api: WeatherApi = weatherRetrofit.create(WeatherApi::class.java)
    val aiApi: AiApi = zhipuRetrofit.create(AiApi::class.java)
}
