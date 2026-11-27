package app.bedtimemode.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.bedtimemode.Bedtime
import app.bedtimemode.BedtimeListenerService
import app.bedtimemode.Dnd
import app.bedtimemode.Format
import app.bedtimemode.Prefs
import app.bedtimemode.SessionRecord
import app.bedtimemode.Setup
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** "Fancy" look: graphite + amber, schedule dial, rounded tiles. Same engine and prefs as classic. */
private object F {
    val bg = Color(0xFF101112)
    val surface = Color(0xFF1B1C1E)
    val raised = Color(0xFF26282B)
    val track = Color(0xFF2C2E31)
    val text = Color(0xFFEDEDED)
    val muted = Color(0xFFA8A8A8)
    val accent: Color @Composable get() = LocalAccent.current.color
    val onAccent: Color @Composable get() = LocalAccent.current.on
    val accentDim: Color @Composable get() = LocalAccent.current.dim
    val error = Color(0xFFFF8A80)
}

/** Accent colours the user can pick for the Fancy look. First one is the default. */
data class Accent(val id: String, val name: String, val color: Color, val on: Color, val dim: Color, val onDim: Color)

val Accents = listOf(
    Accent("blue", "Blue", Color(0xFFAAC7FF), Color(0xFF0A305F), Color(0xFF1F3557), Color(0xFFD6E3FF)),
    Accent("amber", "Amber", Color(0xFFF5B94A), Color(0xFF1A1300), Color(0xFF3A2E12), Color(0xFFFFE2A8)),
    Accent("coral", "Coral", Color(0xFFFF8A5B), Color(0xFF2A0E00), Color(0xFF4A2414), Color(0xFFFFDBCC)),
    Accent("rose", "Rose", Color(0xFFF4A7C8), Color(0xFF3A0A22), Color(0xFF4A2235), Color(0xFFFFD9E6)),
    Accent("green", "Green", Color(0xFF8BD88E), Color(0xFF00390F), Color(0xFF1E3A22), Color(0xFFC8F5C9)),
    Accent("teal", "Teal", Color(0xFF5ED6C8), Color(0xFF00332E), Color(0xFF173A36), Color(0xFFB9F3EB)),
    Accent("lime", "Lime", Color(0xFFC9E07A), Color(0xFF2A3300), Color(0xFF343D14), Color(0xFFE6F5B8)),
    Accent("silver", "Silver", Color(0xFFD4D4D4), Color(0xFF1E1E1E), Color(0xFF333436), Color(0xFFF0F0F0)),
)

fun accentById(id: String?): Accent = Accents.firstOrNull { it.id == id } ?: Accents.first()

internal val LocalAccent = staticCompositionLocalOf { Accents.first() }

