package com.jacen.aura

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeminiRepositoryTest {

    @Before
    fun setup() {

        // 2. 初始化 Firebase
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
    }

    @Test
    fun testGeminiAdviceGeneration() = runBlocking {
        val repository = GeminiRepositoryImpl()
        val prompt = "你是一个专业的天气助手，请给出一个简单的穿衣建议。"
        
        println("正在通过代理请求 Gemini API...")
        val result = repository.generateAdvice(prompt)
        
        if (result != null) {
            println("Gemini 建议成功生成: $result")
        } else {
            println("生成失败。请确保：")
            println("1. 你的代理软件已开启 'Allow LAN' 或 '全局模式'")
            println("2. 端口号与代码中一致")
            println("3. Firebase 控制台已启用 Vertex AI API")
        }
    }
}
