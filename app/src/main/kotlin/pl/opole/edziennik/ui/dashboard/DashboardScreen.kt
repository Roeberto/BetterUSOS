package pl.opole.edziennik.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import pl.opole.edziennik.data.UsosRepository
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.ui.components.ErrorBanner
import pl.opole.edziennik.ui.components.SessionCard
import pl.opole.edziennik.ui.components.sessionCardClickHandler
import pl.opole.edziennik.viewmodel.AuthViewModel
import pl.opole.edziennik.viewmodel.DashboardViewModel
import pl.opole.edziennik.viewmodel.DashboardViewModelFactory
import java.io.File
import java.util.Locale

/** Odpowiednik trasy `/dashboard` (dashboard.html) z aplikacji webowej —
 * płatności + plan na najbliższe 7 dni. Nawigacja do Ocen/Planu/Wylogowania
 * jest na pasku na dole ekranu (łatwiej trafić kciukiem niż w rozwijane
 * menu w rogu). Dane są cache'owane na dysku (patrz `UsosRepository`) —
 * przycisk "⟳" wymusza świeże pobranie; jeśli ono zawiedzie, ostatnio
 * pokazane dane zostają na ekranie razem z małym banerem błędu (patrz
 * `ErrorBanner`), zamiast znikać. */
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
            TopAppBar(
                title = { Text("e-dziennik") },
                actions = {
                    TextButton(onClick = { viewModel.refresh(forceRefresh = true) }) { Text("⟳", fontSize = 22.sp) }
                    TextButton(onClick = { navController.navigate("notifications") }) { Text("🔔", fontSize = 20.sp) }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                TextButton(onClick = { navController.navigate("grades") }, modifier = Modifier.weight(1f)) {
                    Text("Oceny")
                }
                TextButton(onClick = { navController.navigate("plan") }, modifier = Modifier.weight(1f)) {
                    Text("Plan")
                }
                TextButton(
                    onClick = {
                        authViewModel.logout()
                        navController.navigate("login") { popUpTo(0) }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Wyloguj")
                }
            }
        },
    ) { padding ->
        val hasAnyData = state.payments.isNotEmpty() || state.schedule.isNotEmpty()
        if (state.isLoading && !hasAnyData) {
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
            item { Text("Płatności", style = MaterialTheme.typography.titleMedium) }

            state.paymentsError?.let { item { ErrorBanner() } }

            when {
                state.payments.isEmpty() -> item {
                    Text("Brak zaległych płatności.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    item {
                        Text(
                            "Do zapłaty łącznie: ${amount(state.paymentsTotal)} ${state.payments.first().currency}",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(state.payments) { payment ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                        ) {
                            Text(payment.typeLabel + (payment.description?.let { " — $it" } ?: ""))
                            payment.paymentDeadline?.let {
                                Text("termin: $it", style = MaterialTheme.typography.labelSmall)
                            }
                            Text("${amount(payment.amount)} ${payment.currency}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

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

private fun amount(value: Double): String = String.format(Locale.US, "%.2f", value)
