package si.jacen.aura

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.jacen.aura.domain.model.CalendarEvent
import si.jacen.aura.domain.model.CurrentWeather
import si.jacen.aura.domain.model.HourlyItem
import si.jacen.aura.domain.model.WeatherBundle
import si.jacen.aura.domain.usecase.CalendarInsightEngine
import java.time.OffsetDateTime
import java.time.ZoneOffset

class CalendarInsightEngineTest {

    /** 基准时间：3 小时后（事件起点落在 24 小时内 → 走逐小时规则层） */
    private val base: Long =
        OffsetDateTime.now(ZoneOffset.UTC).plusHours(3).withMinute(0).toInstant().toEpochMilli()
    private fun at(hour: Int) = base + hour * 3_600_000L

    private fun event(title: String, startH: Int, endH: Int, location: String? = null) = CalendarEvent(
        id = title.hashCode().toLong(),
        title = title,
        startMillis = at(startH),
        endMillis = at(endH),
        allDay = false,
        location = location,
    )

    private fun hour(h: Int, temp: Int = 22, pop: Int = 0) = HourlyItem(
        time = OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(at(h)),
            ZoneOffset.UTC,
        ).toString(),
        temperature = temp,
        text = if (pop >= 50) "雨" else "晴",
        iconCode = if (pop >= 50) "305" else "100",
        precipProbability = pop,
    )

    private fun weather(hours: List<HourlyItem>) = WeatherBundle(
        updatedAt = null,
        current = CurrentWeather("obs", 20, 20, "晴", "100", null, null, 50, 0.0, 1013, 20.0, 0),
        hourly = hours,
        daily = emptyList(),
    )

    private fun matchAll(
        events: List<CalendarEvent>,
        w: WeatherBundle,
        homeCity: String = "南京",
        eventCity: String? = null,
    ): List<si.jacen.aura.domain.model.CalendarInsight> = runBlocking {
        CalendarInsightEngine.match(events, homeCity, { eventCity?.let { it to w } ?: (homeCity to w) })
    }

    @Test
    fun `事件时段有雨 - 提醒带伞`() {
        val w = weather((0..23).map { h -> hour(h, pop = if (h in 8..10) 70 else 10) })
        val insights = matchAll(listOf(event("团建聚餐", 9, 11)), w)
        assertEquals(1, insights.size)
        assertTrue(insights[0].findings.any { it.text.contains("70%") })
    }

    @Test
    fun `事件时段温差大 - 提醒备衣`() {
        val w = weather((0..23).map { h -> hour(h, temp = if (h in 14..17) 28 else 18) })
        val insights = matchAll(listOf(event("下午会议", 13, 18)), w)
        assertEquals(1, insights.size)
        assertTrue(insights[0].findings.any { it.text.contains("温差") })
    }

    @Test
    fun `户外活动遇高温 - 提醒防晒`() {
        val w = weather((0..23).map { h -> hour(h, temp = if (h in 15..17) 34 else 26) })
        val insights = matchAll(listOf(event("羽毛球局", 15, 17)), w)
        assertTrue(insights[0].findings.any { it.text.contains("高温") })
    }

    @Test
    fun `室内活动高温不提醒`() {
        val w = weather((0..23).map { h -> hour(h, temp = if (h in 15..17) 34 else 26) })
        val insights = matchAll(listOf(event("项目评审会", 15, 17)), w)
        assertTrue("非户外活动不应触发高温提醒", insights.isEmpty())
    }

    @Test
    fun `天气平稳的日程不出现在结果里`() {
        val w = weather((0..23).map { h -> hour(h) })
        val insights = matchAll(listOf(event("喝咖啡", 10, 11)), w)
        assertTrue(insights.isEmpty())
    }

    @Test
    fun `异地事件 - 提醒跨城`() {
        val w = weather((0..23).map { h -> hour(h) })
        val insights = matchAll(
            listOf(event("出差沟通", 10, 12, location = "上海")),
            w, homeCity = "南京", eventCity = "上海",
        )
        assertEquals(1, insights.size)
        assertTrue(insights[0].findings.any { it.text.contains("上海") })
        assertEquals("上海", insights[0].cityName)
    }

    @Test
    fun `地点与家同城不算异地`() {
        val w = weather((0..23).map { h -> hour(h) })
        val insights = matchAll(
            listOf(event("江宁图书馆", 10, 12, location = "江宁")),
            w, homeCity = "南京", eventCity = null, // resolveCityWeather 回退家城市
        )
        assertTrue(insights.isEmpty())
    }

    private fun dailyItem(date: String, pop: Int = 0, min: Int = 15, max: Int = 25) =
        si.jacen.aura.domain.model.DailyItem(
            date = date, textDay = if (pop >= 50) "雨" else "晴", textNight = null,
            iconDay = "100", tempMax = max, tempMin = min, precipProbability = pop,
            uvIndex = null, sunrise = null, sunset = null,
        )

    @Test
    fun `全天事件按日级规则匹配降雨`() = runBlocking {
        // 全天事件：日期对上日预报且降水概率高 → 提醒
        val dayDate = java.time.Instant.ofEpochMilli(at(0))
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        val w = weather((0..23).map { hour(it) }).copy(
            daily = listOf(dailyItem(dayDate, pop = 60)),
        )
        val allDay = event("域名续费", 0, 24).copy(allDay = true, endMillis = at(24))
        val insights = CalendarInsightEngine.match(listOf(allDay), "南京") { "南京" to w }
        assertEquals(1, insights.size)
        assertTrue(insights[0].findings.any { it.text.contains("60%") })
    }

    @Test
    fun `超出24小时的定时事件走日级层`() = runBlocking {
        val dayDate = java.time.LocalDate.now().plusDays(3).toString()
        val w = weather(emptyList()).copy(daily = listOf(dailyItem(dayDate, pop = 55)))
        val far = CalendarEvent(
            id = 1L, title = "相亲见面", allDay = false,
            startMillis = System.currentTimeMillis() + 3 * 86_400_000L,
            endMillis = System.currentTimeMillis() + 3 * 86_400_000L + 3_600_000L,
            location = null,
        )
        val insights = CalendarInsightEngine.match(listOf(far), "南京") { "南京" to w }
        assertEquals(1, insights.size)
        assertTrue(insights[0].findings.any { it.text.contains("降水概率 55%") })
    }

    @Test
    fun `全天事件日预报无匹配日期则无提醒`() = runBlocking {
        val w = weather((0..23).map { hour(it) }).copy(daily = emptyList())
        val allDay = event("读书日", 0, 24).copy(allDay = true, endMillis = at(24))
        val insights = CalendarInsightEngine.match(listOf(allDay), "南京") { "南京" to w }
        assertTrue(insights.isEmpty())
    }

    @Test
    fun `户外关键词识别`() {
        assertTrue(CalendarInsightEngine.isOutdoor("周末爬山"))
        assertTrue(CalendarInsightEngine.isOutdoor("夜跑 5km"))
        assertFalse("周会不应识别为户外", CalendarInsightEngine.isOutdoor("周会"))
    }
}
