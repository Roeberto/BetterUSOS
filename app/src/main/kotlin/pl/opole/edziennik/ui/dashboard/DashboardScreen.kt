package pl.opole.edziennik.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import pl.opole.edziennik.R
import pl.opole.edziennik.data.UsosRepository
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.ui.components.AppIconButton
import pl.opole.edziennik.ui.components.ErrorBanner
import pl.opole.edziennik.ui.components.SessionCard
import pl.opole.edziennik.ui.components.sessionCardClickHandler
import pl.opole.edziennik.viewmodel.AuthViewModel
import pl.opole.edziennik.viewmodel.DashboardViewModel
import pl.opole.edziennik.viewmodel.DashboardViewModelFactory
import java.io.File

/** Odpowiednik trasy `/dashboard` (dashboard.html) z aplikacji webowej —
 * plan na najbliższe 7 dni. Płatności/Oceny/Plan/Wyloguj mają własne
 * zakładki dostępne z paska na dole ekranu. Dane są cache'owane na dysku
 * (patrz `UsosRepository`) — przycisk odświeżania wymusza świeże pobranie;
 * jeśli ono zawiedzie, ostatnio pokazane dane zostają na ekranie razem z
 * małym banerem błędu (patrz `ErrorBanner`), zamiast znikać. */
@Composable
fun DashboardScreen(
    apiClient: UsosApiClient,
    cacheDir: File,
    navController: NavHostController,
    authViewModel: AuthViewModel,
) {
    val repository = remember { UsosRepository(apiClient, cacheDir) }
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModelFactory(repository))
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("e-dziennik") },
                actions = {
                    AppIconButton(R.drawable.ic_refresh, "Odśwież") { viewModel.refresh(forceRefresh = true) }
                    Box(Modifier.padding(start = 8.dp, end = 4.dp)) {
                        AppIconButton(R.drawable.ic_bell, "Powiadomienia") { navController.navigate("notifications") }
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                BottomNavItem(R.drawable.ic_payments, "Płatności", Modifier.weight(1f)) {
                    navController.navigate("payments")
                }
                BottomNavItem(R.drawable.ic_grades, "Oceny", Modifier.weight(1f)) { navController.navigate("grades") }
                BottomNavItem(R.drawable.ic_calendar, "Plan", Modifier.weight(1f)) { navController.navigate("plan") }
                BottomNavItem(R.drawable.ic_logout, "Wyloguj", Modifier.weight(1f)) {
                    authViewModel.logout()
                    navController.navigate("login") { popUpTo(0) }
                }
            }
        },
    ) { padding ->
        if (state.isLoading && state.schedule.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("Plan zajęć — najbliższe 7 dni", style = MaterialTheme.typography.titleMedium) }

            state.scheduleError?.let { item { ErrorBanner() } }

            when {
                state.schedule.isEmpty() -> item {
                    Text("Brak zaplanowanych zajęć w tym okresie.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    state.schedule.forEach { day ->
                        item { Text("${day.weekday} ${day.dateLabel}", fontWeight = FontWeight.SemiBold) }
                        items(day.entries) { entry ->
                            SessionCard(
                                entry = entry,
                                onClick = sessionCardClickHandler(navController, entry.unitId, entry.groupNumber),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(iconRes: Int, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}
