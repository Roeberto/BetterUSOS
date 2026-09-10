package pl.opole.edziennik.ui.payments

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import pl.opole.edziennik.R
import pl.opole.edziennik.data.Payment
import pl.opole.edziennik.data.UsosRepository
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.ui.components.AppIconButton
import pl.opole.edziennik.ui.components.ErrorBanner
import pl.opole.edziennik.ui.theme.CardShape
import pl.opole.edziennik.ui.theme.PillShape
import pl.opole.edziennik.viewmodel.PaymentsViewModel
import pl.opole.edziennik.viewmodel.PaymentsViewModelFactory
import java.io.File
import java.util.Locale

/** Osobna zakładka na płatności — wcześniej sekcja na Pulpicie, wydzielona
 * na życzenie na własny ekran, dostępny z paska na dole obok Ocen i Planu.
 * Dwie zakładki: należności nierozliczone i już rozliczone. Każda pozycja
 * to zwijana karta z terminem, kwotą, numerem konta (do skopiowania) i
 * opisem. Dane są cache'owane na dysku — przycisk odświeżania wymusza
 * świeże pobranie; jeśli zawiedzie, ostatnio pokazane płatności zostają na
 * ekranie razem z małym banerem błędu. */
@Composable
fun PaymentsScreen(apiClient: UsosApiClient, cacheDir: File, navController: NavHostController) {
    val repository = remember { UsosRepository(apiClient, cacheDir) }
    val viewModel: PaymentsViewModel = viewModel(factory = PaymentsViewModelFactory(repository))
    val state by viewModel.uiState.collectAsState()
    var showSettled by rememberSaveable { mutableStateOf(false) }

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
        if (state.isLoading && state.outstanding.isEmpty() && state.settled.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            PaymentsTabs(showSettled) { showSettled = it }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.surfaceVariant))

            val list = if (showSettled) state.settled else state.outstanding

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.error?.let { item { ErrorBanner() } }

                if (!showSettled && state.outstanding.isNotEmpty()) {
                    item {
                        TotalCard(
                            total = state.outstandingTotal,
                            currency = state.outstanding.first().currency,
                        )
                    }
                }

                if (list.isEmpty()) {
                    item {
                        Text(
                            if (showSettled) "Brak należności rozliczonych." else "Brak należności nierozliczonych.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(list) { payment -> PaymentCard(payment) }
                }
            }
        }
    }
}

@Composable
private fun PaymentsTabs(showSettled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PaymentTab("Nierozliczone", selected = !showSettled) { onChange(false) }
        PaymentTab("Rozliczone", selected = showSettled) { onChange(true) }
    }
}

@Composable
private fun PaymentTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .height(2.dp)
                .width(if (selected) 28.dp else 0.dp)
                .background(MaterialTheme.colorScheme.secondary),
        )
    }
}

@Composable
private fun TotalCard(total: Double, currency: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, CardShape)
            .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CardShape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Do zapłaty łącznie", style = MaterialTheme.typography.titleMedium)
        Text(
            "${amount(total)} $currency",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/** Pojedyncza należność — klikalny nagłówek (typ + chevron) zwija/rozwija
 * szczegóły: termin, kwota, numer konta (z przyciskiem kopiowania) i opis. */
@Composable
private fun PaymentCard(payment: Payment) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, CardShape)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                payment.typeLabel,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_down),
                contentDescription = if (expanded) "Zwiń" else "Rozwiń",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
            )
        }

        if (expanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                payment.paymentDeadline?.let { InfoRow("Termin zapłaty", it) }
                InfoRow("Kwota", "${amount(payment.amount)} ${payment.currency}")
                payment.accountNumber?.let { InfoRow("Nr konta", it) }
                payment.description?.let { InfoRow("Opis", it) }

                payment.accountNumber?.let { account ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Kopiuj numer konta",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.secondary)
                            .clickable {
                                clipboard.setText(AnnotatedString(account))
                                Toast.makeText(context, "Skopiowano numer konta", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun amount(value: Double): String = String.format(Locale.US, "%.2f", value)
