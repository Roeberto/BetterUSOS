package pl.opole.edziennik.ui.payments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import pl.opole.edziennik.R
import pl.opole.edziennik.data.UsosRepository
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.ui.components.AppIconButton
import pl.opole.edziennik.ui.components.ErrorBanner
import pl.opole.edziennik.ui.theme.CardShape
import pl.opole.edziennik.viewmodel.PaymentsViewModel
import pl.opole.edziennik.viewmodel.PaymentsViewModelFactory
import java.io.File
import java.util.Locale

/** Osobna zakładka na płatności — wcześniej sekcja na Pulpicie, wydzielona
 * na życzenie na własny ekran, dostępny z paska na dole obok Ocen i Planu.
 * Dane są cache'owane na dysku — przycisk odświeżania wymusza świeże
 * pobranie; jeśli zawiedzie, ostatnio pokazane płatności zostają na
 * ekranie razem z małym banerem błędu. */
@Composable
fun PaymentsScreen(apiClient: UsosApiClient, cacheDir: File, navController: NavHostController) {
    val repository = remember { UsosRepository(apiClient, cacheDir) }
    val viewModel: PaymentsViewModel = viewModel(factory = PaymentsViewModelFactory(repository))
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Płatności") },
                navigationIcon = {
                    Box(Modifier.padding(start = 4.dp)) {
                        AppIconButton(R.drawable.ic_back, "Wstecz") { navController.popBackStack() }
                    }
                },
                actions = {
                    Box(Modifier.padding(end = 8.dp)) {
                        AppIconButton(R.drawable.ic_refresh, "Odśwież") { viewModel.refresh(forceRefresh = true) }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading && state.payments.isEmpty()) {
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
            state.error?.let { item { ErrorBanner() } }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, CardShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CardShape)
                        .padding(horizontal = 14.dp),
                ) {
                    if (state.payments.isEmpty()) {
                        Text(
                            "Brak zaległych płatności.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Do zapłaty łącznie", fontWeight = FontWeight.Bold)
                            Text(
                                "${amount(state.paymentsTotal)} ${state.payments.first().currency}",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.onSurface),
                        )
                        state.payments.forEachIndexed { index, payment ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
                                Text(payment.typeLabel + (payment.description?.let { " — $it" } ?: ""))
                                payment.paymentDeadline?.let {
                                    Text("termin: $it", style = MaterialTheme.typography.labelSmall)
                                }
                                Text("${amount(payment.amount)} ${payment.currency}", fontWeight = FontWeight.SemiBold)
                            }
                            if (index != state.payments.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun amount(value: Double): String = String.format(Locale.US, "%.2f", value)
