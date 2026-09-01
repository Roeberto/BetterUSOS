package pl.opole.edziennik.oauth

import android.util.Base64
import java.net.URLEncoder
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimalny podpisywacz OAuth 1.0a (HMAC-SHA1) — USOS API używa tego
 * standardu do autoryzacji zapytań. To, co po stronie serwera Flask
 * robiła za nas biblioteka `requests_oauthlib`, tu trzeba zaimplementować
 * ręcznie.
 */
object OAuth1Signer {

    fun percentEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")

    /**
     * Zwraca komplet parametrów `oauth_*` (łącznie z `oauth_signature`) dla
     * danego zapytania. Wywołujący powinien dołączyć je do właściwych
     * parametrów zapytania (query string albo ciało POST) — patrz
     * [pl.opole.edziennik.network.UsosApiClient].
     */
    fun sign(
        method: String,
        url: String,
        params: Map<String, String>,
        consumerKey: String,
        consumerSecret: String,
        token: String? = null,
        tokenSecret: String? = null,
    ): Map<String, String> {
        val oauthParams = mutableMapOf(
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to nonce(),
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000).toString(),
            "oauth_version" to "1.0",
        )
        if (token != null) oauthParams["oauth_token"] = token

        val allParams = (params + oauthParams).toSortedMap()
        val paramString = allParams.entries.joinToString("&") {
            "${percentEncode(it.key)}=${percentEncode(it.value)}"
        }
        val baseString = listOf(
            method.uppercase(),
            percentEncode(url),
            percentEncode(paramString),
        ).joinToString("&")

        val signingKey = "${percentEncode(consumerSecret)}&${percentEncode(tokenSecret ?: "")}"
        oauthParams["oauth_signature"] = hmacSha1(baseString, signingKey)
        return oauthParams
    }

    private fun hmacSha1(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val rawHmac = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(rawHmac, Base64.NO_WRAP)
    }

    private fun nonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP).replace(Regex("[^A-Za-z0-9]"), "")
    }
}
