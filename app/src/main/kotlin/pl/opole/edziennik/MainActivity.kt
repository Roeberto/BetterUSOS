package pl.opole.edziennik

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.oauth.TokenStore
import pl.opole.edziennik.oauth.UsosAuthRepository
import pl.opole.edziennik.ui.EdziennikNavHost
import pl.opole.edziennik.ui.theme.EdziennikTheme
import pl.opole.edziennik.viewmodel.AuthViewModel
import pl.opole.edziennik.viewmodel.AuthViewModelFactory

class MainActivity : ComponentActivity() {

    private val apiClient by lazy { UsosApiClient(Config.USOS_BASE_URL) }
    private val authRepository by lazy {
        UsosAuthRepository(apiClient, Config.USOS_WEB_BASE_URL, Config.OAUTH_CALLBACK_URL, Config.OAUTH_SCOPES)
    }
    private val tokenStore by lazy { TokenStore(applicationContext) }

    // Trwały cache odpowiedzi USOS API (patrz DiskCache) — jeden katalog na
    // całą appkę, przekazywany w dół przez EdziennikNavHost do każdego
    // ekranu, który tworzy własne UsosRepository. Nazwa NIE może być
    // "cacheDir" — Activity/Context ma już taką metodę (getCacheDir()),
    // więc właściwość o tej nazwie koliduje z nią na poziomie JVM.
    private val apiCacheDir get() = applicationContext.filesDir

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(authRepository, tokenStore, apiClient, applicationContext)
    }

    // Ustawiane, gdy appka jest otwierana kliknięciem w powiadomienie z
    // SyncWorkera — patrz handleIncomingIntent() i EXTRA_OPEN_NOTIFICATIONS.
    private val openNotifications = mutableStateOf(false)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* brak zgody = po prostu brak systemowych powiadomień, historia i tak działa */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            EdziennikTheme {
                // Appka nigdy nie zna klucza/sekretu konsumenta USOS (patrz
                // Config.kt/proxy/) — zero konfiguracji dla użytkownika,
                // prosto do logowania/pulpitu.
                val navController = rememberNavController()
                val shouldOpenNotifications by openNotifications
                LaunchedEffect(shouldOpenNotifications) {
                    if (shouldOpenNotifications) {
                        navController.navigate("notifications")
                        openNotifications.value = false
                    }
                }
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

    /** Przechwytuje powrót z przeglądarki po autoryzacji w USOS (patrz
     * intent-filter w AndroidManifest.xml i Config.OAUTH_CALLBACK_URL) oraz
     * kliknięcie w powiadomienie z SyncWorkera. */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            openNotifications.value = true
        }

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

    /** Android 13+ (API 33) wymaga zgody runtime, żeby SyncWorker mógł w
     * ogóle pokazać powiadomienie systemowe — bez niej zdarzenia i tak
     * trafiają do zakładki "Powiadomienia", tylko bez dźwięku/alertu. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
    }
}
