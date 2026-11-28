package app.bedtimemode.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.bedtimemode.BedtimeListenerService
import app.bedtimemode.Dnd
import app.bedtimemode.Prefs
import app.bedtimemode.Setup

/*
 * Settings shared by the Classic and Fancy looks, grouped by topic with short, plain wording.
 * Any new setting goes here so both looks get it.
 */

internal fun setupOk(s: UiState) =
    s.setup.let { it.listener && it.dnd && it.exactAlarm && it.battery && it.postNotifications } && s.listenerConnected

/** A setting with a switch. The whole row is tappable. */
@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A setting that opens something, with its current value on the right. */
@Composable
private fun LinkRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("$value  ›", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun LookCard(s: UiState, p: Prefs, fancy: Boolean, onClassic: () -> Unit = {}) {
    SectionCard("Look") {
        SwitchRow("Fancy look", null, fancy) {
            p.fancyUi = it
            if (!it) onClassic()
        }
        if (fancy) {
            Text("Colour", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
            AccentPicker(selected = LocalAccent.current.id) { p.fancyAccent = it }
        }
    }
}

/** Sound, What to hide, and the "Bedtime is on" notice. */
@Composable
internal fun SettingsSections(s: UiState, p: Prefs, ctx: Context, changed: () -> Unit, onApps: () -> Unit) {
    SoundCard(s, p, ctx, changed)
    ScreenCard(s, p, ctx)
    HideCard(s, p, ctx, changed, onApps)
    NoticeCard(s, p, changed)
}

@Composable
internal fun SoundCard(s: UiState, p: Prefs, ctx: Context, changed: () -> Unit) {
    SectionCard("Sound") {
        SwitchRow("Mute sounds and vibration", "Alarms still ring", s.useDnd) {
            p.useDnd = it; changed()
        }
        SwitchRow("Let calls through", "Starred contacts, or anyone who calls twice", s.allowCalls) {
            p.allowCalls = it; Dnd.refreshRule(ctx); changed()
        }
    }
}

/** Black & white screen, separately for scheduled nights and for turning it on by hand. */
@Composable
internal fun ScreenCard(s: UiState, p: Prefs, ctx: Context) {
    if (!Dnd.greySupported) return
    SectionCard("Black & white screen") {
        SwitchRow("At scheduled bedtime", "When the schedule turns bedtime on", s.greyOnSchedule) {
            p.greyOnSchedule = it; Dnd.refreshRule(ctx)
        }
        SwitchRow("When turned on by hand", "From Quick Settings or this app", s.greyOnManual) {
            p.greyOnManual = it; Dnd.refreshRule(ctx)
        }
    }
}

@Composable
internal fun HideCard(s: UiState, p: Prefs, ctx: Context, changed: () -> Unit, onApps: () -> Unit) {
    SectionCard("What to hide") {
        SwitchRow("Hide what's already there", "When bedtime starts", s.hideExisting) {
            p.hideExisting = it; changed()
        }
        SwitchRow("Hide system notifications", "Like USB, hotspot, battery", s.hideSystem) {
            p.hideSystem = it
            if (!it) BedtimeListenerService.releaseSystem(ctx)
            changed()
        }
        LinkRow("Apps that can still notify", if (s.allowedCount == 0) "None" else "${s.allowedCount}", onApps)
    }
}

@Composable
internal fun NoticeCard(s: UiState, p: Prefs, changed: () -> Unit) {
    SectionCard("\"Bedtime is on\" notice") {
        SwitchRow("Show the notice", "In the notification shade, with a Turn off button", s.showStatusNotification) {
            p.showStatusNotification = it; changed()
        }
        if (s.showStatusNotification) {
            SwitchRow("Show how many are hidden", null, s.showHiddenCount) {
                p.showHiddenCount = it; changed()
            }
        }
    }
}

/** Compact "All set" when everything is granted; a list with Fix buttons when something is missing. */
@Composable
internal fun PermissionsCard(s: UiState, ctx: Context) {
    val su = s.setup
    if (setupOk(s)) {
        SectionCard("Permissions") {
            Text("✓ All set", style = MaterialTheme.typography.bodyLarge)
            Text(
                "OPPO / OnePlus: also allow background activity in App info → Battery usage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { Setup.openAppDetails(ctx) }) { Text("Open App info") }
        }
        return
    }
    SectionCard("Permissions needed") {
        PermissionRow("Notification access", "To hide notifications", su.listener) { Setup.openListenerSettings(ctx) }
        if (su.listener && !s.listenerConnected) {
            Text(
                "Access is on but not working yet. Turn it off and on again, or restart the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        PermissionRow("Do Not Disturb access", "To mute sounds", su.dnd) { Setup.openDndSettings(ctx) }
        PermissionRow("Alarms & reminders", "To start and stop on time", su.exactAlarm) { Setup.openExactAlarmSettings(ctx) }
        PermissionRow("Notifications", "For the \"Bedtime is on\" notice", su.postNotifications) { Setup.openAppNotificationSettings(ctx) }
        PermissionRow("No battery limits", "So it keeps running at night", su.battery) { Setup.requestBatteryExemption(ctx) }
        TextButton(onClick = { Setup.openAppDetails(ctx) }) { Text("Open App info") }
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text((if (ok) "✓ " else "✗ ") + title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ok) FilledTonalButton(onClick = onFix) { Text("Fix") }
    }
}
