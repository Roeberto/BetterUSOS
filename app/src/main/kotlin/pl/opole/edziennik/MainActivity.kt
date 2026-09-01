package pl.opole.edziennik

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.compose.rememberNavController
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.oauth.TokenStore
import pl.opole.edziennik.oauth.UsosAuthRepository
import pl.opole.edziennik.ui.EdziennikNavHost
import pl.opole.edziennik.ui.theme.EdziennikTheme
import pl.opole.edziennik.viewmodel.AuthViewModel
import pl.opole.edziennik.viewmodel.AuthViewModelFactory

class MainActivity : ComponentActivity() {

    private val apiClient by lazy {
        UsosApiClient(Config.USOS_BASE_URL, Config.USOS_CONSUMER_KEY, Config.USOS_CONSUMER_SECRET)
    }
    private val authRepository by lazy {
        UsosAuthRepository(apiClient, Config.OAUTH_CALLBACK_URL, Config.OAUTH_SCOPES)
    }
    private val tokenStore by lazy { TokenStore(applicationContext) }

    // Trwały cache odpowiedzi USOS API (patrz DiskCache) — jeden katalog na
    // całą appkę, przekazywany w dół przez EdziennikNavHost do każdego
    // ekranu, który tworzy własne UsosRepository. Nazwa NIE może być
    // "cacheDir" — Activity/Context ma już taką metodę (getCacheDir()),
    // więc właściwość o tej nazwie koliduje z nią na poziomie JVM.
    private val apiCacheDir get() = applicationContext.filesDir

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(authRepository, tokenStore, apiClient)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            EdziennikTheme {
                val navController = rememberNavController()
                EdziennikNavHost(
                    navController = navController,
                    authViewModel = authViewModel,
                    apiClient = apiClient,
                    cacheDir = apiCacheDir,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /** Przechwytuje powrót z przeglądarki po autoryzacji w USOS
     * (patrz intent-filter w AndroidManifest.xml i Config.OAUTH_CALLBACK_URL). */
    private fun handleIncomingIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme == "edziennik" && uri.host == "oauth-callback") {
            val verifier = uri.getQueryParameter("oauth_verifier")
            if (verifier != null) {
                authViewModel.completeLogin(verifier)
            } else {
                authViewModel.reportLoginCancelled()
            }
        }
    }
}
