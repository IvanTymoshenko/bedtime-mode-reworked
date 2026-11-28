package app.bedtimemode.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.bedtimemode.Bedtime
import app.bedtimemode.BedtimeListenerService
import app.bedtimemode.Dnd
import app.bedtimemode.Format
import app.bedtimemode.History
import app.bedtimemode.SessionRecord
import app.bedtimemode.Prefs
import app.bedtimemode.Setup
import app.bedtimemode.SetupState
import app.bedtimemode.Status
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

internal data class UiState(
    val status: Status,
    val setup: SetupState,
    val listenerConnected: Boolean,
    val scheduleEnabled: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val days: Set<DayOfWeek>,
    val useDnd: Boolean,
    val allowCalls: Boolean,
    val greyOnSchedule: Boolean,
    val greyOnManual: Boolean,
    val hideExisting: Boolean,
    val hideSystem: Boolean,
    val showStatusNotification: Boolean,
    val showHiddenCount: Boolean,
    val allowedCount: Int,
    val heldApps: Map<String, Int>,
    val history: List<SessionRecord>,
)

internal fun readState(ctx: Context, p: Prefs) = UiState(
    status = Bedtime.evaluate(p),
    setup = Setup.state(ctx),
    listenerConnected = BedtimeListenerService.isConnected,
    scheduleEnabled = p.scheduleEnabled,
    startMinute = p.startMinute,
    endMinute = p.endMinute,
    days = p.days,
    useDnd = p.useDnd,
    allowCalls = p.allowCalls,
    greyOnSchedule = p.greyOnSchedule,
    greyOnManual = p.greyOnManual,
    hideExisting = p.hideExisting,
    hideSystem = p.hideSystem,
    showStatusNotification = p.showStatusNotification,
    showHiddenCount = p.showHiddenCount,
    allowedCount = p.allowedPackages.size,
    heldApps = History.appCounts(p.heldKeys),
    history = History.read(p),
)

internal fun appLabel(ctx: Context, pkg: String): String = when (pkg) {
    "com.android.shell" -> "Test (adb)"
    else -> runCatching {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

internal fun appsSummary(ctx: Context, apps: Map<String, Int>): String =
    apps.entries.joinToString(" · ") { "${appLabel(ctx, it.key)} ${it.value}" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(showUpdate: Boolean = false, onUpdateShown: () -> Unit = {}) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var showApps by remember { mutableStateOf(false) }
    var showHeld by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var sessionDetail by remember { mutableStateOf<SessionRecord?>(null) }
    var updateKey by remember { mutableIntStateOf(0) }

    val s = rememberUiState(prefs)
    val changed: () -> Unit = { Bedtime.sync(ctx, "settings") }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Bedtime Mode Reworked") },
            actions = { TextButton(onClick = { prefs.fancyUi = true }) { Text("Fancy look") } },
        )
    }) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
            val status: @Composable () -> Unit = {
                UpdateCard(forceCheck = showUpdate, onShown = onUpdateShown, refreshKey = updateKey)
                StatusCard(s, ctx, onToggle = { Bedtime.toggle(ctx) })
                HistoryCard(s, ctx, onShowHeld = { showHeld = true }, onShowAll = { showHistory = true }, onSession = { sessionDetail = it })
            }
            val settings: @Composable () -> Unit = {
                if (!setupOk(s)) PermissionsCard(s, ctx)
                ScheduleCard(s, ctx, prefs, changed)
                SettingsSections(s, prefs, ctx, changed, onApps = { showApps = true })
                LookCard(s, prefs, fancy = false)
                if (setupOk(s)) PermissionsCard(s, ctx)
                AboutCard(onChecked = { updateKey++ })
                Text(
                    "Hidden notifications come back to the shade as soon as bedtime ends or you turn it off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
            if (maxWidth >= 600.dp) {
                // Landscape / unfolded: two independently scrolling columns.
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { status() }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { settings() }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    status()
                    settings()
                }
            }
        }
    }

    if (showHeld) HeldLogDialog(onDismiss = { showHeld = false })
    if (showHistory) HistoryDialog(s.history, onSession = { sessionDetail = it }, onDismiss = { showHistory = false })
    sessionDetail?.let { SessionDetailDialog(it, onDismiss = { sessionDetail = null }) }
    if (showApps) AllowedAppsDialog(prefs, onDismiss = { showApps = false; changed() })
}

