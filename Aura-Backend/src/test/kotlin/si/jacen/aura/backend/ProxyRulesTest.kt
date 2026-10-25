package si.jacen.aura.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyRulesTest {

    @Test
    fun `台风海洋辐照路径被拦截`() {
        assertTrue(ProxyRules.isBlocked("/v7/tropical/storm-list"))
        assertTrue(ProxyRules.isBlocked("/v7/tropical/storm-track"))
        assertTrue(ProxyRules.isBlocked("/v7/ocean/tide"))
        assertTrue(ProxyRules.isBlocked("/v7/ocean/waves"))
        assertTrue(ProxyRules.isBlocked("/v7/solar/radiation"))
    }

    @Test
    fun `大小写与尾斜杠变体被拦截`() {
        assertTrue(ProxyRules.isBlocked("/V7/TROPICAL/storm-list"))
        assertTrue(ProxyRules.isBlocked("/v7/ocean/"))
        assertTrue(ProxyRules.isBlocked("/v7/solar"))
    }

    @Test
    fun `免费接口正常放行`() {
        assertFalse(ProxyRules.isBlocked("/v7/weather/now"))
        assertFalse(ProxyRules.isBlocked("/v7/weather/24h"))
        assertFalse(ProxyRules.isBlocked("/geo/v2/city/lookup"))
        assertFalse(ProxyRules.isBlocked("/airquality/v1/current/39.92/116.41"))
        assertFalse(ProxyRules.isBlocked("/v7/warning/now"))
        assertFalse(ProxyRules.isBlocked("/v7/indices/1d"))
    }
}
