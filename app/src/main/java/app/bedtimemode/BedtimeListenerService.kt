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

        /**
         * Bring held notifications back right now (bedtime turned off early). Android has no
         * public "unsnooze", but re-snoozing a snoozed notification for ~1 s replaces its timer,
         * so it returns to the shade almost immediately.
         */
        fun releaseAll(ctx: Context, keys: Set<String>) {
            val svc = instance ?: run { Log.w(TAG, "release: listener not connected"); return }
            var n = 0
            for (k in keys) {
                runCatching { svc.snoozeNotification(k, 1_000) }.onSuccess { n++ }
                    .onFailure { Log.w(TAG, "release failed for $k: ${it.message}") }
            }
            Log.i(TAG, "released $n/${keys.size} notifications")
        }

        /** After "hide system notifications" is switched off mid-session, release those now. */
        fun releaseSystem(ctx: Context) {
            val svc = instance ?: return
            val p = Prefs(ctx)
            val keys = p.heldKeys.filter { svc.isSystemSource(History.pkgOf(it)) }.toSet()
            if (keys.isNotEmpty()) releaseAll(ctx, keys)
        }

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
            !p.hideSystem && isSystemSource(sbn.packageName) -> false
            sbn.isOngoing -> false
            n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 -> false
            n.category == Notification.CATEGORY_CALL -> false
            n.category == Notification.CATEGORY_ALARM -> false
            else -> true
        }
    }

    private val systemCache = HashMap<String, Boolean>()

    /**
     * "System" = Android itself, System UI, or a preinstalled system package without a launcher
     * icon (background services like battery, hotspot, USB). Preinstalled apps you open yourself
     * (Gmail, Calendar…) count as normal apps.
     */
    fun isSystemSource(pkg: String): Boolean = systemCache.getOrPut(pkg) {
        if (pkg == "android" || pkg == "com.android.systemui") return@getOrPut true
        val info = runCatching { packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@getOrPut false
        val preinstalled = info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        preinstalled && packageManager.getLaunchIntentForPackage(pkg) == null
    }

    /** Longest single snooze; manual "until off" sessions are released explicitly on stop. */
    private val maxSnoozeMs = 7L * 24 * 60 * 60_000

    /** @return true if the notification was snoozed. */
    private fun hold(sbn: StatusBarNotification, endsAt: Long, p: Prefs): Boolean {
        if (!shouldHold(sbn, p)) return false
        val remaining = endsAt - System.currentTimeMillis()
        if (remaining < 5_000) return false
        val duration = minOf(remaining, maxSnoozeMs)
        return runCatching { snoozeNotification(sbn.key, duration) }
            .onSuccess {
                val keys = p.heldKeys
                if (sbn.key !in keys) {
                    p.heldKeys = keys + sbn.key
                    HeldLog.add(this, sbn)
                }
                Log.i(TAG, "held ${sbn.packageName} (${sbn.key}) for ${duration / 1000}s")
            }
            .onFailure { Log.w(TAG, "snooze failed for ${sbn.key}", it) }
            .isSuccess
    }
}
