package pl.opole.edziennik.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Pojedyncze wykryte zdarzenie (zmiana w planie albo nowa ocena),
 * pokazywane na ekranie "Powiadomienia". */
data class NotificationEvent(
    val id: String,
    val timestamp: Long,
    val type: String, // "schedule" | "grade"
    val message: String,
)

/**
 * Trwała historia zdarzeń wykrytych przez `SyncWorker` — najnowsze na
 * początku, ograniczona do ostatnich 100 wpisów, żeby plik nie rósł bez
 * końca.
 */
class NotificationHistoryStore(private val dir: File) {
    init {
        if (!dir.exists()) dir.mkdirs()
    }

    private val file get() = File(dir, "notifications.json")

    fun readAll(): List<NotificationEvent> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                NotificationEvent(
                    id = o.optString("id"),
                    timestamp = o.optLong("timestamp"),
                    type = o.optString("type"),
                    message = o.optString("message"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Dokłada nowe zdarzenia na początek listy (najnowsze najpierw). */
    fun addAll(events: List<NotificationEvent>) {
        if (events.isEmpty()) return
        val merged = (events + readAll()).take(MAX_ENTRIES)
        runCatching {
            val arr = JSONArray()
            merged.forEach { e ->
                arr.put(
                    JSONObject()
                        .put("id", e.id)
                        .put("timestamp", e.timestamp)
                        .put("type", e.type)
                        .put("message", e.message),
                )
            }
            file.writeText(arr.toString())
        }
    }

    companion object {
        private const val MAX_ENTRIES = 100
    }
}
