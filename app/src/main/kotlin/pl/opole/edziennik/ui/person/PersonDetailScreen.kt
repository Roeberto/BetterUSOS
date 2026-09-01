package pl.opole.edziennik.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import pl.opole.edziennik.data.UsosRepository
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.ui.components.ErrorBanner
import pl.opole.edziennik.viewmodel.PersonDetailViewModel
import pl.opole.edziennik.viewmodel.PersonDetailViewModelFactory
import java.io.File

/**
 * Odpowiednik trasy `/osoba/<user_id>` (person_detail.html) z aplikacji
 * webowej — miejsce zatrudnienia, dyżur i dane kontaktowe (na razie głównie
 * przydatne dla prowadzących). Dane są cache'owane na dysku — przycisk "⟳"
 * wymusza świeże pobranie.
 */
@Composable
fun PersonDetailScreen(apiClient: UsosApiClient, cacheDir: File, navController: NavHostController, userId: Int) {
    val repository = remember { UsosRepository(apiClient, cacheDir) }
    val viewModel: PersonDetailViewModel = viewModel(
        factory = PersonDetailViewModelFactory(repository, userId),
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Osoba") },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) { Text("←") }
                },
                actions = {
                    TextButton(onClick = { viewModel.refresh(forceRefresh = true) }) { Text("⟳") }
                },
            )
        },
    ) { padding ->
        when {
            state.detail != null -> {
                val detail = state.detail!!
                val person = detail.person

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.error?.let { ErrorBanner(it) }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (person.photoUrl != null) {
                            AsyncImage(
                                model = person.photoUrl,
                                contentDescription = person.name,
                                modifier = Modifier.size(72.dp).clip(CircleShape),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(person.avatarColor)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(person.initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            }
                        }
                        Column {
                            Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (person.titles.isNotEmpty()) {
                                Text(person.titles, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (detail.employment.isNotEmpty()) {
                        Column {
                            Text("Zatrudnienie", style = MaterialTheme.typography.titleSmall)
                            detail.employment.forEach {
                                Text("${it.position} — ${it.faculty}")
                            }
                        }
                    }

                    if (detail.officeHours.isNotEmpty()) {
                        Column {
                            Text("Dyżur", style = MaterialTheme.typography.titleSmall)
                            Text(detail.officeHours)
                        }
                    }

                    detail.room?.let {
                        Column {
                            Text("Pokój", style = MaterialTheme.typography.titleSmall)
                            Text(it)
                        }
                    }

                    if (detail.phoneNumbers.isNotEmpty()) {
                        Column {
                            Text("Telefon", style = MaterialTheme.typography.titleSmall)
                            detail.phoneNumbers.forEach { Text(it) }
                        }
                    }

                    when {
                        !detail.email.isNullOrEmpty() -> Column {
                            Text("E-mail", style = MaterialTheme.typography.titleSmall)
                            Text(detail.email)
                        }
                        // Bezpośredni `email` bywa pusty mimo scope other_emails, jeśli
                        // dana osoba ma ustawioną w USOS prywatność e-maila (np. wymaga
                        // captchy) — wtedy pokazujemy link do strony USOS zamiast nic.
                        detail.emailUrl != null -> Column {
                            Text("E-mail", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Pokaż na stronie USOS: ${detail.emailUrl}",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Text(
                "Nie udało się pobrać danych osoby. Odpowiedź serwera: ${state.error}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
