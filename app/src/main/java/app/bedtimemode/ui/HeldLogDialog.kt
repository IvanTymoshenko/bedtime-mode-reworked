package app.bedtimemode.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.bedtimemode.Format
import app.bedtimemode.HeldLog

/** Everything Bedtime Mode hid in the last 7 days. Tap an entry to open its app. */
@Composable
fun HeldLogDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val items = remember { HeldLog.read(ctx) }
    val labels = remember { mutableMapOf<String, String>() }
    fun label(pkg: String) = labels.getOrPut(pkg) {
        if (pkg == "com.android.shell") "Test (adb)" else runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hidden notifications", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    "Everything Bedtime Mode hid in the last 7 days, newest first. Tap one to open its app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (items.isEmpty()) {
                    Text("Nothing hidden yet.", Modifier.padding(top = 24.dp))
                }
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(items) { it ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                ctx.packageManager.getLaunchIntentForPackage(it.pkg)?.let { i ->
                                    ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }.padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row {
                                Text(label(it.pkg), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                Text(Format.dayTime(it.time), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (it.title.isNotBlank()) Text(it.title, style = MaterialTheme.typography.bodyLarge)
                            if (it.text.isNotBlank()) {
                                Text(it.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
