package pl.opole.edziennik.data

import org.json.JSONObject

/**
 * Wyciąga polską wersję z pola wielojęzycznego USOS API (LangDict), albo
 * zwraca wartość jak jest, jeśli to już zwykły string. Odpowiednik
 * `pl_text()` z aplikacji webowej.
 */
fun plText(value: Any?): String {
    if (value == null || value == JSONObject.NULL) return ""
    if (value is JSONObject) {
        val pl = value.optString("pl", "")
        if (pl.isNotEmpty()) return pl
        val en = value.optString("en", "")
        if (en.isNotEmpty()) return en
        val keys = value.keys()
        if (keys.hasNext()) return value.optString(keys.next(), "")
        return ""
    }
    return value.toString()
}

fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null

/** Wycina godzinę i minutę (HH:MM) ze znacznika czasu USOS API
 * ("RRRR-MM-DD HH:MM:SS"). Odpowiednik filtra `hm` z aplikacji webowej. */
fun hm(value: String?): String =
    if (value != null && value.length >= 16) value.substring(11, 16) else value.orEmpty()
