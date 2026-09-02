package pl.opole.edziennik.oauth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "usos_auth")

data class RequestToken(val token: String, val secret: String)

/** Trwałe przechowywanie tokenu dostępowego, żeby nie logować się przy
 * każdym uruchomieniu aplikacji. */
class TokenStore(private val context: Context) {
    private val keyToken = stringPreferencesKey("access_token")
    private val keySecret = stringPreferencesKey("access_token_secret")
    private val keyRequestToken = stringPreferencesKey("pending_request_token")
    private val keyRequestSecret = stringPreferencesKey("pending_request_token_secret")

    suspend fun save(credentials: UsosCredentials) {
        context.dataStore.edit { prefs ->
            prefs[keyToken] = credentials.accessToken
            prefs[keySecret] = credentials.accessTokenSecret
        }
    }

    suspend fun load(): UsosCredentials? {
        val prefs = context.dataStore.data.first()
        val token = prefs[keyToken] ?: return null
        val secret = prefs[keySecret] ?: return null
        return UsosCredentials(token, secret)
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    /** Request token/secret (krok 1 OAuth) muszą przetrwać do chwili powrotu
     * z przeglądarki — a Android może w międzyczasie ubić proces appki w
     * tle, kasując wszystko, co siedziało tylko w pamięci (patrz
     * UsosAuthRepository). Stąd zapis na dysk zamiast zwykłego pola. */
    suspend fun savePendingRequestToken(token: String, secret: String) {
        context.dataStore.edit { prefs ->
            prefs[keyRequestToken] = token
            prefs[keyRequestSecret] = secret
        }
    }

    suspend fun loadPendingRequestToken(): RequestToken? {
        val prefs = context.dataStore.data.first()
        val token = prefs[keyRequestToken] ?: return null
        val secret = prefs[keyRequestSecret] ?: return null
        return RequestToken(token, secret)
    }

    suspend fun clearPendingRequestToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(keyRequestToken)
            prefs.remove(keyRequestSecret)
        }
    }
}