@Composable
fun FancyTheme(accentId: String?, content: @Composable () -> Unit) {
    val a = accentById(accentId)
    val scheme = darkColorScheme(
        primary = a.color, onPrimary = a.on,
        primaryContainer = a.dim, onPrimaryContainer = a.onDim,
        secondary = a.color, onSecondary = a.on,
        secondaryContainer = a.dim, onSecondaryContainer = a.onDim,
        tertiary = a.color, onTertiary = a.on,
        tertiaryContainer = a.dim, onTertiaryContainer = a.onDim,
        background = F.bg, onBackground = F.text,
        surface = F.bg, onSurface = F.text,
        surfaceVariant = F.raised, onSurfaceVariant = F.muted,
        surfaceContainerLowest = F.bg, surfaceContainerLow = F.surface, surfaceContainer = F.surface,
        surfaceContainerHigh = F.surface, surfaceContainerHighest = F.surface,
        outline = Color(0xFF55575B), outlineVariant = F.track,
        error = F.error,
    )
    CompositionLocalProvider(LocalAccent provides a) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun FancyScreen(showUpdate: Boolean = false, onUpdateShown: () -> Unit = {}) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val s = rememberUiState(prefs)
    var showApps by remember { mutableStateOf(false) }
    var showHeld by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var sessionDetail by remember { mutableStateOf<SessionRecord?>(null) }
    var updateKey by remember { mutableIntStateOf(0) }
    val changed: () -> Unit = { Bedtime.sync(ctx, "settings") }
    // A new schedule replaces any manual on/off that was based on the old one.
    val scheduleChanged: () -> Unit = { prefs.clearOverride(); changed() }

    val setupOk = setupOk(s)

    Surface(Modifier.fillMaxSize(), color = F.bg, contentColor = F.text) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth >= 600.dp
            val header: @Composable () -> Unit = {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Bedtime", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { showSettings = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = F.surface, contentColor = F.muted),
                    ) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                }
            }
            val hero: @Composable () -> Unit = {
                HeroTile(s, prefs, onToggle = { Bedtime.toggle(ctx) }, onCommit = scheduleChanged)
            }
            val schedule: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeTile("Bedtime", s.startMinute, Modifier.weight(1f)) {
                        pickTime(ctx, s.startMinute) { m -> prefs.startMinute = m; scheduleChanged() }
                    }
                    TimeTile("Wake up", s.endMinute, Modifier.weight(1f)) {
                        pickTime(ctx, s.endMinute) { m -> prefs.endMinute = m; scheduleChanged() }
                    }
                }
                DaysTile(s.days) { d ->
                    prefs.days = if (d in s.days) s.days - d else s.days + d; scheduleChanged()
                }
            }
            val rest: @Composable () -> Unit = {
                UpdateCard(forceCheck = showUpdate, onShown = onUpdateShown, refreshKey = updateKey)
                if (!setupOk) SetupNeededTile { showSettings = true }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeldTile(s, ctx, Modifier.weight(1f)) { showHeld = true }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToggleTile("Mute sounds", s.useDnd) { prefs.useDnd = it; changed() }
                        ToggleTile("Let calls through", s.allowCalls) { prefs.allowCalls = it; Dnd.refreshRule(ctx); changed() }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleTile("Hide system", s.hideSystem, Modifier.weight(1f)) {
                        prefs.hideSystem = it
                        if (!it) BedtimeListenerService.releaseSystem(ctx)
                        changed()
                    }
                    ToggleTile("Hide what's there", s.hideExisting, Modifier.weight(1f)) { prefs.hideExisting = it; changed() }
                }
                HistoryTile(s, onSession = { sessionDetail = it }, onShowAll = { showHistory = true })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoTile(
                        "Can still notify",
                        if (s.allowedCount == 0) "None" else "${s.allowedCount} app${if (s.allowedCount == 1) "" else "s"}",
                        Modifier.weight(1f),
                    ) { showApps = true }
                    InfoTile("Everything hidden", "Open log", Modifier.weight(1f)) { showHeld = true }
                }
                Text(
                    "Hidden notifications come back to the shade as soon as bedtime ends or you turn it off.",
                    fontSize = 12.sp, color = F.muted, modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(24.dp))
            }

            val gap = Arrangement.spacedBy(12.dp)
            if (wide) {
                Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = gap) {
                        header(); hero(); schedule()
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 68.dp), verticalArrangement = gap) {
                        rest()
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    verticalArrangement = gap,
                ) {
                    header(); hero(); schedule(); rest()
                }
            }
        }
    }

    if (showSettings) FancySettings(s, prefs, onChecked = { updateKey++ }, onApps = { showApps = true }) { showSettings = false }
    if (showHeld) HeldLogDialog(onDismiss = { showHeld = false })
    if (showHistory) HistoryDialog(s.history, onSession = { sessionDetail = it }, onDismiss = { showHistory = false })
    sessionDetail?.let { SessionDetailDialog(it, onDismiss = { sessionDetail = null }) }
    if (showApps) AllowedAppsDialog(prefs, onDismiss = { showApps = false; changed() })
}

// ---------- Tiles ----------

@Composable
private fun Tile(
    modifier: Modifier = Modifier,
    radius: Int = 28,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius.dp)
    Surface(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = F.surface,
        contentColor = F.text,
    ) { content() }
}

