package app.bedtimemode

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

data class ScheduleConfig(
    val enabled: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val days: Set<DayOfWeek>,
)

/** Pure schedule math (no Android deps). A session starts on an enabled day and may cross midnight. */
object Schedule {
    private const val DAY_MIN = 24 * 60

    fun durationMinutes(c: ScheduleConfig): Int {
        val d = Math.floorMod(c.endMinute - c.startMinute, DAY_MIN)
        return if (d == 0) DAY_MIN else d
    }

    private fun startOn(c: ScheduleConfig, date: java.time.LocalDate, ref: ZonedDateTime): ZonedDateTime =
        LocalDateTime.of(date, LocalTime.of(c.startMinute / 60, c.startMinute % 60)).atZone(ref.zone)

    /** The session [start, end) containing [now], or null. */
    fun sessionAt(c: ScheduleConfig, now: ZonedDateTime): Pair<ZonedDateTime, ZonedDateTime>? {
        if (!c.enabled || c.days.isEmpty()) return null
        val dur = durationMinutes(c).toLong()
        for (offset in 0L..1L) {
            val date = now.toLocalDate().minusDays(offset)
            if (date.dayOfWeek !in c.days) continue
            val s = startOn(c, date, now)
            val e = s.plusMinutes(dur)
            if (!now.isBefore(s) && now.isBefore(e)) return s to e
        }
        return null
    }

    fun nextStart(c: ScheduleConfig, now: ZonedDateTime): ZonedDateTime? {
        if (!c.enabled || c.days.isEmpty()) return null
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            if (date.dayOfWeek !in c.days) continue
            val s = startOn(c, date, now)
            if (s.isAfter(now)) return s
        }
        return null
    }

    /** Next wall-clock occurrence of [minute] strictly after now (+1 min margin). */
    fun nextOccurrence(minute: Int, now: ZonedDateTime): ZonedDateTime {
        var t = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(now.zone)
        if (!t.isAfter(now.plusMinutes(1))) t = t.plusDays(1)
        return t
    }
}
