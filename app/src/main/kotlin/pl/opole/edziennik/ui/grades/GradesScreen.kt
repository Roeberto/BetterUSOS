package pl.opole.edziennik.ui.grades

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import pl.opole.edziennik.R
import pl.opole.edziennik.data.CourseGrades
import pl.opole.edziennik.data.DistributionBar
import pl.opole.edziennik.data.GradeEntry
import pl.opole.edziennik.data.UsosRepository
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.ui.components.AppIconButton
import pl.opole.edziennik.ui.components.ErrorBanner
import pl.opole.edziennik.ui.theme.PillShape
import pl.opole.edziennik.viewmodel.GradesViewModel
import pl.opole.edziennik.viewmodel.GradesViewModelFactory
import java.io.File
import java.util.Locale

/**
 * Odpowiednik trasy `/oceny` (grades.html) z aplikacji webowej — oceny
 * pogrupowane po semestrze i przedmiocie, z punktami ECTS i średnią ważoną.
 * Rozkład ocen grupy jest dociągany dopiero po rozwinięciu wpisu (ten sam
 * wzorzec co inline `<details>` w wersji webowej) i pokazany razem z resztą
 * dodatkowych informacji o ocenie, nie osobno. Dane są cache'owane na dysku —
 * przycisk odświeżania wymusza świeże pobranie.
 */
@Composable
fun GradesScreen(apiClient: UsosApiClient, cacheDir: File, navController: NavHostController) {
    val repository = remember { UsosRepository(apiClient, cacheDir) }
    val viewModel: GradesViewModel = viewModel(factory = GradesViewModelFactory(repository))
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Oceny") },
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
        when {
            state.isLoading && state.termSections.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.termSections.isEmpty() -> Column(Modifier.padding(padding)) {
                state.error?.let { ErrorBanner(modifier = Modifier.padding(16.dp)) }
                Text(
                    "Brak ocen do wyświetlenia.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.error?.let { item { ErrorBanner(modifier = Modifier.padding(bottom = 8.dp)) } }

                state.weightedAverage?.let { average ->
                    item {
                        Text(
                            "Średnia ważona: ${formatNumber(average, 2)}",
                            modifier = Modifier
                                .padding(bottom = 18.dp)
                                .border(1.dp, MaterialTheme.colorScheme.primary, PillShape)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }

                state.termSections.forEach { section ->
                    item {
                        Text(
                            (section.term ?: "—").uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                        )
                    }
                    items(section.courses) { course -> CourseSection(course, repository) }
                }
            }
        }
    }
}

@Composable
private fun CourseSection(course: CourseGrades, repository: UsosRepository) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(course.courseName, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
            course.ectsPoints?.let {
                Text(
                    "${formatNumber(it, 1)} ECTS",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        course.entries.forEachIndexed { index, entry ->
            GradeEntryRow(entry, repository)
            if (index != course.entries.lastIndex) {
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

/** Pojedynczy wpis oceny — klikalny, rozwija datę wpisania, autora i (jeśli
 * ocena ma `examId`) rozkład procentowy ocen całej grupy, dociągany leniwie
 * dopiero przy pierwszym rozwinięciu. */
@Composable
private fun GradeEntryRow(entry: GradeEntry, repository: UsosRepository) {
    var expanded by remember { mutableStateOf(false) }
    var distribution by remember(entry.examId) { mutableStateOf<List<DistributionBar>?>(null) }
    var distributionError by remember(entry.examId) { mutableStateOf<String?>(null) }
    var distributionLoading by remember(entry.examId) { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded && entry.examId != null && distribution == null && !distributionLoading) {
            distributionLoading = true
            val result = repository.fetchGradeDistribution(entry.examId)
            distribution = result.getOrNull()
            distributionError = result.exceptionOrNull()?.message
            distributionLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    entry.valueSymbol ?: "—",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
                if (entry.valueDescription.isNotEmpty()) {
                    Text(entry.valueDescription, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                if (entry.dateModified.isNotEmpty()) {
                    Text("Wprowadzono: ${entry.dateModified}", fontSize = 12.sp)
                }
                if (entry.issuerName.isNotEmpty()) {
                    Text("Przez: ${entry.issuerName}", fontSize = 12.sp)
                }

                if (entry.examId != null) {
                    when {
                        distributionLoading -> CircularProgressIndicator(
                            modifier = Modifier.padding(top = 8.dp).size(20.dp),
                        )
                        distributionError != null -> Text(
                            "Nie udało się pobrać rozkładu ocen.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        distribution != null -> Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer, PillShape)
                                .padding(12.dp),
                        ) {
                            DistributionChart(distribution!!, entry.valueSymbol)
                        }
                    }
                }
            }
        }
    }
}

/** Histogram procentowego rozkładu ocen całej grupy dla danego egzaminu —
 * słupek odpowiadający własnej ocenie jest pokolorowany inaczej (kolor
 * `secondary`/oxblood z motywu, ten sam co `.dist-bar-mine` w style.css). */
@Composable
private fun DistributionChart(bars: List<DistributionBar>, mySymbol: String?) {
    val maxPercent = bars.maxOfOrNull { it.percent }?.takeIf { it > 0.0 } ?: 1.0
    val maxBarHeight = 56.dp

    Column {
        Text(
            "Rozkład ocen grupy",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            bars.forEach { bar ->
                val isMine = bar.symbol == mySymbol
                val barHeight = maxBarHeight * (bar.percent / maxPercent).toFloat().coerceIn(0.03f, 1f)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${Math.round(bar.percent)}%", fontSize = 10.sp)
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(barHeight)
                            .background(
                                if (isMine) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        bar.symbol,
                        fontSize = 11.sp,
                        fontWeight = if (isMine) FontWeight.Bold else FontWeight.Normal,
                        color = if (isMine) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatNumber(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)
