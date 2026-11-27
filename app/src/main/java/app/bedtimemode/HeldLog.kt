package app.bedtimemode

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.io.File

data class HeldItem(val time: Long, val pkg: String, val title: String, val text: String)

/**
 * The app's own copy of every notification it hides (on-device only), so nothing can be missed
 * even if the system loses a notification. Kept for 7 days / 500 entries.
 */
object HeldLog {
    private const val MAX = 500
    private const val MAX_AGE = 7L * 24 * 60 * 60_000

    private fun file(ctx: Context) = File(ctx.filesDir, "held_log.jsonl")

    @Synchronized
    fun add(ctx: Context, sbn: StatusBarNotification) {
        val ex = sbn.notification.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        // Group summaries usually have no content of their own; their children are logged.
        if (title.isBlank() && text.isBlank()) return
        val line = JSONObject()
            .put("t", System.currentTimeMillis()).put("p", sbn.packageName)
            .put("ti", title.take(200)).put("tx", text.take(1000))
            .toString()
        runCatching { file(ctx).appendText(line + "\n") }
    }

    @Synchronized
    fun read(ctx: Context): List<HeldItem> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        val cutoff = System.currentTimeMillis() - MAX_AGE
        val items = f.readLines().mapNotNull { l ->
            runCatching {
                val o = JSONObject(l)
                HeldItem(o.getLong("t"), o.getString("p"), o.optString("ti"), o.optString("tx"))
            }.getOrNull()
        }.filter { it.time >= cutoff }
        val kept = items.takeLast(MAX)
        if (kept.size != items.size || items.size > MAX) {
            runCatching { f.writeText(kept.joinToString("") { it.toJson() + "\n" }) }
        }
        return kept.asReversed()
    }

    private fun HeldItem.toJson() =
        JSONObject().put("t", time).put("p", pkg).put("ti", title).put("tx", text).toString()
}
