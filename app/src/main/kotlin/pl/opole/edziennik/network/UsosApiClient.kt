package pl.opole.edziennik.network

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.opole.edziennik.oauth.OAuth1Signer
import java.io.IOException

data class UsosResponse(val isSuccessful: Boolean, val body: String)

/**
 * Cienki klient USOS API — podpisuje każde zapytanie OAuth1 (HMAC-SHA1) i
 * woła `{baseUrl}/services/{method}`, dokładnie jak `usos_url()` w
 * aplikacji webowej.
 *
 * UWAGA: błędy sieciowe (brak internetu, brak DNS...) są tu łapane i
 * zamieniane na zwykłe `UsosResponse(isSuccessful = false, ...)`, zamiast
 * pozwolić `IOException` z OkHttp lecieć dalej. Dzięki temu każde miejsce
 * wywołujące `get()`/`post()` może polegać na jednym, prostym sprawdzeniu
 * `resp.isSuccessful` — nie trzeba pamiętać o try/catch przy każdym nowym
 * wywołaniu (i o tym łatwo zapomnieć, co spowodowało realny crash appki po
 * kliknięciu w "Oceny" bez internetu — `fetchEctsPoints()` nie miało
 * własnego try/catch, a wyjątek z tego miejsca lądował bezpośrednio w
 * korutynie ViewModelu, zabijając cały proces).
 *
 * Klucz/sekret konsumenta USOS NIE są wbudowane na stałe (ani w kodzie, ani
 * w BuildConfig) — użytkownik wpisuje je sam przy pierwszym uruchomieniu
 * (patrz `SetupScreen`/`CredentialsStore`), dzięki czemu zbudowany plik APK
 * można bezpiecznie publikować (np. automatycznie w CI) bez ujawniania
 * sekretu każdemu, kto go zdekompiluje.
 */
class UsosApiClient(val baseUrl: String) {
    private val client = OkHttpClient()

    var accessToken: String? = null
    var accessTokenSecret: String? = null
    private var consumerKey: String = ""
    private var consumerSecret: String = ""

    fun setConsumerCredentials(key: String, secret: String) {
        consumerKey = key
        consumerSecret = secret
    }

    fun hasConsumerCredentials(): Boolean = consumerKey.isNotEmpty() && consumerSecret.isNotEmpty()

    fun get(method: String, params: Map<String, String> = emptyMap()): UsosResponse {
        val url = "$baseUrl/services/$method"
        val signed = sign("GET", url, params)
        val allParams = params + signed

        val httpUrl = url.toHttpUrl().newBuilder().apply {
            allParams.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        val request = Request.Builder().url(httpUrl).get().build()
        return executeSafely(request)
    }

    fun post(method: String, params: Map<String, String> = emptyMap()): UsosResponse {
        val url = "$baseUrl/services/$method"
        val signed = sign("POST", url, params)
        val allParams = params + signed

        val formBody = FormBody.Builder().apply {
            allParams.forEach { (k, v) -> add(k, v) }
        }.build()

        val request = Request.Builder().url(url).post(formBody).build()
        return executeSafely(request)
    }

    private fun executeSafely(request: Request): UsosResponse =
        try {
            client.newCall(request).execute().use { response ->
                UsosResponse(response.isSuccessful, response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            UsosResponse(isSuccessful = false, body = e.message ?: "Błąd sieci.")
        }

    private fun sign(method: String, url: String, params: Map<String, String>): Map<String, String> =
        OAuth1Signer.sign(
            method = method,
            url = url,
            params = params,
            consumerKey = consumerKey,
            consumerSecret = consumerSecret,
            token = accessToken,
            tokenSecret = accessTokenSecret,
        )
}
