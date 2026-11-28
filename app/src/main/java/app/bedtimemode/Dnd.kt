package app.bedtimemode

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.os.Build
import android.service.notification.ZenDeviceEffects
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import android.util.Log

/**
 * Silences the phone via an app-owned AutomaticZenRule (shows up as "Bedtime Mode Reworked" in the
 * system DND settings). It never touches the user's global DND policy, so turning it off
 * leaves everything as it was. This only silences; hiding from the shade is the listener's job.
 */
object Dnd {
    private fun ruleName(ctx: Context) = ctx.getString(R.string.app_name)
    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun hasAccess(ctx: Context) = nm(ctx).isNotificationPolicyAccessGranted

    private fun conditionUri(ctx: Context): Uri = Uri.parse("condition://${ctx.packageName}/bedtime")

    /** Black & white screen is a rule "device effect" (Android 15+), no extra permission. */
    val greySupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    /** Whether the screen should be grey for the session described by [st]. */
    fun wantsGrey(p: Prefs, st: Status) = st.active && (if (st.manual) p.greyOnManual else p.greyOnSchedule)

    /**
     * [mute] false (sound option off) but [grey] true: the rule still has to be active to turn the
     * screen grey, so it then lets every sound and visual through.
     */
    private fun buildRule(ctx: Context, mute: Boolean = true, grey: Boolean = false): AutomaticZenRule {
        val p = Prefs(ctx)
        val policy = if (mute) {
            ZenPolicy.Builder()
                .disallowAllSounds()
                .allowAlarms(true)
                .allowCalls(if (p.allowCalls) ZenPolicy.PEOPLE_TYPE_STARRED else ZenPolicy.PEOPLE_TYPE_NONE)
                .allowRepeatCallers(p.allowCalls)
                .hideAllVisualEffects()
                .build()
        } else {
            ZenPolicy.Builder().allowAllSounds().showAllVisualEffects().build()
        }
        @Suppress("DEPRECATION")
        val rule = AutomaticZenRule(
            ruleName(ctx),
            null,
            ComponentName(ctx, MainActivity::class.java),
            conditionUri(ctx),
            policy,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            true,
        )
        if (greySupported) {
            rule.deviceEffects = ZenDeviceEffects.Builder().setShouldDisplayGrayscale(grey).build()
        }
        return rule
    }

    /** What the rule was last built with, so [apply] only rebuilds it on change. */
    private var lastBuilt: Pair<Boolean, Boolean>? = null

    private fun ensureRule(ctx: Context): String {
        val p = Prefs(ctx)
        val nm = nm(ctx)
        p.zenRuleId?.let { id ->
            val existing = nm.getAutomaticZenRule(id)
            if (existing != null) {
                // Keep the rule's visible name in sync after a rename.
                if (existing.name != ruleName(ctx)) runCatching { nm.updateAutomaticZenRule(id, buildRule(ctx)) }
                return id
            }
        }
        val id = nm.addAutomaticZenRule(buildRule(ctx))
        p.zenRuleId = id
        Log.i(TAG, "dnd: created zen rule $id")
        return id
    }

    /** Re-apply rule settings after the user changes options. */
    fun refreshRule(ctx: Context) {
        lastBuilt = null
        Bedtime.sync(ctx, "rule-changed")
    }

    /** [mute] silences; [grey] turns the screen black & white. Rule is active if either is on. */
    fun apply(ctx: Context, mute: Boolean, grey: Boolean) {
        val on = mute || grey
        if (!hasAccess(ctx)) {
            if (on) Log.w(TAG, "dnd: no policy access, cannot silence")
            return
        }
        runCatching {
            val id = ensureRule(ctx)
            // Effects only matter while on; when off keep the last build so nothing flips mid-release.
            val want = if (on) mute to grey else lastBuilt ?: (true to false)
            if (lastBuilt != want) {
                nm(ctx).updateAutomaticZenRule(id, buildRule(ctx, want.first, want.second))
                lastBuilt = want
                Log.i(TAG, "dnd: rule rebuilt mute=${want.first} grey=${want.second}")
            }
            val state = if (on) Condition.STATE_TRUE else Condition.STATE_FALSE
            nm(ctx).setAutomaticZenRuleState(id, Condition(conditionUri(ctx), if (on) "Bedtime" else "Off", state))
            Log.i(TAG, "dnd: rule $id -> ${if (on) "ON" else "OFF"}")
        }.onFailure { Log.w(TAG, "dnd: apply failed", it) }
    }
}
