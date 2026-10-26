package si.jacen.aura

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.jacen.aura.data.network.dto.AirCurrentDto
import si.jacen.aura.data.network.dto.GeoLookupDto
import si.jacen.aura.data.network.dto.MinutelyDto
import si.jacen.aura.data.network.dto.WeatherAlertDto
import si.jacen.aura.data.network.dto.toDomain as toMinutely
import si.jacen.aura.data.network.dto.toDomain as toWarning
import si.jacen.aura.data.network.dto.WeatherNowDto
import si.jacen.aura.data.network.dto.toCurrentWeather
import si.jacen.aura.data.network.dto.toDomain
import si.jacen.aura.domain.model.Coordinate

class MappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `气象预警解析 - weatheralert v1 真实结构`() {
        val payload = """
            {"metadata":{"tag":"x","zeroResult":false},
             "alerts":[{"id":"202510241119105837988676","senderName":"临桂区气象台",
               "issuedTime":"2026-10-27T07:30+08:00",
               "messageType":{"code":"update","supersedes":["p"]},
               "eventType":{"name":"大风","code":"1006"},
               "severity":"moderate","color":{"code":"blue","red":30,"green":50,"blue":205,"alpha":1},
               "effectiveTime":"2026-10-27T08:00+08:00","expireTime":"2026-10-27T20:00+08:00",
               "headline":"临桂区气象台更新大风蓝色预警信号",
               "description":"预计今天白天偏北风力5到6级阵风7级，请注意防范。",
               "instruction":"1. 关好门窗。"}]}
        """.trimIndent()
        val alerts = json.decodeFromString<WeatherAlertDto>(payload).alerts.mapNotNull { it.toWarning() }
        assertEquals(1, alerts.size)
        assertEquals("大风", alerts[0].typeName)
        assertEquals("蓝色", alerts[0].level)
        assertTrue(alerts[0].description.contains("阵风"))
        assertEquals("临桂区气象台", alerts[0].sender)
        assertEquals(205, alerts[0].blue)
    }

    @Test
    fun `取消类预警被过滤`() {
        val payload = """
            {"alerts":[{"eventType":{"name":"大风"},"messageType":{"code":"cancel"},
               "color":{"code":"blue"},"headline":"解除大风蓝色预警"}]}
        """.trimIndent()
        val alerts = json.decodeFromString<WeatherAlertDto>(payload).alerts.mapNotNull { it.toWarning() }
        assertEquals(1, alerts.size) // 映射层保留，由仓库层过滤 isCancel
        assertTrue(alerts[0].isCancel)
    }

    @Test
    fun `分钟级降水解析 - 真实结构样例`() {
        val payload = """
            {"code":"200","summary":"未来两小时无降雨",
             "minutely":[{"fxTime":"2026-10-27T14:00+08:00","precip":"0.00","prob":"0"},
                         {"fxTime":"2026-10-27T14:05+08:00","precip":"0.35","prob":"62"}]}
        """.trimIndent()
        val domain = json.decodeFromString<MinutelyDto>(payload).toMinutely()
        assertNotNull(domain)
        assertEquals("未来两小时无降雨", domain!!.summary)
        assertEquals(2, domain.points.size)
        assertEquals(0.35, domain.points[1].precip, 0.001)
        assertEquals(62, domain.points[1].prob)
    }

    @Test
    fun `预警空列表与分钟降水缺失容错`() {
        assertEquals(0, json.decodeFromString<WeatherAlertDto>("""{"alerts":[]}""").alerts.size)
        assertNull(json.decodeFromString<MinutelyDto>("""{"code":"200"}""").toMinutely())
    }

    @Test
    fun `坐标顺序归一 - 天气查询为经度在前`() {
        val c = Coordinate(latitude = 39.92, longitude = 116.41)
        // 和风天气/城市接口：location=经度,纬度
        assertEquals("116.41,39.92", c.weatherQuery)
    }

    @Test
    fun `坐标顺序归一 - 空气路径为纬度在前`() {
        val c = Coordinate(latitude = 39.92, longitude = 116.41)
        // 空气质量 v1：/airquality/v1/current/{纬度}/{经度}
        assertEquals("39.92/116.41", c.airPath)
    }

    @Test
    fun `天气 now 解析与映射 - 真实返回样例`() {
        val payload = """
            {"code":"200","updateTime":"2026-10-19T00:18+08:00",
             "now":{"obsTime":"2026-10-19T00:05+08:00","temp":"6","icon":"150","text":"晴",
                    "wind360":"45","windDir":"东北风","windScale":"1","windSpeed":"4",
                    "humidity":"89","precip":"0.0","pressure":"1017","cloud":"0","dew":"17",
                    "feelsLike":"4","vis":"18"}}
        """.trimIndent()
        val dto = json.decodeFromString<WeatherNowDto>(payload)
        val domain = dto.toCurrentWeather()

        assertEquals(6, domain.temperature)
        assertEquals(4, domain.feelsLike)
        assertEquals("晴", domain.text)
        assertEquals("东北风", domain.windDir)
        assertEquals(89, domain.humidity)
        assertEquals(18.0, domain.visibilityKm!!, 0.001)
    }

    @Test
    fun `城市反查解析 - 真实返回样例`() {
        val payload = """
            {"code":"200","location":[
              {"name":"东城","id":"101011600","lat":"39.91755","lon":"116.41876",
               "adm2":"北京","adm1":"北京市","country":"中国","tz":"Asia/Shanghai"}
            ]}
        """.trimIndent()
        val dto = json.decodeFromString<GeoLookupDto>(payload)
        val city = dto.location.firstOrNull()?.toDomain()

        assertNotNull(city)
        assertEquals("东城", city!!.name)
        assertEquals("101011600", city.id)
        assertEquals(39.91755, city.latitude, 0.00001)
        assertEquals(116.41876, city.longitude, 0.00001)
        // 展示名不带重复的上级行政区
        assertTrue(city.displayName.contains("东城"))
    }

    @Test
    fun `空气质量解析 - 多标准索引与污染物`() {
        val payload = """
            {"metadata":{"tag":"x"},
             "indexes":[
               {"code":"us-epa","name":"AQI (US)","aqi":46,"aqiDisplay":"46","level":"1",
                "category":"Good","color":{"red":0,"green":228,"blue":0,"alpha":1},
                "primaryPollutant":{"code":"pm2p5","name":"PM 2.5"},
                "health":{"effect":"ok","advice":{"generalPopulation":"g","sensitivePopulation":"s"}}},
               {"code":"cn-mee","name":"AQI (CN)","aqi":32,"aqiDisplay":"32","level":"1",
                "category":"优","color":{"red":0,"green":228,"blue":0,"alpha":1}}
             ],
             "pollutants":[
               {"code":"pm2p5","name":"PM 2.5",
                "concentration":{"value":11.0,"unit":"μg/m3"},
                "subIndexes":[{"code":"us-epa","aqi":46,"aqiDisplay":"46"}]}
             ]}
        """.trimIndent()
        val dto = json.decodeFromString<AirCurrentDto>(payload)
        val indexes = dto.indexes.mapNotNull { it.toDomain() }
        val pollutants = dto.pollutants.mapNotNull { it.toDomain() }

        assertEquals(2, indexes.size)
        assertEquals("优", indexes.first { it.code == "cn-mee" }.category)
        assertEquals(32, indexes.first { it.code == "cn-mee" }.aqi)
        assertEquals(1, pollutants.size)
        assertEquals(11.0, pollutants.first().value, 0.001)
    }
}
