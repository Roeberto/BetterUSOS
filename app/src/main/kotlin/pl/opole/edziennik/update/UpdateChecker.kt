package pl.opole.edziennik.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import pl.opole.edziennik.Config

data class UpdateInfo(val versionCode: Int, val downloadUrl: String)

/**
 * Sprawdza, czy na GitHubie jest nowszy build niż ten zainstalowany —
 * publiczne, nieautoryzowane GitHub API (ten sam release "latest-build",
 * który pobieraliśmy ręcznie przez curl przy weryfikacji CI), więc nie idzie
 * przez serwer podpisujący ani nie potrzebuje żadnych danych logowania.
 *
 * `versionCode` nie jest osobnym polem release'u w GitHub API — CI dopisuje
 * go jako zwykłą linię tekstu do treści ("body") releasu (patrz
 * `build-apk.yml`), stąd proste wyciąganie liczby wyrażeniem regularnym
 * zamiast parsowania dedykowanego pola.
 */
class UpdateChecker(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(Config.RELEASE_INFO_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val releaseBody = JSONObject(body).optString("body", "")
                val versionCode = VERSION_CODE_REGEX.find(releaseBody)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: return@withContext null
                UpdateInfo(versionCode, Config.RELEASE_APK_URL)
            }
        } catch (e: Exception) {
            // Brak internetu, GitHub niedostępny itp. — po prostu nie
            // zgłaszamy aktualizacji, bez przerywania działania appki.
            null
        }
    }

    private companion object {
        val VERSION_CODE_REGEX = Regex("""versionCode:\s*(\d+)""")
    }
}
