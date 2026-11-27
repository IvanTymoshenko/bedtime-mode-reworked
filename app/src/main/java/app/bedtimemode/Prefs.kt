package app.bedtimemode

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.DayOfWeek

enum class OverrideMode { NONE, ON, OFF }

/** All persisted state. Plain SharedPreferences: small, synchronous, readable from any component. */
class Prefs(context: Context) {
    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("bedtime", Context.MODE_PRIVATE)

    // --- Schedule ---
    var scheduleEnabled: Boolean
        get() = sp.getBoolean("schedule_enabled", true)
        set(v) = sp.edit { putBoolean("schedule_enabled", v) }

    /** Minutes after midnight. */
    var startMinute: Int
        get() = sp.getInt("start_minute", 23 * 60)
        set(v) = sp.edit { putInt("start_minute", v) }

    var endMinute: Int
        get() = sp.getInt("end_minute", 7 * 60)
        set(v) = sp.edit { putInt("end_minute", v) }

    /** Days on which a bedtime session STARTS. */
    var days: Set<DayOfWeek>
        get() = sp.getStringSet("days", null)?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }?.toSet()
            ?: DayOfWeek.entries.toSet()
        set(v) = sp.edit { putStringSet("days", v.map { it.name }.toSet()) }

    // --- Options ---
    var useDnd: Boolean
        get() = sp.getBoolean("use_dnd", true)
        set(v) = sp.edit { putBoolean("use_dnd", v) }

    /** Let calls from starred contacts and repeat callers ring through DND. */
    var allowCalls: Boolean
        get() = sp.getBoolean("allow_calls", true)
        set(v) = sp.edit { putBoolean("allow_calls", v) }

    /** Also hide notifications already in the shade when bedtime starts. */
    var hideExisting: Boolean
        get() = sp.getBoolean("hide_existing", true)
        set(v) = sp.edit { putBoolean("hide_existing", v) }

    /** Also hide notifications from Android itself and background system services. */
    var hideSystem: Boolean
        get() = sp.getBoolean("hide_system", true)
        set(v) = sp.edit { putBoolean("hide_system", v) }

    /** Show the ongoing "Bedtime is on" notification while active. */
    var showStatusNotification: Boolean
        get() = sp.getBoolean("show_status_notification", true)
        set(v) = sp.edit { putBoolean("show_status_notification", v) }

    /** Add "· N hidden" to the status notification. Off by default (ColorOS 16 didn't show it). */
    var showHiddenCount: Boolean
        get() = sp.getBoolean("show_hidden_count", false)
        set(v) = sp.edit { putBoolean("show_hidden_count", v) }

    var allowedPackages: Set<String>
        get() = sp.getStringSet("allowed_packages", emptySet())!!.toSet()
        set(v) = sp.edit { putStringSet("allowed_packages", v) }

    // --- Manual override ---
    var overrideMode: OverrideMode
        get() = runCatching { OverrideMode.valueOf(sp.getString("override_mode", "NONE")!!) }.getOrDefault(OverrideMode.NONE)
        set(v) = sp.edit { putString("override_mode", v.name) }

    var overrideUntil: Long
        get() = sp.getLong("override_until", 0L)
        set(v) = sp.edit { putLong("override_until", v) }

    fun setOverride(mode: OverrideMode, until: Long) = sp.edit {
        putString("override_mode", mode.name)
        putLong("override_until", until)
    }

    fun clearOverride() = setOverride(OverrideMode.NONE, 0L)

    // --- Runtime bookkeeping ---
    /** Whether the engine last applied the "active" state (used to detect transitions). */
    var appliedActive: Boolean
        get() = sp.getBoolean("applied_active", false)
        set(v) = sp.edit { putBoolean("applied_active", v) }

    var sessionStartedAt: Long
        get() = sp.getLong("session_started_at", 0L)
        set(v) = sp.edit { putLong("session_started_at", v) }

    var sessionEndsAt: Long
        get() = sp.getLong("session_ends_at", 0L)
        set(v) = sp.edit { putLong("session_ends_at", v) }

    /** Keys of notifications snoozed in the current session (unique, so re-holds don't double count). */
    var heldKeys: Set<String>
        get() = sp.getStringSet("held_keys", emptySet())!!.toSet()
        set(v) = sp.edit { putStringSet("held_keys", v) }

    /** Keys held in the last finished session that haven't come back to the shade yet. */
    var pendingReturnKeys: Set<String>
        get() = sp.getStringSet("pending_return_keys", emptySet())!!.toSet()
        set(v) = sp.edit { putStringSet("pending_return_keys", v) }

    /** JSON array of finished sessions, newest first. See [History]. */
    var historyJson: String
        get() = sp.getString("history", "[]")!!
        set(v) = sp.edit { putString("history", v) }

    /** Main screen look: false = classic Material cards, true = "fancy" dial + tiles. */
    var fancyUi: Boolean
        get() = sp.getBoolean("fancy_ui", false)
        set(v) = sp.edit { putBoolean("fancy_ui", v) }

    /** Accent colour id for the Fancy look (see ui/Fancy.kt `Accents`); null = default (blue). */
    var fancyAccent: String?
        get() = sp.getString("fancy_accent", null)
        set(v) = sp.edit { putString("fancy_accent", v) }

    // --- Onboarding / updates ---
    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(v) = sp.edit { putBoolean("onboarding_done", v) }

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(v) = sp.edit { putLong("last_update_check", v) }

    var availableUpdateJson: String?
        get() = sp.getString("available_update", null)
        set(v) = sp.edit { putString("available_update", v) }

    var notifiedVersion: String?
        get() = sp.getString("notified_version", null)
        set(v) = sp.edit { putString("notified_version", v) }

    var zenRuleId: String?
        get() = sp.getString("zen_rule_id", null)
        set(v) = sp.edit { putString("zen_rule_id", v) }
}
