package app.bedtimemode

import android.app.AutomaticZenRule
import android.app.NotificationManager
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
    private const val RULE_NAME = "Bedtime Mode Reworked"
    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun hasAccess(ctx: Context) = nm(ctx).isNotificationPolicyAccessGranted

    private fun conditionUri(ctx: Context): Uri = Uri.parse("condition://${ctx.packageName}/bedtime")

    private fun buildRule(ctx: Context): AutomaticZenRule {
        val p = Prefs(ctx)
        val policy = ZenPolicy.Builder()
            .disallowAllSounds()
            .allowAlarms(true)
            .allowCalls(if (p.allowCalls) ZenPolicy.PEOPLE_TYPE_STARRED else ZenPolicy.PEOPLE_TYPE_NONE)
            .allowRepeatCallers(p.allowCalls)
            .hideAllVisualEffects()
            .build()
        @Suppress("DEPRECATION")
        return AutomaticZenRule(
            RULE_NAME,
            null,
            ComponentName(ctx, MainActivity::class.java),
            conditionUri(ctx),
            policy,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            true,
        )
    }

    private fun ensureRule(ctx: Context): String {
        val p = Prefs(ctx)
        val nm = nm(ctx)
        p.zenRuleId?.let { id ->
            val existing = nm.getAutomaticZenRule(id)
            if (existing != null) {
                // Keep the rule's visible name in sync after a rename.
                if (existing.name != RULE_NAME) runCatching { nm.updateAutomaticZenRule(id, buildRule(ctx)) }
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
        if (!hasAccess(ctx)) return
        runCatching { nm(ctx).updateAutomaticZenRule(ensureRule(ctx), buildRule(ctx)) }
            .onFailure { Log.w(TAG, "dnd: update failed", it) }
    }

    fun apply(ctx: Context, on: Boolean) {
        if (!hasAccess(ctx)) {
            if (on) Log.w(TAG, "dnd: no policy access, cannot silence")
            return
        }
        runCatching {
            val id = ensureRule(ctx)
            val state = if (on) Condition.STATE_TRUE else Condition.STATE_FALSE
            nm(ctx).setAutomaticZenRuleState(id, Condition(conditionUri(ctx), if (on) "Bedtime" else "Off", state))
            Log.i(TAG, "dnd: rule $id -> ${if (on) "ON" else "OFF"}")
        }.onFailure { Log.w(TAG, "dnd: apply failed", it) }
    }
}
