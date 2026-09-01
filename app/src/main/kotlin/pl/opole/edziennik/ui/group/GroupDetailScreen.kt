package pl.opole.edziennik.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import pl.opole.edziennik.R
import pl.opole.edziennik.data.UsosRepository
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.ui.components.AppIconButton
import pl.opole.edziennik.ui.components.ErrorBanner
import pl.opole.edziennik.ui.components.PersonRow
import pl.opole.edziennik.ui.theme.PillShape
import pl.opole.edziennik.viewmodel.GroupDetailViewModel
import pl.opole.edziennik.viewmodel.GroupDetailViewModelFactory
import java.io.File

/**
 * Odpowiednik trasy `/grupa/<unit_id>/<group_number>` (group_detail.html) z
 * aplikacji webowej — przedmiot, forma zajęć, prowadzący (klikalni do strony
 * osoby) i lista pozostałych uczestników (bez samego zalogowanego użytkownika
 * — patrz `current_user_id()`/`fetchGroupDetail()`). Dane są cache'owane na
 * dysku — przycisk odświeżania wymusza świeże pobranie.
 */
@Composable
fun GroupDetailScreen(
    apiClient: UsosApiClient,
    cacheDir: File,
    navController: NavHostController,
    unitId: Int,
    groupNumber: Int,
) {
    val repository = remember { UsosRepository(apiClient, cacheDir) }
    val viewModel: GroupDetailViewModel = viewModel(
        factory = GroupDetailViewModelFactory(repository, unitId, groupNumber),
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grupa zajęciowa") },
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
            state.detail != null -> {
                val detail = state.detail!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.error?.let { ErrorBanner() }

                    Column {
                        Text(detail.courseName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val subline = listOfNotNull(
                            detail.classType.ifEmpty { null },
                            detail.groupNumber?.let { "Grupa nr $it" },
                        ).joinToString(" · ")
                        if (subline.isNotEmpty()) {
                            Text(subline, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        detail.termId?.let {
                            Text(
                                it,
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.primary, PillShape)
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }

                    if (detail.lecturers.isNotEmpty()) {
                        Column {
                            Text("Prowadzący", style = MaterialTheme.typography.titleSmall)
                            detail.lecturers.forEach { lecturer ->
                                PersonRow(
                                    person = lecturer,
                                    onClick = lecturer.id?.let { id ->
                                        { navController.navigate("person/$id") }
                                    },
                                )
                            }
                        }
                    }

                    Column {
                        Text(
                            "Uczestnicy (${detail.participants.size})",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (detail.participants.isEmpty()) {
                            Text(
                                "Brak pozostałych uczestników.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            detail.participants.forEach { participant ->
                                PersonRow(
                                    person = participant,
                                    onClick = participant.id?.let { id ->
                                        { navController.navigate("person/$id") }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Text(
                "Nie udało się pobrać danych grupy.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
