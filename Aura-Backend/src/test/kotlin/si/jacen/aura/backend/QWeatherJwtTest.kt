package si.jacen.aura.backend

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QWeatherJwtTest {

    private fun writeTestKey(): Path {
        // 生成一把临时 Ed25519 私钥（测试专用，用后即删）
        val tmp = Files.createTempFile("qw-test", ".pem")
        val p = ProcessBuilder("openssl", "genpkey", "-algorithm", "ed25519", "-out", tmp.toString())
            .start().apply { waitFor() }
        check(p.exitValue() == 0) { "openssl 生成测试密钥失败" }
        return tmp
    }

    private fun decode(segment: String): String =
        String(Base64.getUrlDecoder().decode(segment))

    @Test
    fun `生成的 JWT 结构符合和风规范`() {
        val keyPath = writeTestKey()
        try {
            val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
            val jwt = QWeatherJwt(
                privateKeyPem = keyPath,
                developerId = "QTESTDEV001",
                projectId = "TESTPROJ01",
                credentialId = "TESTCRED01",
                clock = clock,
            )
            val token = jwt.token()
            val parts = token.split(".")
            assertEquals(3, parts.size, "JWT 应为 header.payload.signature 三段")

            assertEquals("""{"alg":"EdDSA","kid":"TESTCRED01"}""", decode(parts[0]))

            val payload = decode(parts[1])
            assertTrue(payload.contains("\"iss\":\"QTESTDEV001\""), "payload 应含开发者 ID")
            assertTrue(payload.contains("\"sub\":\"TESTPROJ01\""), "payload 应含项目 ID")
            // iat 取签发时间前 30 秒；exp = iat + 30 + TTL(1800)
            assertTrue(payload.contains("\"iat\":1767225570"), "iat 应为签发时间减 30 秒")
            assertTrue(payload.contains("\"exp\":1767227400"), "exp 应为 30 分钟有效期")
        } finally {
            Files.deleteIfExists(keyPath)
        }
    }

    @Test
    fun `Token 在有效期内复用，过期后重新签发`() {
        val keyPath = writeTestKey()
        try {
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val clock = object : Clock() {
                override fun getZone(): ZoneOffset = ZoneOffset.UTC
                override fun withZone(zone: java.time.ZoneId): Clock = this
                override fun instant(): Instant = now
            }
            val jwt = QWeatherJwt(keyPath, "QTESTDEV001", "TESTPROJ01", "TESTCRED01", clock)

            val first = jwt.token()
            assertEquals(first, jwt.token(), "有效期内应复用同一 Token")
        } finally {
            Files.deleteIfExists(keyPath)
        }
    }
}
