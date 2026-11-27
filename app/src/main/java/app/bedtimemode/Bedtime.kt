package app.bedtimemode

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

const val TAG = "BedtimeMode"

data class Status(
    val active: Boolean,
    /** When the current session ends (only when active). */
    val endsAt: Long?,
    /** Next moment the state may flip (end of session, or next start). */
    val nextChangeAt: Long?,
    val manual: Boolean,
)

/**
 * The engine. Everything funnels into [sync], which is idempotent: it evaluates the desired
 * state, applies DND, asks the listener to hold notifications, and schedules the next wake-up.
 */
object Bedtime {

    fun config(p: Prefs) = ScheduleConfig(p.scheduleEnabled, p.startMinute, p.endMinute, p.days)

    private fun zoned(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    fun evaluate(p: Prefs, now: Long = System.currentTimeMillis()): Status {
        val until = p.overrideUntil
        if (until > now) {
            when (p.overrideMode) {
                OverrideMode.ON -> return Status(true, until, until.takeIf { it != FOREVER }, manual = true)
                OverrideMode.OFF -> return Status(false, null, until, manual = true)
                OverrideMode.NONE -> Unit
            }
        }
        val cfg = config(p)
        val nowZ = zoned(now)
        Schedule.sessionAt(cfg, nowZ)?.let { (_, end) ->
            val e = end.toInstant().toEpochMilli()
            return Status(true, e, e, manual = false)
        }
        val next = Schedule.nextStart(cfg, nowZ)?.toInstant()?.toEpochMilli()
        return Status(false, null, next, manual = false)
    }

    /** Manual "until you turn it off" marker for [Prefs.overrideUntil]. */
    const val FOREVER = Long.MAX_VALUE

    /**
     * Manual ON from the app / tile / notification, ignoring the schedule. It lasts until turned
     * off, or until the end of the next scheduled night (so forgetting it on can't hide
     * notifications all day). If a scheduled session was paused, this simply resumes it.
     */
    fun startNow(ctx: Context) {
        val p = Prefs(ctx)
        val now = System.currentTimeMillis()
        val cfg = config(p)
        if (Schedule.sessionAt(cfg, zoned(now)) != null) {
            p.clearOverride()
        } else {
            val until = Schedule.nextStart(cfg, zoned(now))
                ?.plusMinutes(Schedule.durationMinutes(cfg).toLong())
                ?.toInstant()?.toEpochMilli() ?: FOREVER
            p.setOverride(OverrideMode.ON, until)
        }
        sync(ctx, "start-now")
    }

    /**
     * Manual OFF. During a scheduled session it's off for the rest of that session only;
     * the schedule resumes the next night.
     */
    fun stopNow(ctx: Context) {
        val p = Prefs(ctx)
        val now = System.currentTimeMillis()
        val session = Schedule.sessionAt(config(p), zoned(now))
        if (session != null) {
            p.setOverride(OverrideMode.OFF, session.second.toInstant().toEpochMilli())
        } else {
            p.clearOverride()
        }
        sync(ctx, "stop-now")
    }

    fun toggle(ctx: Context) {
        if (evaluate(Prefs(ctx)).active) stopNow(ctx) else startNow(ctx)
    }

    @Synchronized
    fun sync(ctx: Context, reason: String) {
        val app = ctx.applicationContext
        val p = Prefs(app)
        val now = System.currentTimeMillis()
        if (p.overrideMode != OverrideMode.NONE && p.overrideUntil <= now) p.clearOverride()

        val st = evaluate(p, now)
        Log.i(TAG, "sync($reason): $st wasActive=${p.appliedActive}")

        val ending = !st.active && p.appliedActive
        if (!ending) Dnd.apply(app, st.active && p.useDnd)

        if (st.active) {
            if (!p.appliedActive) {
                p.sessionStartedAt = now
                p.heldKeys = emptySet()
                p.pendingReturnKeys = emptySet()
                p.appliedActive = true
            }
            p.sessionEndsAt = st.endsAt ?: 0L
            BedtimeListenerService.holdAll(app)
        } else if (ending) {
            History.finishSession(p, now)
            p.appliedActive = false
            // Release held notifications while DND is still on, so they come back silently,
            // then lift DND a few seconds later.
            BedtimeListenerService.releaseAll(app, p.pendingReturnKeys)
            Handler(Looper.getMainLooper()).postDelayed({
                if (!evaluate(Prefs(app)).active) Dnd.apply(app, false)
            }, 4_000)
        }

        Alarms.schedule(app, st.nextChangeAt)
        StatusNotification.update(app, st, p)
        BedtimeTileService.refresh(app)
        Updater.maybeCheckInBackground(app)
    }
}

object Alarms {
    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, AlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /**
     * ColorOS stretches "exact" alarms into a window up to ~75% of the delay (an hour for an
     * alarm 8h away). So we never ask for a far target directly: we wake at the halfway point
     * (which even with the stretch lands before the target), re-evaluate, and halve again. The
     * last stretch (<= 90 s) uses setAlarmClock, which the OEM can't defer.
     */
    fun schedule(ctx: Context, at: Long?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx)
        am.cancel(pi)
        if (at == null) {
            Log.i(TAG, "alarm: none scheduled")
            return
        }
        val now = System.currentTimeMillis()
        val target = at + 500 // just past the boundary
        val remaining = target - now
        when {
            remaining <= 90_000 -> {
                val show = PendingIntent.getActivity(
                    ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(maxOf(target, now + 1_000), show), pi)
                Log.i(TAG, "alarm: final at ${Instant.ofEpochMilli(target)}")
            }
            else -> {
                val checkpoint = now + remaining / 2
                if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkpoint, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkpoint, pi)
                Log.i(TAG, "alarm: checkpoint at ${Instant.ofEpochMilli(checkpoint)} (target ${Instant.ofEpochMilli(target)})")
            }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Bedtime.sync(context, "alarm")
}

class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Bedtime.sync(context, intent.action ?: "system")
}
