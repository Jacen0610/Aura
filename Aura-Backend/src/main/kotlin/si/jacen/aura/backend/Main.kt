package si.jacen.aura.backend

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

private val logger = LoggerFactory.getLogger("AuraBackend")

/** 缓存 TTL（秒）：和风 now 观测本身约 10-20 分钟更新一次，20 分钟在额度与新鲜度间取平衡。 */
private const val CACHE_TTL_SECONDS = 1200L

fun errorBody(status: Int, title: String, detail: String): String =
    """{"error":{"status":$status,"title":"$title","detail":"$detail"}}"""

private fun parseContentType(value: String?): ContentType =
    value?.let { runCatching { ContentType.parse(it) }.getOrNull() } ?: ContentType.Application.Json

/** 和风响应默认 gzip 压缩，手动解压（不依赖 Ktor 插件行为）。 */
suspend fun HttpResponse.decompressedBytes(): ByteArray {
    val raw = bodyAsBytes()
    return when (headers[HttpHeaders.ContentEncoding]?.lowercase()) {
        null, "", "identity" -> raw
        "gzip", "x-gzip" -> GZIPInputStream(raw.inputStream()).use { it.readBytes() }
        "deflate" -> InflaterInputStream(raw.inputStream()).use { it.readBytes() }
        else -> raw
    }
}

fun main() {
    val config = Config.load()
    val jwt = QWeatherJwt(
        privateKeyPem = config.privateKeyPath,
        developerId = config.developerId,
        projectId = config.projectId,
        credentialId = config.credentialId,
    )
    val cache = TtlCache(ttlSeconds = CACHE_TTL_SECONDS)
    val guard = Guard(config.ratePerMinute, config.dailyUpstreamLimit, config.llmDailyLimit)
    val client = HttpClient(CIO)
    // LLM 生成可能超过 CIO 默认 15s 请求超时，专用客户端放宽到 60s
    val llmClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
        }
    }
    val askService = AskService(
        client = client,
        jwt = jwt,
        cache = cache,
        guard = guard,
        apiHost = config.apiHost,
        llm = LlmClient(llmClient, config.glmApiKey, config.glmBaseUrl, config.glmModel),
    )

    logger.info(
        "Aura backend 启动: port={}, apiHost={}, cacheTTL={}s, 限流={}/min/IP, 日出网上限={}",
        config.port, config.apiHost, CACHE_TTL_SECONDS, config.ratePerMinute, config.dailyUpstreamLimit,
    )

    embeddedServer(ServerCIO, port = config.port, host = "0.0.0.0") {
        routing {
            get("/healthz") {
                call.respondText("ok")
            }

            // 天气问答（必须在通配路由之前注册）
            get("/ai/ask") {
                if (!guard.tryAcquire(call.request.origin.remoteHost)) {
                    call.respondText(
                        errorBody(429, "Too Many Requests", "请求过于频繁，请稍后再试"),
                        ContentType.Application.Json,
                        HttpStatusCode.TooManyRequests,
                    )
                    return@get
                }
                val q = call.request.queryParameters
                val location = q["location"]
                val question = q["question"]?.trim()
                when {
                    location.isNullOrBlank() || question.isNullOrBlank() || question.length > 100 ->
                        call.respondText(
                            errorBody(400, "Bad Request", "缺少 location/question 或问题过长"),
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                    else -> when (val result = askService.ask(location, question)) {
                        is AskService.Result.Ok ->
                            call.respondText(result.body, ContentType.Application.Json, HttpStatusCode.OK)
                        is AskService.Result.Error ->
                            call.respondText(result.body, ContentType.Application.Json, HttpStatusCode.fromValue(result.status))
                    }
                }
            }

            get("/{path...}") {
                // 使用原始 uri（保持百分号编码），path 与 query 原样转发给上游
                val uri = call.request.uri
                val path = uri.substringBefore('?')
                val query = uri.substringAfter('?', "")

                // 防护层1：每 IP 每分钟限流
                if (!guard.tryAcquire(call.request.origin.remoteHost)) {
                    logger.warn("限流拦截: ip={}", call.request.origin.remoteHost)
                    call.respondText(
                        errorBody(429, "Too Many Requests", "请求过于频繁，请稍后再试"),
                        ContentType.Application.Json,
                        HttpStatusCode.TooManyRequests,
                    )
                    return@get
                }

                if (ProxyRules.isBlocked(path)) {
                    logger.warn("黑名单拦截: {}", path)
                    call.respondText(
                        errorBody(403, "Forbidden", "该接口不在免费订阅范围，已被代理黑名单拦截"),
                        ContentType.Application.Json,
                        HttpStatusCode.Forbidden,
                    )
                    return@get
                }

                val cacheKey = uri
                cache.get(cacheKey)?.let { entry ->
                    call.response.header("X-Cache", "HIT")
                    call.respondText(
                        String(entry.body, StandardCharsets.UTF_8),
                        parseContentType(entry.contentType),
                        HttpStatusCode.fromValue(entry.status),
                    )
                    return@get
                }

                // 防护层2：每日出网额度熔断（缓存 HIT 已在上面零成本返回，不受影响）
                if (!guard.tryConsumeUpstream()) {
                    logger.warn("日额度熔断: {} (已出网 {} 次)", uri, guard.upstreamCountToday())
                    call.respondText(
                        errorBody(429, "Too Many Requests", "今日服务额度已用尽，明天恢复"),
                        ContentType.Application.Json,
                        HttpStatusCode.TooManyRequests,
                    )
                    return@get
                }

                val upstream = buildString {
                    append("https://").append(config.apiHost).append(path)
                    if (query.isNotBlank()) append('?').append(query)
                }

                val resp: HttpResponse = try {
                    client.get(upstream) {
                        header(HttpHeaders.Authorization, "Bearer ${jwt.token()}")
                    }
                } catch (e: Exception) {
                    logger.error("上游请求失败: {} ({})", upstream, e.message)
                    call.respondText(
                        errorBody(502, "Bad Gateway", "上游和风天气请求失败"),
                        ContentType.Application.Json,
                        HttpStatusCode.BadGateway,
                    )
                    return@get
                }

                val status = resp.status.value
                val contentType = resp.headers[HttpHeaders.ContentType]
                val body = resp.decompressedBytes()
                if (status == 200) cache.put(cacheKey, status, contentType, body)
                logger.info("转发 {} -> {} ({}B, cache={})", uri, status, body.size, if (status == 200) "MISS" else "BYPASS")

                call.response.header("X-Cache", if (status == 200) "MISS" else "BYPASS")
                call.respondText(
                    String(body, StandardCharsets.UTF_8),
                    parseContentType(contentType),
                    HttpStatusCode.fromValue(status),
                )
            }
        }
    }.start(wait = true)
}
