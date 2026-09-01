package pl.opole.edziennik.data

import java.io.File
import java.security.MessageDigest

/**
 * Trwały cache surowych odpowiedzi JSON z USOS API — po jednym pliku na
 * parę (metoda, parametry), zapisany w plikach aplikacji (przeżywa
 * zamknięcie appki, nie tylko nawigację między ekranami).
 *
 * Celowo cache'ujemy surowy tekst odpowiedzi, nie sparsowane dane: dzięki
 * temu cała logika parsowania (`flattenTermsGrades`, `formatPerson`,
 * `groupByDay`...) działa identycznie niezależnie od tego, czy JSON przyszedł
 * właśnie z sieci, czy z dysku — nie trzeba nic dodatkowo serializować.
 */
class DiskCache(private val dir: File) {
    init {
        if (!dir.exists()) dir.mkdirs()
    }

    fun read(key: String): String? {
        val file = File(dir, fileName(key))
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }

    fun write(key: String, value: String) {
        runCatching { File(dir, fileName(key)).writeText(value) }
    }

    /** Zamienia klucz (np. "grades/terms2?term_ids=...") na bezpieczną,
     * stałej długości nazwę pliku przez skrót SHA-256 — nie trzeba się
     * martwić o znaki niedozwolone w nazwach plików ani o to, że USOS
     * potrafi zwrócić bardzo długie listy ID w parametrach zapytania. */
    private fun fileName(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) } + ".json"
    }
}

/** Buduje deterministyczny klucz cache z metody i parametrów zapytania —
 * parametry są sortowane, żeby ta sama treściowo prośba zawsze trafiała w
 * ten sam plik, niezależnie od kolejności wstawiania do mapy. */
fun cacheKeyFor(method: String, params: Map<String, String>): String {
    val sortedParams = params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
    return "$method?$sortedParams"
}
