package pl.opole.edziennik.network

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.opole.edziennik.oauth.OAuth1Signer

data class UsosResponse(val isSuccessful: Boolean, val body: String)

/**
 * Cienki klient USOS API — podpisuje każde zapytanie OAuth1 (HMAC-SHA1) i
 * woła `{baseUrl}/services/{method}`, dokładnie jak `usos_url()` w
 * aplikacji webowej.
 */
class UsosApiClient(
    val baseUrl: String,
    private val consumerKey: String,
    private val consumerSecret: String,
) {
    private val client = OkHttpClient()

    var accessToken: String? = null
    var accessTokenSecret: String? = null

    fun get(method: String, params: Map<String, String> = emptyMap()): UsosResponse {
        val url = "$baseUrl/services/$method"
        val signed = sign("GET", url, params)
        val allParams = params + signed

        val httpUrl = url.toHttpUrl().newBuilder().apply {
            allParams.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        val request = Request.Builder().url(httpUrl).get().build()
        client.newCall(request).execute().use { response ->
            return UsosResponse(response.isSuccessful, response.body?.string().orEmpty())
        }
    }

    fun post(method: String, params: Map<String, String> = emptyMap()): UsosResponse {
        val url = "$baseUrl/services/$method"
        val signed = sign("POST", url, params)
        val allParams = params + signed

        val formBody = FormBody.Builder().apply {
            allParams.forEach { (k, v) -> add(k, v) }
        }.build()

        val request = Request.Builder().url(url).post(formBody).build()
        client.newCall(request).execute().use { response ->
            return UsosResponse(response.isSuccessful, response.body?.string().orEmpty())
        }
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
