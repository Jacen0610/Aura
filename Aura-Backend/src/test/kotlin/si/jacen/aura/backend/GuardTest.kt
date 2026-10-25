package si.jacen.aura.backend

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardTest {

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    @Test
    fun `同一 IP 每分钟限流`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val guard = Guard(perIpPerMinute = 3, dailyUpstreamLimit = 100, clock = clock)

        assertTrue(guard.tryAcquire("1.2.3.4"))
        assertTrue(guard.tryAcquire("1.2.3.4"))
        assertTrue(guard.tryAcquire("1.2.3.4"))
        assertFalse(guard.tryAcquire("1.2.3.4"), "同一分钟第 4 次应被限流")

        // 其他 IP 不受影响
        assertTrue(guard.tryAcquire("5.6.7.8"))

        // 下一分钟窗口重置
        clock.now = Instant.parse("2026-01-01T00:01:00Z")
        assertTrue(guard.tryAcquire("1.2.3.4"))
    }

    @Test
    fun `日出网额度熔断与跨天重置`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val guard = Guard(perIpPerMinute = 100, dailyUpstreamLimit = 2, clock = clock)

        assertTrue(guard.tryConsumeUpstream())
        assertTrue(guard.tryConsumeUpstream())
        assertFalse(guard.tryConsumeUpstream(), "超出日上限应熔断")
        assertEquals(3, guard.upstreamCountToday(), "被拒的请求也计入统计")

        // 次日自动重置
        clock.now = Instant.parse("2026-01-02T00:00:00Z")
        assertTrue(guard.tryConsumeUpstream())
    }
}
