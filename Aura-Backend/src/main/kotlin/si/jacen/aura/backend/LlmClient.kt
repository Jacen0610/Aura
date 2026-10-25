package si.jacen.aura.backend

import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** GLM（智谱开放平台，OpenAI 兼容）对话补全客户端。 */
class LlmClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
) {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class Thinking(val type: String = "disabled")

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double = 0.4,
        val max_tokens: Int = 400,
        // 4.5/4.7 系列为推理模型，简答场景关闭思考模式，避免 token 全耗在 reasoning 上
        val thinking: Thinking? = Thinking("disabled"),
    )

    @Serializable
    private data class ChatResponse(val choices: List<Choice> = emptyList()) {
        @Serializable
        data class Choice(val message: Message? = null)
    }

    /** 生成回答。带重试（免费档高峰限流常见），最终失败抛异常由调用方降级。 */
    suspend fun complete(systemPrompt: String, userPrompt: String): String {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return completeOnce(systemPrompt, userPrompt)
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) delay(800L * (attempt + 1))
            }
        }
        throw lastError!!
    }

    private suspend fun completeOnce(systemPrompt: String, userPrompt: String): String {
        val resp = client.post("$baseUrl/api/paas/v4/chat/completions") {
            contentType(ContentType.Application.Json)
            headers { append(HttpHeaders.Authorization, "Bearer $apiKey") }
            setBody(
                json.encodeToString(
                    ChatRequest.serializer(),
                    ChatRequest(
                        model = model,
                        messages = listOf(
                            Message("system", systemPrompt),
                            Message("user", userPrompt),
                        ),
                    ),
                ),
            )
        }
        val body = resp.bodyAsText()
        if (resp.status.value != 200) {
            throw IllegalStateException("GLM HTTP ${resp.status.value}: ${body.take(200)}")
        }
        val content = json.decodeFromString<ChatResponse>(body)
            .choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("GLM 返回空 content")
        return content.trim()
    }
}
