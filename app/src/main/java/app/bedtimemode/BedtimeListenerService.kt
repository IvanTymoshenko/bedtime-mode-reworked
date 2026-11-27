package app.bedtimemode

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Hides notifications during bedtime by snoozing them until the session ends. Android itself
 * re-posts snoozed notifications when the snooze expires, so they all come back to the shade
 * in the morning with their original actions intact.
 */
class BedtimeListenerService : NotificationListenerService() {

    companion object {
        @Volatile
        private var instance: BedtimeListenerService? = null

        val isConnected get() = instance != null

        fun component(ctx: Context) = ComponentName(ctx, BedtimeListenerService::class.java)

        /** Hold everything currently in the shade (called on session start / resync). */
        fun holdAll(ctx: Context) {
            val svc = instance
            if (svc != null) {
                svc.holdActive()
            } else {
                Log.w(TAG, "listener not connected; requesting rebind")
                runCatching { requestRebind(component(ctx)) }
            }
        }
    }

    override fun onListenerConnected() {
        instance = this
        Log.i(TAG, "listener connected")
        val p = Prefs(this)
        if (Bedtime.evaluate(p).active != p.appliedActive) Bedtime.sync(this, "listener-connected")
        else holdActive()
    }

    override fun onListenerDisconnected() {
        instance = null
        Log.w(TAG, "listener disconnected; requesting rebind")
        runCatching { requestRebind(component(this)) }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val p = Prefs(this)
        val st = Bedtime.evaluate(p)
        // Self-heal if an alarm was missed (e.g. the app got killed by the OEM).
        if (st.active != p.appliedActive) Bedtime.sync(this, "listener-drift")
        if (st.active && st.endsAt != null) {
            if (hold(sbn, st.endsAt, p)) StatusNotification.update(this, st, p)
        } else {
            History.noteReturned(p, sbn.key)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // If the user swipes our status notification away while bedtime is on, put it back.
        if (sbn.packageName == packageName && sbn.id == StatusNotification.ID) {
            val p = Prefs(this)
            val st = Bedtime.evaluate(p)
            if (st.active) StatusNotification.update(this, st, p)
        }
    }

    fun holdActive() {
        val p = Prefs(this)
        val st = Bedtime.evaluate(p)
        val endsAt = st.endsAt
        if (!st.active || endsAt == null) return
        val all = runCatching { activeNotifications }.getOrNull() ?: return
        var any = false
        for (sbn in all) {
            if (!p.hideExisting && sbn.postTime < p.sessionStartedAt && sbn.key !in p.heldKeys) continue
            any = hold(sbn, endsAt, p) || any
        }
        if (any) StatusNotification.update(this, st, p)
    }

    private fun shouldHold(sbn: StatusBarNotification, p: Prefs): Boolean {
        val n = sbn.notification
        return when {
            sbn.packageName == packageName -> false
            sbn.packageName in p.allowedPackages -> false
            sbn.isOngoing -> false
            n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 -> false
            n.category == Notification.CATEGORY_CALL -> false
            n.category == Notification.CATEGORY_ALARM -> false
            else -> true
        }
    }

    /**
     * Snooze in chunks rather than all the way to the end: a notification that comes back while
     * bedtime is still on is simply snoozed again. This means that if bedtime is turned off
     * early, everything returns within one chunk (Android has no public "unsnooze" for apps).
     * Chunks are short only while DND is silencing, so re-posts can never make a sound.
     */
    private fun chunkMs(p: Prefs): Long =
        if (Bedtime.dndSilencing(this, p)) p.chunkMs else 6 * 60 * 60_000L

    /** @return true if the notification was snoozed. */
    private fun hold(sbn: StatusBarNotification, endsAt: Long, p: Prefs): Boolean {
        if (!shouldHold(sbn, p)) return false
        val remaining = endsAt - System.currentTimeMillis()
        if (remaining < 5_000) return false
        val duration = minOf(remaining, chunkMs(p))
        return runCatching { snoozeNotification(sbn.key, duration) }
            .onSuccess {
                val keys = p.heldKeys
                if (sbn.key !in keys) p.heldKeys = keys + sbn.key
                Log.i(TAG, "held ${sbn.packageName} (${sbn.key}) for ${duration / 1000}s")
            }
            .onFailure { Log.w(TAG, "snooze failed for ${sbn.key}", it) }
            .isSuccess
    }
}
