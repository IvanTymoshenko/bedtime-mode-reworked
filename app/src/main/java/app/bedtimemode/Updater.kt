package app.bedtimemode

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val pageUrl: String,
)

/**
 * Self-update from GitHub Releases: checks the latest release, shows its notes, downloads the
 * APK and installs it through PackageInstaller (the user confirms one system dialog).
 * Every release must be signed with the same key, or Android refuses the update.
 */
object Updater {
    private const val API = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"
    private const val CHECK_INTERVAL = 12 * 60 * 60_000L
    private const val CHANNEL = "updates"
    private const val NOTIF_ID = 2
    const val EXTRA_SHOW_UPDATE = "show_update"

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** "1.2.10" > "1.2.9"; tolerates a leading "v" and suffixes like "-beta". */
    fun isNewer(remote: String, local: String = currentVersion): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Blocking network call — call off the main thread. */
    fun fetchLatest(): Release? = runCatching {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "BedtimeModeReworked-Android")
        }
        conn.inputStream.bufferedReader().use { it.readText() }.let { body ->
            val o = JSONObject(body)
            val assets = o.getJSONArray("assets")
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    apk = a.getString("browser_download_url"); break
                }
            }
            Release(
                version = o.getString("tag_name").removePrefix("v"),
                notes = o.optString("body").trim(),
                apkUrl = apk ?: return@let null,
                pageUrl = o.optString("html_url"),
            )
        }
    }.onFailure { Log.w(TAG, "update check failed: ${it.message}") }.getOrNull()

    /** Remembers the found release so the UI can show it without another network call. */
    fun checkNow(ctx: Context): Release? {
        Prefs(ctx).lastUpdateCheck = System.currentTimeMillis()
        return fetchLatest()?.let { remember(ctx, it) }
    }

    /** Store [r] as the available update if it's newer; returns it, or null when up to date. */
    fun remember(ctx: Context, r: Release): Release? {
        val p = Prefs(ctx)
        if (isNewer(r.version)) {
            p.availableUpdateJson = JSONObject()
                .put("version", r.version).put("notes", r.notes)
                .put("apk", r.apkUrl).put("page", r.pageUrl).toString()
            return r
        }
        p.availableUpdateJson = null
        return null
    }

    fun cached(ctx: Context): Release? {
        val s = Prefs(ctx).availableUpdateJson ?: return null
        return runCatching {
            val o = JSONObject(s)
            Release(o.getString("version"), o.getString("notes"), o.getString("apk"), o.getString("page"))
        }.getOrNull()?.takeIf { isNewer(it.version) }
    }

    /** Background check at most every 12h; piggybacks on engine syncs. Notifies once per version. */
    fun maybeCheckInBackground(ctx: Context) {
        val app = ctx.applicationContext
        val p = Prefs(app)
        if (System.currentTimeMillis() - p.lastUpdateCheck < CHECK_INTERVAL) return
        p.lastUpdateCheck = System.currentTimeMillis()
        thread(name = "update-check", isDaemon = true) {
            if (checkNow(app) != null) notifyIfPending(app)
        }
    }

    /**
     * Post the "update available" notification once per version — but never during bedtime
     * (it would be hidden); the next sync after bedtime ends posts it instead.
     */
    fun notifyIfPending(ctx: Context) {
        val p = Prefs(ctx)
        val r = cached(ctx) ?: return
        if (p.notifiedVersion == r.version) return
        if (Bedtime.evaluate(p).active) return
        p.notifiedVersion = r.version
        notifyUpdate(ctx, r)
    }

    private fun notifyUpdate(ctx: Context, r: Release) {
        if (!StatusNotification.canPost(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setBypassDnd(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 3,
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_SHOW_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_bedtime)
            .setContentTitle("Bedtime Mode Reworked ${r.version} is available")
            .setContentText(r.notes.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart('-', '*', ' ') ?: "Tap to see what's new")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, n)
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Blocking download with progress (0..1). Returns the APK file. */
    fun download(ctx: Context, r: Release, onProgress: (Float) -> Unit): File {
        val out = File(ctx.cacheDir, "update.apk")
        val conn = (URL(r.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "BedtimeModeReworked-Android")
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        return out
    }

    /** Hands the APK to the system installer; the user sees one "Update?" confirmation. */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val status = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, InstallResultReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(status.intentSender)
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "update installed")
            else -> Log.w(TAG, "update install failed: $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}
