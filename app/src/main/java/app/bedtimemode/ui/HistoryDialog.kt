package app.bedtimemode.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.bedtimemode.Format
import app.bedtimemode.SessionRecord

/** One finished session: times, counts, apps. Tap for the per-app breakdown. */
@Composable
internal fun SessionRow(ctx: Context, r: SessionRecord, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text("${Format.dayTime(r.start)} → ${Format.dayTime(r.end)}", style = MaterialTheme.typography.bodyLarge)
        val back = if (r.held == 0) "" else " · ${r.returned} of ${r.held} back in shade"
        Text("${r.held} hidden$back", style = MaterialTheme.typography.bodyMedium)
        if (r.apps.isNotEmpty()) {
            Text(
                appsSummary(ctx, r.apps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/** Full list of finished sessions (up to the 30 that History keeps). */
@Composable
fun HistoryDialog(history: List<SessionRecord>, onSession: (SessionRecord) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("All sessions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    "Your last ${history.size} bedtime sessions, newest first. Tap one to see which apps were hidden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(history) { r ->
                        SessionRow(ctx, r, onClick = { onSession(r) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Per-app breakdown of one session. Tap an app to open it. */
@Composable
fun SessionDetailDialog(r: SessionRecord, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("${Format.dayTime(r.start)} → ${Format.dayTime(r.end)}") },
        text = {
            Column {
                Text(
                    "${r.held} hidden · ${r.returned} back in shade",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (r.apps.isEmpty()) Text("Nothing was hidden in this session.")
                LazyColumn {
                    items(r.apps.entries.sortedByDescending { it.value }.toList()) { (pkg, n) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
                                    ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }.padding(vertical = 10.dp),
                        ) {
                            Text(appLabel(ctx, pkg), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("$n", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        },
    )
}
