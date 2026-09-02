package pl.opole.edziennik.network

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class UsosResponse(val isSuccessful: Boolean, val body: String)

/**
 * Cienki klient USOS API. `baseUrl` wskazuje na serwer podpisujący (Worker
 * Cloudflare, patrz `proxy/`) zamiast bezpośrednio na usosapps.po.edu.pl —
 * ten klient NIE zna klucza/sekretu konsumenta USOS w ogóle i niczego nie
 * podpisuje; dorzuca tylko token zalogowanego użytkownika jako zwykłe
 * parametry (`oauth_token`/`oauth_token_secret`), a podpis OAuth1 dolicza
 * dopiero Worker, trzymający sekret wyłącznie po swojej stronie
 * (`wrangler secret put`). Dzięki temu żaden zbudowany APK — publiczny czy
 * lokalny — nie ujawnia sekretu nawet po zdekompilowaniu.
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
 */
class UsosApiClient(val baseUrl: String) {
    private val client = OkHttpClient()

    var accessToken: String? = null
    var accessTokenSecret: String? = null

    fun get(method: String, params: Map<String, String> = emptyMap()): UsosResponse {
        val allParams = withTokenParams(params)
        val httpUrl = "$baseUrl/services/$method".toHttpUrl().newBuilder().apply {
            allParams.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        val request = Request.Builder().url(httpUrl).get().build()
        return executeSafely(request)
    }

    fun post(method: String, params: Map<String, String> = emptyMap()): UsosResponse {
        val allParams = withTokenParams(params)
        val formBody = FormBody.Builder().apply {
            allParams.forEach { (k, v) -> add(k, v) }
        }.build()

        val request = Request.Builder().url("$baseUrl/services/$method").post(formBody).build()
        return executeSafely(request)
    }

    /** Dokłada token zalogowanego użytkownika (albo request token w trakcie
     * logowania) jako zwykłe parametry — Worker po drugiej stronie
     * używa `oauth_token_secret` tylko do wyliczenia podpisu i nigdy nie
     * przekazuje go dalej do USOS. */
    private fun withTokenParams(params: Map<String, String>): Map<String, String> {
        val extra = mutableMapOf<String, String>()
        accessToken?.let { extra["oauth_token"] = it }
        accessTokenSecret?.let { extra["oauth_token_secret"] = it }
        return params + extra
    }

    private fun executeSafely(request: Request): UsosResponse =
        try {
            client.newCall(request).execute().use { response ->
                UsosResponse(response.isSuccessful, response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            UsosResponse(isSuccessful = false, body = e.message ?: "Błąd sieci.")
        }
}
