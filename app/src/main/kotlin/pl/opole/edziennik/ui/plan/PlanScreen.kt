package pl.opole.edziennik.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import pl.opole.edziennik.viewmodel.PlanViewModel
import pl.opole.edziennik.viewmodel.PlanViewModelFactory
import pl.opole.edziennik.viewmodel.academicYearMonths
import pl.opole.edziennik.viewmodel.academicYearStart
import java.io.File
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Odpowiednik trasy `/plan` (plan.html) z aplikacji webowej — jeden
 * miesiąc naraz, z przełącznikiem roku i zakładkami miesięcy. Dane są
 * cache'owane na dysku — przycisk "⟳" wymusza świeże pobranie bieżącego
 * miesiąca. */
@Composable
fun PlanScreen(apiClient: UsosApiClient, cacheDir: File, navController: NavHostController) {
    val repository = remember { UsosRepository(apiClient, cacheDir) }
    val viewModel: PlanViewModel = viewModel(factory = PlanViewModelFactory(repository))
    val state by viewModel.uiState.collectAsState()

    val startYear = academicYearStart(state.yearMonth)
    val months = academicYearMonths(startYear)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan zajęć") },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) { Text("←", fontSize = 22.sp) }
                },
                actions = {
                    TextButton(onClick = { viewModel.load(state.yearMonth, forceRefresh = true) }) { Text("⟳", fontSize = 22.sp) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            YearSwitcher(startYear = startYear, onSelectYear = { newStartYear ->
                viewModel.load(YearMonth.of(newStartYear, 10))
            })

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(months) { month ->
                    val isSelected = month == state.yearMonth
                    TextButton(onClick = { viewModel.load(month) }) {
                        Text(
                            text = month.month.getDisplayName(TextStyle.SHORT, Locale("pl"))
                                .replaceFirstChar { it.uppercase() },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            when {
                state.isLoading && state.days.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.days.isEmpty() -> Column {
                    state.error?.let { ErrorBanner(modifier = Modifier.padding(16.dp)) }
                    Text(
                        "Brak zajęć w tym miesiącu.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.error?.let { item { ErrorBanner() } }
                    state.days.forEach { day ->
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
private fun YearSwitcher(startYear: Int, onSelectYear: (Int) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = { onSelectYear(startYear - 1) }) {
            Text("${startYear - 1}/${startYear}")
        }
        Text("$startYear/${startYear + 1}", fontWeight = FontWeight.Bold)
        TextButton(onClick = { onSelectYear(startYear + 1) }) {
            Text("${startYear + 1}/${startYear + 2}")
        }
    }
}
