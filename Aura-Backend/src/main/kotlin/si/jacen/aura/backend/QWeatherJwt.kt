package si.jacen.aura.backend

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.util.Base64

/**
 * 和风天气 JWT（Ed25519 / EdDSA）生成器。
 *
 * 规范（https://dev.qweather.com/docs/configuration/authentication/）：
 * - Header:  {"alg":"EdDSA","kid":"<凭据ID>"}
 * - Payload: {"iss":"<开发者ID>","sub":"<项目ID>","iat":<签发时间-30s>,"exp":<过期时间，最长24h>}
 * - 使用 Base64URL（非标准 Base64）编码，签名算法为 Ed25519。
 *
 * Token 缓存在内存中，距过期不足 [REFRESH_MARGIN] 秒时自动重新签发。
 */
class QWeatherJwt(
    privateKeyPem: Path,
    private val developerId: String,
    private val projectId: String,
    private val credentialId: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val privateKey: PrivateKey = loadPrivateKey(privateKeyPem)

    @Volatile
    private var cached: Token? = null

    private data class Token(val value: String, val expiresAt: Instant)

    fun token(): String {
        val now = Instant.now(clock)
        cached?.let { if (it.expiresAt.isAfter(now.plusSeconds(REFRESH_MARGIN))) return it.value }
        return generate(now).also { cached = it }.value
    }

    private fun generate(now: Instant): Token {
        val exp = now.plusSeconds(TTL_SECONDS)
        val header = """{"alg":"EdDSA","kid":"$credentialId"}"""
        val payload = """{"iss":"$developerId","sub":"$projectId","iat":${now.epochSecond - IAT_BACKOFF_SECONDS},"exp":${exp.epochSecond}}"""
        val signingInput = "${b64Url(header.toByteArray())}.${b64Url(payload.toByteArray())}"
        val signature = Signature.getInstance("Ed25519").apply { initSign(privateKey) }.run {
            update(signingInput.toByteArray())
            sign()
        }
        return Token("$signingInput.${b64Url(signature)}", exp)
    }

    private fun loadPrivateKey(pem: Path): PrivateKey {
        val base64 = Files.readString(pem)
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = Base64.getDecoder().decode(base64)
        return KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private fun b64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    companion object {
        const val TTL_SECONDS = 1800L
        const val IAT_BACKOFF_SECONDS = 30L
        const val REFRESH_MARGIN = 60L
    }
}
