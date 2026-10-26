package si.jacen.aura

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.jacen.aura.domain.model.MinutelyPoint
import si.jacen.aura.util.MinutelyRainAnalyzer

class MinutelyRainAnalyzerTest {

    private fun point(minuteOffset: Int, precip: Double): MinutelyPoint {
        val time = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
            .plusMinutes(minuteOffset.toLong())
        return MinutelyPoint(fxTime = time.toString(), precip = precip, prob = null)
    }

    @Test
    fun `无雨`() {
        val a = MinutelyRainAnalyzer.analyze((0 until 24).map { point(it * 5, 0.0) })
        assertTrue(a.noRain)
        assertEquals("未来两小时无降雨", a.conclusion)
        assertNull(a.startLabel)
        assertEquals(0, a.totalMinutes)
    }

    @Test
    fun `将下雨 - 单段`() {
        // 20 分钟后开始下 5 根柱（25 分钟）
        val points = (0 until 24).map { point(it * 5, if (it in 4..8) 0.3 else 0.0) }
        val a = MinutelyRainAnalyzer.analyze(points)
        assertFalse(a.noRain)
        assertTrue(a.conclusion.contains("开始下雨"))
        assertTrue(a.conclusion.contains("持续约 25 分钟"))
        assertEquals(25, a.totalMinutes)
        assertEquals(0.3, a.maxPrecip5m, 0.001)
    }

    @Test
    fun `正在下雨 - 窗口内停止`() {
        // 前 6 根有雨（30 分钟），之后停
        val points = (0 until 24).map { point(it * 5, if (it <= 5) 0.4 else 0.0) }
        val a = MinutelyRainAnalyzer.analyze(points)
        assertTrue(a.ongoing)
        assertTrue(a.conclusion.contains("正在下雨"))
        assertTrue(a.conclusion.contains("停止"))
        assertEquals(30, a.totalMinutes)
    }

    @Test
    fun `正在下雨 - 贯穿全窗口`() {
        val points = (0 until 24).map { point(it * 5, 0.2) }
        val a = MinutelyRainAnalyzer.analyze(points)
        assertTrue(a.ongoing)
        assertTrue(a.conclusion.contains("持续"))
        assertEquals(120, a.totalMinutes)
    }

    @Test
    fun `两波降雨`() {
        // 第 2-3 根与第 10-12 根有雨
        val points = (0 until 24).map {
            point(it * 5, if (it in 2..3 || it in 10..12) 0.5 else 0.0)
        }
        val a = MinutelyRainAnalyzer.analyze(points)
        assertTrue(a.conclusion.contains("两波"))
        assertEquals(25, a.totalMinutes) // 2+3=5 根 ×5
    }
}
