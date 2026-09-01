package pl.opole.edziennik.ui.login

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import pl.opole.edziennik.viewmodel.AuthState
import pl.opole.edziennik.viewmodel.AuthViewModel

/** Odpowiednik strony `/` (index.html) z aplikacji webowej. */
@Composable
fun LoginScreen(authViewModel: AuthViewModel, navController: NavHostController) {
    val authState by authViewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) {
            navController.navigate("dashboard") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("e-dziennik", style = MaterialTheme.typography.headlineSmall)
        Text("Politechnika Opolska", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Text(
            "Twoje oceny i plan zajęć z USOS. Logowanie odbywa się przez oficjalną " +
                "stronę USOS — ta aplikacja nigdy nie zobaczy Twojego hasła.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))

        when (val state = authState) {
            AuthState.CheckingSession, AuthState.LoggingIn -> {
                CircularProgressIndicator()
            }
            is AuthState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                LoginButton(authViewModel, scope) { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            else -> {
                LoginButton(authViewModel, scope) { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        }
    }
}

@Composable
private fun LoginButton(
    authViewModel: AuthViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    onUrlReady: (String) -> Unit,
) {
    Button(onClick = {
        scope.launch {
            val url = authViewModel.startLogin()
            if (url != null) onUrlReady(url)
        }
    }) {
        Text("Zaloguj się przez USOS")
    }
}
