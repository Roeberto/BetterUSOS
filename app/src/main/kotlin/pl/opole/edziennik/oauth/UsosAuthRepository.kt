package pl.opole.edziennik.oauth

import android.net.Uri
import pl.opole.edziennik.network.UsosApiClient
import java.net.URLDecoder

data class UsosCredentials(val accessToken: String, val accessTokenSecret: String)

/** Krok 1 zwraca zarówno adres do otwarcia w przeglądarce, jak i request
 * token/secret — wywołujący musi je utrwalić (patrz TokenStore), bo
 * przeglądarka to osobna aktywność/zadanie i Android może w międzyczasie
 * ubić proces appki w tle, zanim użytkownik wróci z autoryzacji. */
data class PendingLogin(val authorizeUrl: String, val requestToken: String, val requestTokenSecret: String)

/**
 * Odpowiednik tras `/login` i `/callback` z aplikacji webowej — trzy kroki
 * OAuth 1.0a: request token -> autoryzacja w przeglądarce -> access token.
 *
 * `client` woła serwer podpisujący (Worker), więc `client.baseUrl` to jego
 * adres — ale strona autoryzacji (`oauth/authorize`) to zwykła, niepodpisywana
 * strona HTML na PRAWDZIWYM USOS, którą trzeba otworzyć w przeglądarce, nie
 * przez Worker — stąd osobny `webBaseUrl`.
 *
 * UWAGA: celowo bezstanowy (request token nie jest polem instancji) — patrz
 * PendingLogin i AuthViewModel, które utrwalają go między krokami 1 i 2.
 */
class UsosAuthRepository(
    private val client: UsosApiClient,
    private val webBaseUrl: String,
    private val callbackUrl: String,
    private val scopes: String,
) {
    /** Krok 1: pobiera request token i zwraca adres do otwarcia w przeglądarce. */
    fun startLogin(): PendingLogin {
        val resp = client.post(
            "oauth/request_token",
            params = mapOf("oauth_callback" to callbackUrl, "scopes" to scopes),
        )
        if (!resp.isSuccessful) error(resp.body)

        val parsed = parseFormEncoded(resp.body)
        val token = parsed["oauth_token"] ?: error("USOS nie zwrócił oauth_token (request token).")
        val secret = parsed["oauth_token_secret"] ?: error("USOS nie zwrócił oauth_token_secret (request token).")

        val authorizeUrl = "$webBaseUrl/services/oauth/authorize?oauth_token=${Uri.encode(token)}"
        return PendingLogin(authorizeUrl, token, secret)
    }

    /** Krok 2: po powrocie z przeglądarki (oauth_verifier w URI) wymienia token na dostępowy.
     * `token`/`secret` to request token z kroku 1, odczytany przez wywołującego z trwałego magazynu. */
    fun completeLogin(oauthVerifier: String, token: String, secret: String): UsosCredentials {
        client.accessToken = token
        client.accessTokenSecret = secret

        val resp = client.post("oauth/access_token", params = mapOf("oauth_verifier" to oauthVerifier))
        if (!resp.isSuccessful) error(resp.body)

        val parsed = parseFormEncoded(resp.body)
        val accessToken = parsed["oauth_token"] ?: error("USOS nie zwrócił oauth_token (access token).")
        val accessTokenSecret = parsed["oauth_token_secret"]
            ?: error("USOS nie zwrócił oauth_token_secret.")

        client.accessToken = accessToken
        client.accessTokenSecret = accessTokenSecret
        return UsosCredentials(accessToken, accessTokenSecret)
    }

    private fun parseFormEncoded(body: String): Map<String, String> =
        body.split("&").filter { it.isNotBlank() }.associate { pair ->
            val parts = pair.split("=", limit = 2)
            val key = URLDecoder.decode(parts[0], "UTF-8")
            val value = URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
            key to value
        }
}
