package app.bedtimemode.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import app.bedtimemode.BuildConfig
import app.bedtimemode.Release
import app.bedtimemode.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Download + install state for one release; shared by the card and the pop-up. */
private class UpdateState {
    var progress by mutableStateOf<Float?>(null)
    var error by mutableStateOf<String?>(null)
    var needsPermission by mutableStateOf(false)
}

@Composable
private fun rememberUpdateState(): UpdateState {
    val ctx = LocalContext.current
    val st = remember { UpdateState() }
    LifecycleResumeEffect(Unit) {
        st.needsPermission = st.needsPermission && !Updater.canInstall(ctx)
        onPauseOrDispose { }
    }
    return st
}

/** Version line, notes, progress and errors. */
@Composable
private fun UpdateBody(r: Release, st: UpdateState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("You have ${Updater.currentVersion}. What's new:", style = MaterialTheme.typography.bodySmall)
        Text(r.notes.ifBlank { "Bug fixes and improvements." }, style = MaterialTheme.typography.bodyMedium)
        st.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
        st.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (st.needsPermission) {
            Text(
                "Allow \"Install unknown apps\" for Bedtime Mode Reworked, then come back and tap Update again.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun UpdateButton(r: Release, st: UpdateState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Button(enabled = st.progress == null, onClick = {
        if (!Updater.canInstall(ctx)) {
            st.needsPermission = true
            Updater.openInstallPermission(ctx)
            return@Button
        }
        st.error = null
        st.progress = 0f
        scope.launch {
            runCatching {
                val apk = withContext(Dispatchers.IO) { Updater.download(ctx, r) { p -> st.progress = p } }
                withContext(Dispatchers.IO) { Updater.install(ctx, apk) }
            }.onFailure { st.error = "Update failed: ${it.message}" }
            st.progress = null
        }
    }) { Text("Update now") }
}

private fun openUrl(ctx: android.content.Context, url: String) =
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

/** Shows "Update available" with the release notes at the top of the main screen. */
@Composable
fun UpdateCard(forceCheck: Boolean, onShown: () -> Unit, refreshKey: Int = 0) {
    val ctx = LocalContext.current
    var release by remember(refreshKey) { mutableStateOf<Release?>(Updater.cached(ctx)) }
    val st = rememberUpdateState()

    LaunchedEffect(forceCheck) {
        // Check on open (cheap: one small API call) so users see updates without waiting.
        release = withContext(Dispatchers.IO) { Updater.checkNow(ctx) } ?: Updater.cached(ctx)
        if (forceCheck) onShown()
    }

    val r = release ?: return
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Update available: ${r.version}", style = MaterialTheme.typography.titleMedium)
            UpdateBody(r, st)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpdateButton(r, st)
                TextButton(onClick = { openUrl(ctx, r.pageUrl) }) { Text("Details") }
            }
        }
    }
}

private sealed interface CheckResult {
    data object Checking : CheckResult
    data object UpToDate : CheckResult
    data object Offline : CheckResult
    data class Available(val r: Release) : CheckResult
}

/** Settings section: version + "Check for updates", which answers in a pop-up. */
@Composable
fun AboutCard(onChecked: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<CheckResult?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text("Version ${Updater.currentVersion}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Updates are checked automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = result != CheckResult.Checking, onClick = {
                    result = CheckResult.Checking
                    scope.launch {
                        val latest = withContext(Dispatchers.IO) { Updater.fetchLatest() }
                        val newer = latest?.let { withContext(Dispatchers.IO) { Updater.remember(ctx, it) } }
                        result = when {
                            latest == null -> CheckResult.Offline
                            newer != null -> CheckResult.Available(newer)
                            else -> CheckResult.UpToDate
                        }
                        onChecked()
                    }
                }) { Text(if (result == CheckResult.Checking) "Checking…" else "Check for updates") }
                TextButton(onClick = { openUrl(ctx, "https://github.com/${BuildConfig.GITHUB_REPO}") }) { Text("GitHub page") }
            }
        }
    }

    val dismiss = { result = null }
    when (val r = result) {
        is CheckResult.Available -> {
            val st = rememberUpdateState()
            AlertDialog(
                onDismissRequest = { if (st.progress == null) dismiss() },
                title = { Text("Update available: ${r.r.version}") },
                text = {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { UpdateBody(r.r, st) }
                },
                confirmButton = { UpdateButton(r.r, st) },
                dismissButton = { TextButton(enabled = st.progress == null, onClick = dismiss) { Text("Later") } },
            )
        }
        CheckResult.UpToDate -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("You're up to date") },
            text = { Text("Version ${Updater.currentVersion} is the latest.") },
            confirmButton = { TextButton(onClick = dismiss) { Text("OK") } },
        )
        CheckResult.Offline -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("Couldn't check") },
            text = { Text("Couldn't reach GitHub. Check your internet connection and try again.") },
            confirmButton = { TextButton(onClick = dismiss) { Text("OK") } },
        )
        else -> {}
    }
}
