package app.bedtimemode

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Format {
    private val time = DateTimeFormatter.ofPattern("HH:mm")
    private val dayTime = DateTimeFormatter.ofPattern("EEE HH:mm")
    private fun z(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    fun time(ms: Long): String = time.format(z(ms))
    fun dayTime(ms: Long): String = dayTime.format(z(ms))
    fun minute(m: Int) = "%02d:%02d".format(m / 60, m % 60)
}

data class SetupState(
    val listener: Boolean,
    val dnd: Boolean,
    val exactAlarm: Boolean,
    val battery: Boolean,
    val postNotifications: Boolean,
)

/** Permission checks and the settings screens to grant them. */
object Setup {
    fun state(ctx: Context) = SetupState(
        listener = ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx),
        dnd = Dnd.hasAccess(ctx),
        exactAlarm = Alarms.canExact(ctx),
        battery = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
        postNotifications = StatusNotification.canPost(ctx),
    )

    /** Ask the system to add our tile to Quick Settings (Android 13+ shows a one-tap dialog). */
    fun requestAddTile(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val sbm = ctx.getSystemService(android.app.StatusBarManager::class.java)
        runCatching {
            sbm.requestAddTileService(
                android.content.ComponentName(ctx, BedtimeTileService::class.java),
                ctx.getString(R.string.tile_label),
                android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_bedtime),
                ctx.mainExecutor,
            ) { result -> android.util.Log.i(TAG, "add tile result: $result") }
        }
    }

    private fun pkgUri(ctx: Context) = Uri.parse("package:${ctx.packageName}")

    private fun launch(ctx: Context, vararg intents: Intent) {
        for (i in intents) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }

    fun openListenerSettings(ctx: Context) {
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                BedtimeListenerService.component(ctx).flattenToString(),
            )
        } else null
        launch(ctx, *listOfNotNull(detail, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)).toTypedArray())
    }

    fun openDndSettings(ctx: Context) =
        launch(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    fun openExactAlarmSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            launch(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri(ctx)), appDetails(ctx))
        }
    }

    @android.annotation.SuppressLint("BatteryLife")
    fun requestBatteryExemption(ctx: Context) = launch(
        ctx,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(ctx)),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    private fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx))

    fun openZenSettings(ctx: Context) = launch(
        ctx,
        Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),
        Intent("android.settings.ZEN_MODE_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun openAppNotificationSettings(ctx: Context) = launch(
        ctx,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
        appDetails(ctx),
    )

    /** ColorOS hides "Auto-launch" / "Allow background activity" in the app's info page. */
    fun openAppDetails(ctx: Context) = launch(ctx, appDetails(ctx))
}
