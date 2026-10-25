package si.jacen.aura.backend

import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 代理防护闸，两层：
 * 1. 每 IP 固定窗口限流（默认 30 次/分钟，命中缓存的请求也计数——防的是恶意打点）；
 * 2. 每日真实出网（MISS→和风）额度熔断（默认 1500 次/天）。
 *    1500 × 31 ≈ 46,500 < 和风免费额度 50,000/月，数学上保证免费额度不可能被刷穿。
 *
 * 熔断触发后：缓存 HIT 仍正常返回（零成本），仅新的上游请求被拒（429）。
 */
class Guard(
    private val perIpPerMinute: Int,
    private val dailyUpstreamLimit: Long,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private class Window(var minute: Long, var count: Int)

    private val ipWindows = ConcurrentHashMap<String, Window>()
    private var currentDay: LocalDate? = null
    private val upstreamToday = AtomicLong()

    /** 该 IP 本分钟内是否还允许请求。 */
    @Synchronized
    fun tryAcquire(ip: String): Boolean {
        val minute = java.time.Instant.now(clock).epochSecond / 60
        val window = ipWindows.getOrPut(ip) { Window(minute, 0) }
        if (window.minute != minute) {
            window.minute = minute
            window.count = 0
        }
        window.count++
        // 防内存被伪造 IP 撑爆：表过大时整体清空（限流短暂失效可接受）
        if (ipWindows.size > 10_000) ipWindows.clear()
        return window.count <= perIpPerMinute
    }

    /** 是否还允许一次真实出网请求。跨天自动重置。 */
    fun tryConsumeUpstream(): Boolean {
        val today = LocalDate.now(clock)
        val day = currentDay
        if (day != today) {
            synchronized(this) {
                if (currentDay != today) {
                    currentDay = today
                    upstreamToday.set(0)
                }
            }
        }
        return upstreamToday.incrementAndGet() <= dailyUpstreamLimit
    }

    fun upstreamCountToday(): Long = upstreamToday.get()
}
