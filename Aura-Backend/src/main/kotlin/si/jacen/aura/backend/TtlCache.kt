package si.jacen.aura.backend

import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** 简单的内存 TTL 缓存，仅缓存上游 200 响应。满员后整体清空（代理场景容量足够）。 */
class TtlCache(
    private val ttlSeconds: Long,
    private val maxEntries: Int = 256,
    private val clock: Clock = Clock.systemUTC(),
) {
    data class Entry(val status: Int, val contentType: String?, val body: ByteArray, val storedAt: Instant)

    private val map = ConcurrentHashMap<String, Entry>()

    fun get(key: String): Entry? {
        val entry = map[key] ?: return null
        if (entry.storedAt.plusSeconds(ttlSeconds).isBefore(Instant.now(clock))) {
            map.remove(key, entry)
            return null
        }
        return entry
    }

    fun put(key: String, status: Int, contentType: String?, body: ByteArray) {
        if (status != 200) return
        if (map.size >= maxEntries) map.clear()
        map[key] = Entry(status, contentType, body, Instant.now(clock))
    }

    val size: Int get() = map.size
}
