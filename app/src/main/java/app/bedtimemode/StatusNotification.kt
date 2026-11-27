package app.bedtimemode

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/** The "Bedtime is on" notification that stays in the shade while bedtime is active. */
object StatusNotification {
    private const val CHANNEL = "bedtime_status"
    const val ID = 1
    const val ACTION_TURN_OFF = "app.bedtimemode.TURN_OFF"

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Bedtime status", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows that bedtime mode is active"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true) // so our own status stays visible while DND hides everything else
        }
        nm.createNotificationChannel(ch)
    }

    fun update(ctx: Context, st: Status, p: Prefs) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!st.active || !p.showStatusNotification) {
            nm.cancel(ID)
            return
        }
        if (!canPost(ctx)) return
        ensureChannel(nm)

        val held = p.heldKeys.size
        val until = st.endsAt
        val text = buildString {
            append(if (until == null || until == Long.MAX_VALUE) "Hiding notifications until you turn it off" else "Hiding notifications until ${Format.time(until)}")
            if (p.showHiddenCount) append(" · $held hidden")
        }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val off = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, ActionReceiver::class.java).setAction(ACTION_TURN_OFF),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bedtime)
            .setContentTitle("Bedtime is on")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Turn off", off).build())
            .build()
        runCatching { nm.notify(ID, n) }.onFailure { Log.w(TAG, "status notification failed", it) }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == StatusNotification.ACTION_TURN_OFF) Bedtime.stopNow(context)
    }
}
