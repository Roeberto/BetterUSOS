package pl.opole.edziennik.ui.login

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import pl.opole.edziennik.R
import pl.opole.edziennik.ui.theme.PillShape
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
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_grades),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp).padding(bottom = 18.dp),
        )
        Text("e-dziennik", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Text(
            "Plan zajęć, oceny i płatności z USOS Politechniki Opolskiej — w jednym miejscu, bez przeglądarki.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(30.dp))

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

        Spacer(Modifier.height(16.dp))
        Text(
            "Logowanie odbywa się przez przeglądarkę (OAuth) — appka nie zna Twojego hasła.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoginButton(
    authViewModel: AuthViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    onUrlReady: (String) -> Unit,
) {
    Button(
        onClick = {
            scope.launch {
                val url = authViewModel.startLogin()
                if (url != null) onUrlReady(url)
            }
        },
        shape = PillShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Zaloguj przez USOS", modifier = Modifier.padding(vertical = 4.dp))
    }
}
