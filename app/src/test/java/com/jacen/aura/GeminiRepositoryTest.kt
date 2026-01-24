package com.jacen.aura

import kotlinx.coroutines.runBlocking
import org.junit.Test

class GeminiRepositoryTest {
    @Test
    fun testGeminiAdviceGeneration() = runBlocking {
        // Note: Real Firebase AI tests usually require an Android environment or specific setup.
        // This is a placeholder test file to demonstrate the usage of GeminiRepositoryImpl.
        val repository = GeminiRepositoryImpl()
        val prompt = "你好，请给出一个简单的穿衣建议。"
        
        println("正在请求 Gemini API...")
        val result = repository.generateAdvice(prompt)
        
        if (result != null) {
            println("Gemini 建议: $result")
        } else {
            println("生成失败，请检查网络或 API 配置。")
        }
    }
}
