package pl.opole.edziennik.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.oauth.TokenStore
import pl.opole.edziennik.oauth.UsosAuthRepository
import pl.opole.edziennik.sync.SyncScheduler

sealed interface AuthState {
    data object CheckingSession : AuthState
    data object LoggedOut : AuthState
    data object LoggingIn : AuthState
    data class Error(val message: String) : AuthState
    data object LoggedIn : AuthState
}

/** Odpowiednik tras `/login`, `/callback` i `/logout` z aplikacji webowej. */
class AuthViewModel(
    private val authRepository: UsosAuthRepository,
    private val tokenStore: TokenStore,
    private val apiClient: UsosApiClient,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.CheckingSession)
    val state: StateFlow<AuthState> = _state

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { tokenStore.load() }
            if (saved != null) {
                apiClient.accessToken = saved.accessToken
                apiClient.accessTokenSecret = saved.accessTokenSecret
                _state.value = AuthState.LoggedIn
                SyncScheduler.enqueue(appContext)
            } else {
                _state.value = AuthState.LoggedOut
            }
        }
    }

    /** Krok 1 logowania — zwraca adres do otwarcia w przeglądarce, albo
     * null (i ustawia stan błędu), jeśli USOS odrzuci zapytanie. */
    suspend fun startLogin(): String? = withContext(Dispatchers.IO) {
        _state.value = AuthState.LoggingIn
        try {
            authRepository.startLogin()
        } catch (e: Exception) {
            _state.value = AuthState.Error(e.message ?: "Nie udało się rozpocząć logowania.")
            null
        }
    }

    /** Krok 2 logowania — wywoływane przez MainActivity po powrocie z przeglądarki. */
    fun completeLogin(oauthVerifier: String) {
        viewModelScope.launch {
            try {
                val credentials = withContext(Dispatchers.IO) { authRepository.completeLogin(oauthVerifier) }
                withContext(Dispatchers.IO) { tokenStore.save(credentials) }
                _state.value = AuthState.LoggedIn
                SyncScheduler.enqueue(appContext)
            } catch (e: Exception) {
                _state.value = AuthState.Error(e.message ?: "Nie udało się zalogować.")
            }
        }
    }

    fun reportLoginCancelled() {
        _state.value = AuthState.LoggedOut
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { tokenStore.clear() }
            apiClient.accessToken = null
            apiClient.accessTokenSecret = null
            SyncScheduler.cancel(appContext)
            _state.value = AuthState.LoggedOut
        }
    }
}

class AuthViewModelFactory(
    private val authRepository: UsosAuthRepository,
    private val tokenStore: TokenStore,
    private val apiClient: UsosApiClient,
    private val appContext: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AuthViewModel(authRepository, tokenStore, apiClient, appContext) as T
    }
}