@Composable
private fun Label(text: String) = Text(text, fontSize = 13.sp, color = F.muted)

@Composable
private fun TimeTile(label: String, minute: Int, modifier: Modifier, onClick: () -> Unit) {
    Tile(modifier, onClick = onClick) {
        Column(Modifier.heightIn(min = 108.dp).padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Label(label)
            Spacer(Modifier.height(16.dp))
            Text(Format.minute(minute), fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DaysTile(days: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    Tile {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("Starts on")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in DayOfWeek.entries) {
                    val on = d in days
                    Box(
                        Modifier.weight(1f).height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) F.accent else F.raised)
                            .clickable { onToggle(d) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = if (on) F.onAccent else F.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeldTile(s: UiState, ctx: Context, modifier: Modifier, onClick: () -> Unit) {
    val n = s.heldApps.values.sum()
    Tile(modifier, onClick = onClick) {
        Column(Modifier.height(172.dp).padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Label("Held right now")
            Column {
                Text("$n", fontSize = 54.sp, fontWeight = FontWeight.Bold, color = F.accent, lineHeight = 56.sp)
                Text(
                    if (n == 0) (if (s.status.active) "Nothing yet" else "Bedtime is off") else appsSummary(ctx, s.heldApps),
                    fontSize = 13.sp, color = F.muted, maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ToggleTile(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Tile(modifier, onClick = { onChange(!checked) }) {
        Row(
            Modifier.height(80.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Tile(modifier, onClick = onClick) {
        Column(Modifier.height(108.dp).padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Label(label)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SetupNeededTile(onClick: () -> Unit) {
    Tile(onClick = onClick) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Setup needed", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = F.error)
            Text("Some permissions are missing, so bedtime may not work. Tap to fix.", fontSize = 13.sp, color = F.muted)
        }
    }
}

@Composable
private fun HistoryTile(s: UiState, onSession: (SessionRecord) -> Unit, onShowAll: () -> Unit) {
    Tile {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Last nights", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            if (s.history.isEmpty()) {
                Text(
                    "No finished sessions yet. After each bedtime you'll see here what was hidden and what came back.",
                    fontSize = 13.sp, color = F.muted,
                )
            }
            for (r in s.history.take(3)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onSession(r) }
                        .heightIn(min = 60.dp).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(F.raised),
                        contentAlignment = Alignment.Center,
                    ) { Text("${r.held}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = F.accent) }
                    Column(Modifier.weight(1f)) {
                        Text("${Format.dayTime(r.start)} → ${Format.dayTime(r.end)}", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            when {
                                r.held == 0 -> "nothing hidden"
                                r.returned >= r.held -> "all back in the shade"
                                else -> "${r.returned} of ${r.held} back in the shade"
                            },
                            fontSize = 13.sp, color = F.muted,
                        )
                    }
                    Text("›", fontSize = 22.sp, color = F.muted)
                }
            }
            if (s.history.size > 3) {
                Button(
                    onClick = onShowAll,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = F.raised, contentColor = F.accent),
                ) { Text("Show all (${s.history.size})", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ---------- Hero: dial + main button ----------

private fun duration(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(0)
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

@Composable
private fun HeroTile(s: UiState, p: Prefs, onToggle: () -> Unit, onCommit: () -> Unit) {
    val st = s.status
    val now = System.currentTimeMillis()
    // Live drag state (null = not dragging): which handle and its current minute.
    var dragStart by remember(s.startMinute) { mutableIntStateOf(s.startMinute) }
    var dragEnd by remember(s.endMinute) { mutableIntStateOf(s.endMinute) }
    var dragging by remember { mutableStateOf<Boolean?>(null) } // true = start handle, false = end handle

    Tile(radius = 36) {
        Column(
            Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                ScheduleDial(
                    start = dragStart, end = dragEnd,
                    nowMinute = LocalTime.now().let { it.hour * 60 + it.minute },
                    enabled = s.scheduleEnabled,
                    onDragHandle = { isStart, m ->
                        dragging = isStart
                        if (isStart) dragStart = m else dragEnd = m
                    },
                    onDragEnd = {
                        dragging = null
                        if (dragStart != s.startMinute || dragEnd != s.endMinute) {
                            p.startMinute = dragStart; p.endMinute = dragEnd; onCommit()
                        }
                    },
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val (label, big, sub) = when {
                        dragging == true -> Triple("Bedtime", Format.minute(dragStart), "drag to change")
                        dragging == false -> Triple("Wake up", Format.minute(dragEnd), "drag to change")
                        st.active && st.endsAt == Bedtime.FOREVER -> Triple("Bedtime is on", "On", "until you turn it off")
                        st.active && st.endsAt != null -> Triple("Sleeping until", Format.time(st.endsAt), "${duration(st.endsAt - now)} left")
                        st.nextChangeAt != null -> Triple(
                            if (st.manual) "Paused · next bedtime" else "Next bedtime",
                            Format.time(st.nextChangeAt),
                            if (st.nextChangeAt - now < 24 * 3_600_000L) "in ${duration(st.nextChangeAt - now)}" else Format.dayTime(st.nextChangeAt),
                        )
                        else -> Triple("Schedule is off", "Off", "turn it on in settings")
                    }
                    Text(label, fontSize = 13.sp, color = F.muted)
                    Text(big, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Text(sub, fontSize = 13.sp, color = F.accent)
                }
            }
            val buttonText = when {
                !st.active -> "Turn on now"
                st.endsAt == Bedtime.FOREVER || st.manual -> "Turn off"
                else -> "Turn off for tonight"
            }
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = F.accent, contentColor = F.onAccent),
            ) { Text(buttonText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/**
 * 24h ring: 00 at the top, clockwise. Amber arc = bedtime. Drag the moon (start) or the dot (end);
 * snaps to 5 minutes. Touches away from the handles fall through so the page still scrolls.
 */
@Composable
private fun ScheduleDial(
    start: Int,
    end: Int,
    nowMinute: Int,
    enabled: Boolean,
    onDragHandle: (isStart: Boolean, minute: Int) -> Unit,
    onDragEnd: () -> Unit,
) {
    val accent = F.accent
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val stroke = with(density) { 24.dp.toPx() }
    val handleR = with(density) { 17.dp.toPx() }
    val grab = with(density) { 40.dp.toPx() }
    val curStart by rememberUpdatedState(start)
    val curEnd by rememberUpdatedState(end)
    val onHandle by rememberUpdatedState(onDragHandle)
    val onEnd by rememberUpdatedState(onDragEnd)

    fun angleOf(m: Int) = Math.toRadians(m / 1440.0 * 360.0)

    Canvas(
        Modifier.fillMaxSize().alpha(if (enabled) 1f else 0.4f).pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.width / 2f - stroke / 2f - 4f
                fun handlePos(m: Int): Offset {
                    val a = angleOf(m)
                    return Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat())
                }
                val ds = (down.position - handlePos(curStart)).getDistance()
                val de = (down.position - handlePos(curEnd)).getDistance()
                val isStart = when {
                    ds <= grab && ds <= de -> true
                    de <= grab -> false
                    else -> return@awaitEachGesture // not on a handle: let the page scroll
                }
                down.consume()
                var last = if (isStart) curStart else curEnd
                drag(down.id) { change ->
                    change.consume()
                    val d = change.position - c
                    if (hypot(d.x, d.y) < 8f) return@drag
                    var deg = Math.toDegrees(atan2(d.x.toDouble(), -d.y.toDouble()))
                    if (deg < 0) deg += 360.0
                    val m = (((deg / 360.0 * 1440.0) / 5.0).roundToInt() * 5) % 1440
                    if (m != last) {
                        last = m
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onHandle(isStart, m)
                    }
                }
                onEnd()
            }
        },
    ) {
        val c = center
        val r = size.minDimension / 2f - stroke / 2f - 4f
        drawCircle(F.track, radius = r, center = c, style = Stroke(stroke))
        val sweep = ((end - start + 1440) % 1440) / 1440f * 360f
        val topLeft = Offset(c.x - r, c.y - r)
        if (sweep > 0f) {
            drawArc(
                accent, startAngle = start / 1440f * 360f - 90f, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        // Hour labels inside the ring.
        val labelR = r - stroke / 2f - 16.dp.toPx()
        for ((h, t) in listOf(0 to "00", 6 to "06", 12 to "12", 18 to "18")) {
            val a = angleOf(h * 60)
            val layout = measurer.measure(t, TextStyle(color = F.muted, fontSize = 12.sp))
            val pos = Offset(c.x + labelR * sin(a).toFloat(), c.y - labelR * cos(a).toFloat())
            drawText(layout, topLeft = pos - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
        fun pos(m: Int, rr: Float = r): Offset {
            val a = angleOf(m)
            return Offset(c.x + rr * sin(a).toFloat(), c.y - rr * cos(a).toFloat())
        }
        // Current time: small light tick on the ring.
        drawCircle(F.text, radius = 3.dp.toPx(), center = pos(nowMinute))
        // Start handle: a moon.
        val ps = pos(start)
        drawCircle(F.surface, radius = handleR, center = ps)
        drawCircle(accent, radius = handleR, center = ps, style = Stroke(3.dp.toPx()))
        drawCircle(accent, radius = 7.dp.toPx(), center = ps)
        drawCircle(F.surface, radius = 6.dp.toPx(), center = ps + Offset(4.dp.toPx(), -3.dp.toPx()))
        // End handle: a dot.
        val pe = pos(end)
        drawCircle(F.surface, radius = handleR, center = pe)
        drawCircle(accent, radius = handleR, center = pe, style = Stroke(3.dp.toPx()))
        drawCircle(accent, radius = 5.dp.toPx(), center = pe)
    }
}

// ---------- Settings page ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentPicker(selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (a in Accents) {
            val on = a.id == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(if (on) F.text else Color.Transparent)
                        .padding(if (on) 3.dp else 0.dp)
                        .clip(CircleShape).background(F.bg).padding(if (on) 3.dp else 0.dp)
                        .clip(CircleShape).background(a.color)
                        .clickable(onClickLabel = a.name) { onPick(a.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = a.on, modifier = Modifier.size(22.dp))
                }
                Text(a.name, fontSize = 12.sp, color = if (on) F.text else F.muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun FancySettings(s: UiState, p: Prefs, onChecked: () -> Unit, onApps: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val changed: () -> Unit = { Bedtime.sync(ctx, "settings") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = F.bg, contentColor = F.text) {
            Column(Modifier.safeDrawingPadding().padding(horizontal = 14.dp)) {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                val left: @Composable () -> Unit = {
                    if (!setupOk(s)) PermissionsCard(s, ctx)
                    LookCard(s, p, fancy = true, onClassic = onDismiss)
                    SectionCard("Schedule") {
                        SwitchRow("Turn on automatically", "Uses your bedtime and wake-up times", s.scheduleEnabled) {
                            p.scheduleEnabled = it; p.clearOverride(); changed()
                        }
                        TextButton(onClick = { Setup.requestAddTile(ctx) }) { Text("Add to Quick Settings") }
                    }
                    SoundCard(s, p, ctx, changed)
                }
                val right: @Composable () -> Unit = {
                    HideCard(s, p, ctx, changed, onApps)
                    NoticeCard(s, p, changed)
                    if (setupOk(s)) PermissionsCard(s, ctx)
                    AboutCard(onChecked = onChecked)
                    Spacer(Modifier.height(24.dp))
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val gap = Arrangement.spacedBy(12.dp)
                    if (maxWidth >= 600.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = gap) { left() }
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = gap) { right() }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = gap) {
                            left(); right()
                        }
                    }
                }
            }
        }
    }
}
