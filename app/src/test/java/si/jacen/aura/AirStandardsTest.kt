package si.jacen.aura

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import si.jacen.aura.domain.model.AirPollutant
import si.jacen.aura.util.AirStandards

class AirStandardsTest {

    private fun pollutant(code: String, value: Double, unit: String = "μg/m³") =
        AirPollutant(code = code, name = code, value = value, unit = unit)

    @Test
    fun `PM2_5 低浓度判为优`() {
        // 6.0/35 * 50 = 8.57 → 9
        val level = AirStandards.evaluate(pollutant("pm2p5", 6.0))
        assertNotNull(level)
        assertEquals("优", level!!.category)
        assertEquals(9, level.iaqi)
        assertEquals(0, level.bands.indexOfFirst { it.isCurrent })
    }

    @Test
    fun `PM2_5 跨过35判为良`() {
        // 50 + (100-50)/(75-35)*(40-35) = 56.25 → 56
        val level = AirStandards.evaluate(pollutant("pm2p5", 40.0))
        assertEquals("良", level!!.category)
        assertEquals(56, level.iaqi)
    }

    @Test
    fun `PM10 300判为中度污染`() {
        // 150 + (200-150)/(350-250)*(300-250) = 175
        val level = AirStandards.evaluate(pollutant("pm10", 300.0))
        assertEquals("中度污染", level!!.category)
        assertEquals(175, level.iaqi)
    }

    @Test
    fun `CO 以 mg 立方米计`() {
        // 50 + (100-50)/(4-2)*(3-2) = 75
        val level = AirStandards.evaluate(pollutant("co", 3.0, unit = "mg/m³"))
        assertEquals("良", level!!.category)
        assertEquals(75, level.iaqi)
    }

    @Test
    fun `O3 120判为良`() {
        // 50 + (100-50)/(160-100)*(120-100) = 66.67 → 67
        val level = AirStandards.evaluate(pollutant("o3", 120.0))
        assertEquals("良", level!!.category)
        assertEquals(67, level.iaqi)
    }

    @Test
    fun `超上限封顶为严重污染`() {
        val level = AirStandards.evaluate(pollutant("pm2p5", 600.0))
        assertEquals("严重污染", level!!.category)
        assertEquals(500, level.iaqi)
    }

    @Test
    fun `无国标分级返回null`() {
        assertNull(AirStandards.evaluate(pollutant("no", 20.0)))
        assertNull(AirStandards.evaluate(pollutant("nmhc", 1.5)))
    }

    @Test
    fun `单位不符返回null`() {
        assertNull(AirStandards.evaluate(pollutant("pm2p5", 6.0, unit = "ppb")))
    }

    @Test
    fun `单位写法差异容忍`() {
        // API 常见写法 μg/m3（无上标）
        assertNotNull(AirStandards.evaluate(pollutant("pm2p5", 6.0, unit = "μg/m3")))
        assertNotNull(AirStandards.evaluate(pollutant("co", 3.0, unit = "mg/m3")))
    }
}
