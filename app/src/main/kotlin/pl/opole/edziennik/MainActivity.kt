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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.oauth.CredentialsStore
import pl.opole.edziennik.oauth.TokenStore
import pl.opole.edziennik.oauth.UsosAuthRepository
import pl.opole.edziennik.oauth.UsosConsumerCredentials
import pl.opole.edziennik.ui.EdziennikNavHost
import pl.opole.edziennik.ui.setup.SetupScreen
import pl.opole.edziennik.ui.theme.EdziennikTheme
import pl.opole.edziennik.viewmodel.AuthViewModel
import pl.opole.edziennik.viewmodel.AuthViewModelFactory

class MainActivity : ComponentActivity() {

    private val apiClient by lazy { UsosApiClient(Config.USOS_BASE_URL) }
    private val authRepository by lazy {
        UsosAuthRepository(apiClient, Config.OAUTH_CALLBACK_URL, Config.OAUTH_SCOPES)
    }
    private val tokenStore by lazy { TokenStore(applicationContext) }
    private val credentialsStore by lazy { CredentialsStore(applicationContext) }

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
                // Appka nie ma wbudowanego na stałe klucza/sekretu konsumenta
                // USOS (patrz Config.kt) — dopóki użytkownik ich nie poda na
                // SetupScreen, cała reszta appki (łącznie z logowaniem) jest
                // niedostępna.
                var credentialsReady by remember { mutableStateOf<Boolean?>(null) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    val saved = credentialsStore.load()
                    val effective = saved ?: run {
                        // Wygoda lokalnego builda — jeśli local.properties na
                        // TYM komputerze ma klucz USOS, appka konfiguruje się
                        // sama, bez pokazywania SetupScreen (patrz Config.kt).
                        // CI nie ma tego pliku, więc publiczny APK i tak
                        // zawsze wychodzi bez wbudowanego sekretu.
                        if (Config.DEFAULT_CONSUMER_KEY.isNotBlank() && Config.DEFAULT_CONSUMER_SECRET.isNotBlank()) {
                            UsosConsumerCredentials(Config.DEFAULT_CONSUMER_KEY, Config.DEFAULT_CONSUMER_SECRET)
                                .also { credentialsStore.save(it) }
                        } else {
                            null
                        }
                    }
                    if (effective != null) {
                        apiClient.setConsumerCredentials(effective.consumerKey, effective.consumerSecret)
                    }
                    credentialsReady = effective != null
                }

                when (credentialsReady) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    false -> SetupScreen { key, secret ->
                        apiClient.setConsumerCredentials(key, secret)
                        scope.launch { credentialsStore.save(UsosConsumerCredentials(key, secret)) }
                        credentialsReady = true
                    }
                    true -> {
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
