package si.jacen.aura.data.calendar

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.jacen.aura.domain.model.CalendarEvent
import si.jacen.aura.domain.repository.CalendarEventReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 读取 Android 系统日历中枢（Calendar Provider）。
 * 所有写入系统日历的 App（Google 日历/厂商日历/Outlook/滴答清单等）的数据都在这里，
 * 与具体日历 App 无关；仅本机读取，日历数据永不上传。
 */
@Singleton
class CalendarReaderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CalendarEventReader {

    private val resolver: ContentResolver get() = context.contentResolver

    @SuppressLint("MissingPermission")
    override suspend fun upcomingEvents(withinHours: Int): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        return withContext(Dispatchers.IO) { query(withinHours) }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun query(withinHours: Int): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        val end = now + withinHours * 3_600_000L
        // 直查事件表而非实例视图：实例展开在同步不稳定时可能滞后/缺失，
        // 事件表是更接近真相的一层；重复规则日程（rrule）MVP 阶段跳过
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.SELF_ATTENDEE_STATUS,
            CalendarContract.Events.RRULE,
        )
        val events = mutableListOf<CalendarEvent>()
        var skippedRecurring = 0
        val cursor = runCatching {
            resolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events.DELETED} = 0 AND ${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DTEND} >= ?",
                arrayOf(end.toString(), now.toString()),
                "${CalendarContract.Events.DTSTART} ASC",
            )
        }.getOrNull() ?: run {
            android.util.Log.w("AuraCalendar", "事件表查询失败")
            return emptyList()
        }

        cursor.use {
            val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
            val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
            val beginIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
            val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)
            val allDayIdx = it.getColumnIndex(CalendarContract.Events.ALL_DAY)
            val locIdx = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
            val statusIdx = it.getColumnIndex(CalendarContract.Events.SELF_ATTENDEE_STATUS)
            val rruleIdx = it.getColumnIndex(CalendarContract.Events.RRULE)

            while (it.moveToNext()) {
                val status = if (statusIdx >= 0) it.getInt(statusIdx) else CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED
                if (status == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                val title = titleIdx.takeIf { i -> i >= 0 }?.let { i -> it.getString(i) }?.trim()
                if (title.isNullOrEmpty()) continue
                val rrule = rruleIdx.takeIf { i -> i >= 0 }?.let { i -> it.getString(i) }
                if (!rrule.isNullOrEmpty()) {
                    skippedRecurring++
                    continue
                }
                val allDay = allDayIdx >= 0 && it.getInt(allDayIdx) == 1
                val begin = it.getLong(beginIdx)
                val finish = it.getLong(endIdx)
                if (finish <= begin) continue
                // 全天事件保留（按当日规则匹配）；跨多日（>2 天）与超长定时事件跳过
                if ((finish - begin) > MAX_EVENT_MILLIS) continue
                events.add(
                    CalendarEvent(
                        id = it.getLong(idIdx),
                        title = title,
                        startMillis = begin,
                        endMillis = finish,
                        allDay = allDay,
                        location = locIdx.takeIf { i -> i >= 0 }?.let { i -> it.getString(i) }?.trim()?.takeIf { s -> s.isNotEmpty() },
                    )
                )
            }
        }
        if (skippedRecurring > 0) {
            android.util.Log.d("AuraCalendar", "跳过 $skippedRecurring 条重复规则日程（MVP 暂不支持）")
        }
        return events.sortedBy { it.startMillis }
    }

    private companion object {
        /** 超过 2 天的日程（跨多天行程/长假）不参与匹配 */
        const val MAX_EVENT_MILLIS = 2 * 24 * 3_600_000L
    }
}
