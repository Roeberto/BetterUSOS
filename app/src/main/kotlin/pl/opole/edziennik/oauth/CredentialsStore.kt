package pl.opole.edziennik.oauth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.credentialsDataStore by preferencesDataStore(name = "usos_consumer_credentials")

data class UsosConsumerCredentials(val consumerKey: String, val consumerSecret: String)

/**
 * Trwałe przechowywanie klucza/sekretu konsumenta USOS, wpisanego przez
 * użytkownika przy pierwszym uruchomieniu (patrz `SetupScreen`) — appka NIE
 * ma ich wbudowanych na stałe w kodzie ani w BuildConfig, dzięki czemu
 * zbudowany plik APK można bezpiecznie publikować (np. automatycznie w CI
 * przy każdym commicie) bez ujawniania sekretu każdemu, kto go
 * zdekompiluje — w skompilowanej appce po prostu go nie ma.
 */
class CredentialsStore(private val context: Context) {
    private val keyConsumerKey = stringPreferencesKey("consumer_key")
    private val keyConsumerSecret = stringPreferencesKey("consumer_secret")

    suspend fun save(credentials: UsosConsumerCredentials) {
        context.credentialsDataStore.edit { prefs ->
            prefs[keyConsumerKey] = credentials.consumerKey
            prefs[keyConsumerSecret] = credentials.consumerSecret
        }
    }

    suspend fun load(): UsosConsumerCredentials? {
        val prefs = context.credentialsDataStore.data.first()
        val key = prefs[keyConsumerKey]?.takeIf { it.isNotBlank() } ?: return null
        val secret = prefs[keyConsumerSecret]?.takeIf { it.isNotBlank() } ?: return null
        return UsosConsumerCredentials(key, secret)
    }

    suspend fun clear() {
        context.credentialsDataStore.edit { it.clear() }
    }
}