/** Live [UiState]: re-read on any pref change, on resume (after a sync) and every 30 s. */
@Composable
internal fun rememberUiState(prefs: Prefs): UiState {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> tick++ }
        prefs.sp.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.sp.unregisterOnSharedPreferenceChangeListener(l) }
    }
    LifecycleResumeEffect(Unit) {
        Bedtime.sync(ctx, "app-resume")
        tick++
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000); tick++
        }
    }
    return remember(tick) { readState(ctx, prefs) }
}

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun StatusCard(s: UiState, ctx: Context, onToggle: () -> Unit) {
    val st = s.status
    val colors = if (st.active) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else CardDefaults.cardColors()
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (st.active) "Bedtime is on" else "Bedtime is off",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val detail = when {
                st.active && st.endsAt == Bedtime.FOREVER ->
                    "Turned on manually — stays on until you turn it off · ${s.heldApps.values.sum()} hidden so far"
                st.active && st.endsAt != null ->
                    "Notifications hidden until ${Format.time(st.endsAt)} · ${s.heldApps.values.sum()} hidden so far"
                st.nextChangeAt != null ->
                    (if (st.manual) "Paused. " else "") + "Next bedtime: ${Format.dayTime(st.nextChangeAt)}"
                else -> "No upcoming bedtime scheduled"
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (st.active && s.heldApps.isNotEmpty()) {
                Text(appsSummary(ctx, s.heldApps), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggle) { Text(if (st.active) "Turn off" else "Turn on now") }
                TextButton(onClick = { Setup.requestAddTile(ctx) }) { Text("Add Quick Settings tile") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(s: UiState, ctx: Context, p: Prefs, settingsChanged: () -> Unit) {
    // A new schedule replaces any manual on/off that was based on the old one.
    val changed = { p.clearOverride(); settingsChanged() }
    SectionCard("Schedule") {
        SwitchRow("Turn on automatically", null, s.scheduleEnabled) { p.scheduleEnabled = it; changed() }
        Row(Modifier.fillMaxWidth()) {
            TimeField("Starts", s.startMinute, Modifier.weight(1f)) { pickTime(ctx, s.startMinute) { m -> p.startMinute = m; changed() } }
            TimeField("Ends", s.endMinute, Modifier.weight(1f)) { pickTime(ctx, s.endMinute) { m -> p.endMinute = m; changed() } }
        }
        Text("Starts on", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in DayOfWeek.entries) {
                FilterChip(
                    selected = d in s.days,
                    onClick = { p.days = if (d in s.days) s.days - d else s.days + d; changed() },
                    label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    leadingIcon = if (d in s.days) {
                        { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Check, null) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun TimeField(label: String, minute: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Format.minute(minute), style = MaterialTheme.typography.displaySmall)
    }
}

internal fun pickTime(ctx: Context, current: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPicked(h * 60 + m) }, current / 60, current % 60, true).show()
}

@Composable
private fun HistoryCard(
    s: UiState,
    ctx: Context,
    onShowHeld: () -> Unit,
    onShowAll: () -> Unit,
    onSession: (SessionRecord) -> Unit,
) {
    SectionCard("History") {
        FilledTonalButton(onClick = onShowHeld) { Text("See everything that was hidden") }
        if (s.history.isEmpty()) {
            Text(
                "No finished sessions yet. After each bedtime you'll see here what was hidden and what came back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (r in s.history.take(HISTORY_PREVIEW)) SessionRow(ctx, r, onClick = { onSession(r) })
        if (s.history.size > HISTORY_PREVIEW) {
            TextButton(onClick = onShowAll) { Text("Show all (${s.history.size})") }
        }
    }
}

private const val HISTORY_PREVIEW = 3
