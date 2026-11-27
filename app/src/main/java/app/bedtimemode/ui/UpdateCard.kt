package app.bedtimemode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.bedtimemode.Release
import app.bedtimemode.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows "Update available" with the release notes; downloads and installs in-app. */
@Composable
fun UpdateCard(forceCheck: Boolean, onShown: () -> Unit, refreshKey: Int = 0) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember(refreshKey) { mutableStateOf<Release?>(Updater.cached(ctx)) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsPermission by remember { mutableStateOf(false) }

    LaunchedEffect(forceCheck) {
        // Check on open (cheap: one small API call) so users see updates without waiting.
        release = withContext(Dispatchers.IO) { Updater.checkNow(ctx) } ?: Updater.cached(ctx)
        if (forceCheck) onShown()
    }
    LifecycleResumeEffect(Unit) {
        needsPermission = needsPermission && !Updater.canInstall(ctx)
        onPauseOrDispose { }
    }

    val r = release ?: return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Update available: ${r.version}", style = MaterialTheme.typography.titleMedium)
            Text("You have ${Updater.currentVersion}. What's new:", style = MaterialTheme.typography.bodySmall)
            Text(r.notes.ifBlank { "Bug fixes and improvements." }, style = MaterialTheme.typography.bodyMedium)
            progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (needsPermission) {
                Text(
                    "Allow \"Install unknown apps\" for Bedtime Mode Reworked, then come back and tap Update again.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = progress == null, onClick = {
                    if (!Updater.canInstall(ctx)) {
                        needsPermission = true
                        Updater.openInstallPermission(ctx)
                        return@Button
                    }
                    error = null
                    progress = 0f
                    scope.launch {
                        runCatching {
                            val apk = withContext(Dispatchers.IO) { Updater.download(ctx, r) { p -> progress = p } }
                            withContext(Dispatchers.IO) { Updater.install(ctx, apk) }
                        }.onFailure { error = "Update failed: ${it.message}" }
                        progress = null
                    }
                }) { Text("Update now") }
                TextButton(onClick = {
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(r.pageUrl))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Details") }
            }
        }
    }
}

/** Settings section: version + manual "Check for updates". */
@Composable
fun AboutCard(onChecked: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About & updates", style = MaterialTheme.typography.titleMedium)
            Text("Version ${Updater.currentVersion}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "The app checks for updates automatically and notifies you when a new version is out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilledTonalButton(enabled = !checking, onClick = {
                    checking = true
                    status = "Checking…"
                    scope.launch {
                        val latest = withContext(Dispatchers.IO) { Updater.fetchLatest() }
                        val newer = latest?.let { withContext(Dispatchers.IO) { Updater.remember(ctx, it) } }
                        status = when {
                            latest == null -> "Couldn't reach GitHub. Check your internet connection."
                            newer != null -> "Version ${newer.version} is available — see the top of the screen."
                            else -> "You're up to date (${Updater.currentVersion})."
                        }
                        checking = false
                        onChecked()
                    }
                }) { Text("Check for updates") }
                TextButton(onClick = {
                    ctx.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/${app.bedtimemode.BuildConfig.GITHUB_REPO}"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("GitHub page") }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
