package app.bedtimemode.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.bedtimemode.Bedtime
import app.bedtimemode.Format
import app.bedtimemode.Prefs
import app.bedtimemode.Setup

private enum class Step { WELCOME, LISTENER, DND, NOTIFICATIONS, BATTERY, BUILT_IN, TILE, SCHEDULE }

/** First-run wizard: one permission per screen, each with plain instructions. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var setup by remember { mutableStateOf(Setup.state(ctx)) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        setup = Setup.state(ctx); tick++
        onPauseOrDispose { }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        setup = Setup.state(ctx)
    }

    val steps = Step.entries
    val step = steps[stepIndex]
    val next: () -> Unit = {
        if (stepIndex < steps.lastIndex) {
            stepIndex++
        } else {
            prefs.onboardingDone = true
            Bedtime.sync(ctx, "onboarding-done")
            onDone()
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LinearProgressIndicator(
                    progress = { (stepIndex + 1f) / steps.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Step ${stepIndex + 1} of ${steps.size}", style = MaterialTheme.typography.labelLarge)

                when (step) {
                    Step.WELCOME -> StepBody(
                        title = "Welcome to Bedtime Mode Reworked",
                        text = "At night this app hides every notification from your notification shade and " +
                            "keeps the phone silent. In the morning, everything comes back to the shade so " +
                            "you can review it.\n\nIt needs a few permissions. This takes about a minute, " +
                            "one step at a time.",
                    ) {
                        Button(onClick = next, Modifier.fillMaxWidth()) { Text("Let's start") }
                    }

                    Step.LISTENER -> StepBody(
                        title = "1. Notification access",
                        text = "This lets the app hide notifications at bedtime and bring them back in the " +
                            "morning. Nothing is read, stored or sent anywhere.\n\n" +
                            "Tap the button, then turn on \"Bedtime Mode Reworked\".",
                        done = setup.listener,
                    ) {
                        GrantOrNext(setup.listener, "Open notification access", { Setup.openListenerSettings(ctx) }, next)
                        if (!setup.listener) {
                            HintCard(
                                "Switch greyed out or \"Restricted setting\"?",
                                "Android blocks this for apps installed from a downloaded file. To unlock it:\n" +
                                    "1. Tap \"Open App info\" below\n" +
                                    "2. Tap the ⋮ menu (top-right)\n" +
                                    "3. Tap \"Allow restricted settings\" and confirm\n" +
                                    "4. Come back here and tap \"Open notification access\" again",
                            ) { OutlinedButton(onClick = { Setup.openAppDetails(ctx) }) { Text("Open App info") } }
                        }
                    }

                    Step.DND -> StepBody(
                        title = "2. Do Not Disturb access",
                        text = "Used to silence sound, vibration and pop-ups at bedtime. Alarms will still " +
                            "ring.\n\nTap the button, then allow \"Bedtime Mode Reworked\".",
                        done = setup.dnd,
                    ) { GrantOrNext(setup.dnd, "Open Do Not Disturb access", { Setup.openDndSettings(ctx) }, next) }

                    Step.NOTIFICATIONS -> StepBody(
                        title = "3. Notifications",
                        text = "Lets the app show a small \"Bedtime is on\" notification while it's active, and " +
                            "tell you when an app update is available.",
                        done = setup.postNotifications,
                    ) {
                        GrantOrNext(setup.postNotifications, "Allow notifications", {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else Setup.openAppNotificationSettings(ctx)
                        }, next)
                    }

                    Step.BATTERY -> StepBody(
                        title = "4. Keep it running at night",
                        text = "Phones (especially OPPO, OnePlus, Xiaomi, Samsung) close background apps to " +
                            "save battery. That would stop Bedtime Mode Reworked in the middle of the night.",
                        done = setup.battery,
                    ) {
                        if (!setup.battery) {
                            FilledTonalButton(onClick = { Setup.requestBatteryExemption(ctx) }, Modifier.fillMaxWidth()) {
                                Text("Allow unrestricted battery")
                            }
                        }
                        HintCard(
                            "On ColorOS / OxygenOS also:",
                            "• App info → Battery usage → select \"Allow background activity\"\n" +
                                "• If your phone has an \"Auto launch\" setting, turn it on for Bedtime Mode Reworked\n" +
                                "• In Recent apps, lock the Bedtime Mode Reworked card so it isn't cleared",
                        ) { OutlinedButton(onClick = { Setup.openAppDetails(ctx) }) { Text("Open App info") } }
                        Button(onClick = next, Modifier.fillMaxWidth()) { Text(if (setup.battery) "Next" else "Skip for now") }
                    }

                    Step.BUILT_IN -> StepBody(
                        title = "5. Turn off the phone's own Bedtime",
                        text = "If your phone's built-in Bedtime / Do Not Disturb schedules are on, they can " +
                            "fight with this app. Turn off their schedules:\n\n" +
                            "• Digital Wellbeing → Bedtime mode → Bedtime routine → None\n" +
                            "• Do Not Disturb → Schedules → turn off old schedules\n\n" +
                            "Keep the \"Bedtime Mode Reworked\" schedule entry ON — that one is this app.",
                    ) {
                        OutlinedButton(onClick = { Setup.openZenSettings(ctx) }, Modifier.fillMaxWidth()) {
                            Text("Open Do Not Disturb settings")
                        }
                        Button(onClick = next, Modifier.fillMaxWidth()) { Text("Next") }
                    }

                    Step.TILE -> StepBody(
                        title = "6. Quick Settings button",
                        text = "Add a \"Bedtime\" button to the Quick Settings panel (swipe down from the top-right) " +
                            "to turn it on or off anytime, regardless of the schedule.",
                    ) {
                        FilledTonalButton(onClick = { Setup.requestAddTile(ctx) }, Modifier.fillMaxWidth()) {
                            Text("Add Bedtime button")
                        }
                        Button(onClick = next, Modifier.fillMaxWidth()) { Text("Next") }
                    }

                    Step.SCHEDULE -> {
                        val key = tick // re-read after time picker
                        val start = remember(key) { prefs.startMinute }
                        val end = remember(key) { prefs.endMinute }
                        StepBody(
                            title = "7. Your bedtime",
                            text = "Bedtime turns on and off automatically every day. You can change days and " +
                                "more later.",
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = {
                                    TimePickerDialog(ctx, { _, h, m -> prefs.startMinute = h * 60 + m; tick++ }, start / 60, start % 60, true).show()
                                }, Modifier.weight(1f)) { Text("Starts ${Format.minute(start)}") }
                                OutlinedButton(onClick = {
                                    TimePickerDialog(ctx, { _, h, m -> prefs.endMinute = h * 60 + m; tick++ }, end / 60, end % 60, true).show()
                                }, Modifier.weight(1f)) { Text("Ends ${Format.minute(end)}") }
                            }
                            Button(onClick = next, Modifier.fillMaxWidth()) { Text("Finish") }
                        }
                    }
                }

                if (stepIndex > 0) {
                    TextButton(onClick = { stepIndex-- }) { Text("Back") }
                }
            }
        }
    }
}

@Composable
private fun StepBody(title: String, text: String, done: Boolean? = null, actions: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    if (done == true) {
        Text("✓ Done", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
    }
    Text(text, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(4.dp))
    actions()
}

@Composable
private fun GrantOrNext(done: Boolean, grantLabel: String, onGrant: () -> Unit, onNext: () -> Unit) {
    if (done) {
        Button(onClick = onNext, Modifier.fillMaxWidth()) { Text("Next") }
    } else {
        Button(onClick = onGrant, Modifier.fillMaxWidth()) { Text(grantLabel) }
        TextButton(onClick = onNext) { Text("Skip (the app won't fully work)") }
    }
}

@Composable
private fun HintCard(title: String, body: String, action: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            action()
        }
    }
}
