package com.jacen.aura

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class ZhipuApiTest {
    @Test
    fun testZhipuApi() = runBlocking {
        val apiKey = "YOUR_API_KEY_HERE" // 测试时请手动填入
        
        val json = Json { ignoreUnknownKeys = true }
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://open.bigmodel.cn/api/paas/v4/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val api = retrofit.create(AiApi::class.java)

        val request = ChatRequest(
            model = "glm-4-flash",
            messages = listOf(
                ChatMessage(role = "user", content = "你好，请回复'连接成功'")
            )
        )

        try {
            val response = api.getChatCompletion("Bearer $apiKey", request)
            println("API 响应: ${response.choices.first().message.content}")
        } catch (e: Exception) {
            println("调用失败: ${e.message}")
            e.printStackTrace()
        }
    }
}
