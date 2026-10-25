package si.jacen.aura.backend

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 天气问答：拉取该城市天气上下文（复用缓存/额度防护）→ GLM 依据数据作答。
 * 数据为锚、模型为嘴：回答只允许来自给定数据，杜绝编造。
 */
class AskService(
    private val client: HttpClient,
    private val jwt: QWeatherJwt,
    private val cache: TtlCache,
    private val guard: Guard,
    private val apiHost: String,
    private val llm: LlmClient,
) {

    @Serializable
    data class AskResponse(val answer: String, val generatedAt: String)

    sealed interface Result {
        data class Ok(val body: String) : Result
        data class Error(val status: Int, val body: String) : Result
    }

    /** location 为和风天气格式：经度,纬度 */
    suspend fun ask(location: String, question: String): Result {
        val context = buildContext(location)
            ?: return Result.Error(502, errorBody(502, "Bad Gateway", "获取天气数据失败"))
        if (!guard.tryConsumeLlm()) {
            return Result.Error(429, errorBody(429, "Too Many Requests", "今日问答额度已用尽，明天恢复"))
        }
        val answer = try {
            llm.complete(SYSTEM_PROMPT, "$context\n\n用户问题：$question")
        } catch (e: Exception) {
            return Result.Error(502, errorBody(502, "Bad Gateway", "AI 服务暂时不可用，请稍后再试"))
        }
        val body = Json.encodeToString(
            AskResponse.serializer(),
            AskResponse(answer, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))),
        )
        return Result.Ok(body)
    }

    /** 并行拉 now/24h/7d/空气，失败的部分跳过（不阻塞回答）。 */
    private suspend fun buildContext(location: String): String? = coroutineScope {
        val parts = location.split(",")
        if (parts.size != 2) return@coroutineScope null
        val lon = parts[0].trim().toDoubleOrNull() ?: return@coroutineScope null
        val lat = parts[1].trim().toDoubleOrNull() ?: return@coroutineScope null
        val airPath = String.format(java.util.Locale.US, "%.2f/%.2f", lat, lon)

        val now = async { fetch("/v7/weather/now", "location=$location") }
        val hourly = async { fetch("/v7/weather/24h", "location=$location") }
        val daily = async { fetch("/v7/weather/7d", "location=$location") }
        val air = async { fetch("/airquality/v1/current/$airPath", "") }

        buildString {
            append("【实时】").append(now.await() ?: "无数据").append('\n')
            append("【未来24小时】").append(hourly.await() ?: "无数据").append('\n')
            append("【未来7天】").append(daily.await() ?: "无数据").append('\n')
            append("【空气质量】").append(air.await()?.take(600) ?: "无数据")
        }.take(12000)
    }

    private suspend fun fetch(path: String, query: String): String? {
        if (ProxyRules.isBlocked(path)) return null
        val cacheKey = "$path?$query"
        cache.get(cacheKey)?.let { return String(it.body, Charsets.UTF_8) }
        if (!guard.tryConsumeUpstream()) return null
        val url = "https://$apiHost$path" + if (query.isNotBlank()) "?$query" else ""
        val resp: HttpResponse = try {
            client.get(url) { header(HttpHeaders.Authorization, "Bearer ${jwt.token()}") }
        } catch (e: Exception) {
            return null
        }
        val status = resp.status.value
        val body = resp.decompressedBytes().toString(Charsets.UTF_8)
        if (status == 200) cache.put(cacheKey, status, resp.headers[HttpHeaders.ContentType], body.toByteArray())
        return if (status == 200) body else null
    }

    companion object {
        private val Json = kotlinx.serialization.json.Json { }

        private val SYSTEM_PROMPT = """
            你是天气助手。只依据提供的天气数据回答用户问题，严禁编造数据里没有的信息。
            要求：中文回答，不超过120字；引用具体的时间和数字（如"明天下午3点降水概率60%"）；
            数据不足以回答时直接说明；如涉及健康安全（高温/污染）给出明确建议。
        """.trimIndent()
    }
}
