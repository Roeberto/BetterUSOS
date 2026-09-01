package pl.opole.edziennik.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import pl.opole.edziennik.R
import pl.opole.edziennik.data.UsosRepository
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.ui.components.AppIconButton
import pl.opole.edziennik.ui.components.ErrorBanner
import pl.opole.edziennik.ui.components.SessionCard
import pl.opole.edziennik.ui.components.sessionCardClickHandler
import pl.opole.edziennik.ui.theme.PillShape
import pl.opole.edziennik.viewmodel.PlanViewModel
import pl.opole.edziennik.viewmodel.PlanViewModelFactory
import pl.opole.edziennik.viewmodel.academicYearMonths
import pl.opole.edziennik.viewmodel.academicYearStart
import java.io.File
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Odpowiednik trasy `/plan` (plan.html) z aplikacji webowej — jeden
 * miesiąc naraz, z przełącznikiem roku, zakładkami miesięcy i paskiem
 * szybkiego wyboru dnia (tylko dni z zajęciami — `state.days` i tak
 * zawiera wyłącznie takie, patrz `group_by_day` w app.py/`groupByDay` w
 * UsosRepository). Dane są cache'owane na dysku — przycisk odświeżania
 * wymusza świeże pobranie bieżącego miesiąca. */
@Composable
fun PlanScreen(apiClient: UsosApiClient, cacheDir: File, navController: NavHostController) {
    val repository = remember { UsosRepository(apiClient, cacheDir) }
    val viewModel: PlanViewModel = viewModel(factory = PlanViewModelFactory(repository))
    val state by viewModel.uiState.collectAsState()

    val startYear = academicYearStart(state.yearMonth)
    val months = academicYearMonths(startYear)

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Indeks elementu nagłówka każdego dnia w LazyColumn poniżej — lista
    // przeplata nagłówki dni i karty zajęć o zmiennej długości, więc trzeba
    // to policzyć samemu, żeby wiedzieć, dokąd przewinąć po kliknięciu w
    // pasek wyboru dnia.
    val dayItemIndices = remember(state.days, state.error) {
        val indices = mutableListOf<Int>()
        var index = if (state.error != null) 1 else 0
        state.days.forEach { day ->
            indices.add(index)
            index += 1 + day.entries.size
        }
        indices
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Plan zajęć") },
                navigationIcon = {
                    Box(Modifier.padding(start = 4.dp)) {
                        AppIconButton(R.drawable.ic_back, "Wstecz") { navController.popBackStack() }
                    }
                },
                actions = {
                    Box(Modifier.padding(end = 8.dp)) {
                        AppIconButton(R.drawable.ic_refresh, "Odśwież") {
                            viewModel.load(state.yearMonth, forceRefresh = true)
                        }
                    }
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
                    val background = if (isSelected) {
                        Modifier.background(MaterialTheme.colorScheme.surfaceContainer, PillShape)
                    } else {
                        Modifier
                    }
                    TextButton(onClick = { viewModel.load(month) }, modifier = background) {
                        Text(
                            text = month.month.getDisplayName(TextStyle.SHORT, Locale("pl"))
                                .replaceFirstChar { it.uppercase() },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            if (state.days.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(state.days) { i, day ->
                        Column(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(2.dp))
                                .clickable { scope.launch { listState.animateScrollToItem(dayItemIndices[i]) } }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                day.date.dayOfMonth.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                day.weekday.take(3),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                    state = listState,
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
