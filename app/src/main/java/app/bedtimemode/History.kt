package app.bedtimemode

import org.json.JSONArray
import org.json.JSONObject

data class SessionRecord(
    val start: Long,
    val end: Long,
    val held: Int,
    val returned: Int,
    /** package -> number of notifications held. */
    val apps: Map<String, Int>,
)

/** Per-session log so the user can see proof of what was hidden and what came back. */
object History {
    private const val MAX = 30

    /** Notification key format: "user|pkg|id|tag|uid". */
    fun pkgOf(key: String): String = key.split('|').getOrNull(1) ?: key

    fun appCounts(keys: Set<String>): Map<String, Int> =
        keys.groupingBy(::pkgOf).eachCount().toList().sortedByDescending { it.second }.toMap()

    fun read(p: Prefs): List<SessionRecord> = runCatching {
        val arr = JSONArray(p.historyJson)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val appsObj = o.optJSONObject("apps") ?: JSONObject()
            SessionRecord(
                start = o.getLong("start"),
                end = o.getLong("end"),
                held = o.getInt("held"),
                returned = o.optInt("returned"),
                apps = appsObj.keys().asSequence().associateWith { appsObj.getInt(it) },
            )
        }
    }.getOrDefault(emptyList())

    private fun write(p: Prefs, list: List<SessionRecord>) {
        val arr = JSONArray()
        list.take(MAX).forEach { r ->
            arr.put(
                JSONObject()
                    .put("start", r.start)
                    .put("end", r.end)
                    .put("held", r.held)
                    .put("returned", r.returned)
                    .put("apps", JSONObject(r.apps)),
            )
        }
        p.historyJson = arr.toString()
    }

    /** Called when a session ends. */
    fun finishSession(p: Prefs, now: Long) {
        val keys = p.heldKeys
        val record = SessionRecord(p.sessionStartedAt, now, keys.size, 0, appCounts(keys))
        write(p, listOf(record) + read(p))
        // Keep anything from earlier sessions that hasn't come back yet, so it's released too.
        p.pendingReturnKeys = (p.pendingReturnKeys + keys).take(500).toSet()
        p.heldKeys = emptySet()
    }

    /** Called when a notification is posted outside bedtime: was it one of ours coming back? */
    fun noteReturned(p: Prefs, key: String) {
        val pending = p.pendingReturnKeys
        if (key !in pending) return
        p.pendingReturnKeys = pending - key
        val list = read(p)
        if (list.isEmpty()) return
        write(p, listOf(list[0].copy(returned = list[0].returned + 1)) + list.drop(1))
    }
}
