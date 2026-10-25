package si.jacen.aura.backend

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TtlCacheTest {

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneOffset = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    @Test
    fun `命中与过期`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val cache = TtlCache(ttlSeconds = 300, clock = clock)

        cache.put("/v7/weather/now?location=116.41,39.92", 200, "application/json", "{}".toByteArray())
        assertIs<TtlCache.Entry>(cache.get("/v7/weather/now?location=116.41,39.92"), "300 秒内应命中")

        clock.now = Instant.parse("2026-01-01T00:05:01Z")
        assertNull(cache.get("/v7/weather/now?location=116.41,39.92"), "超过 TTL 应过期")
    }

    @Test
    fun `非 200 响应不缓存`() {
        val cache = TtlCache(ttlSeconds = 300)
        cache.put("/v7/weather/now?location=bad", 404, "application/json", "[]".toByteArray())
        assertNull(cache.get("/v7/weather/now?location=bad"))
    }

    @Test
    fun `满员整体清空`() {
        val cache = TtlCache(ttlSeconds = 300, maxEntries = 2)
        cache.put("/a", 200, null, "1".toByteArray())
        cache.put("/b", 200, null, "1".toByteArray())
        cache.put("/c", 200, null, "1".toByteArray())
        assertEquals(1, cache.size)
        assertTrue(cache.get("/c") != null)
    }
}
