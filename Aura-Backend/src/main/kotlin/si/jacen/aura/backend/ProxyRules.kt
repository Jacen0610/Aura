package si.jacen.aura.backend

import java.util.Locale

/** 代理转发规则：付费接口黑名单（见根目录 AGENTS.md，禁止修改为放行）。 */
object ProxyRules {

    /** 不在免费订阅范围的接口前缀：热带气旋（台风）、海洋、太阳辐射（辐照）。 */
    private val BLOCKED_PREFIXES = listOf(
        "/v7/tropical",
        "/v7/ocean",
        "/v7/solar",
    )

    fun isBlocked(path: String): Boolean {
        val normalized = path.lowercase(Locale.ROOT).trimEnd('/')
        return BLOCKED_PREFIXES.any { normalized.startsWith(it) }
    }
}
