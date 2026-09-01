package pl.opole.edziennik.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.opole.edziennik.R
import pl.opole.edziennik.data.NotificationEvent
import pl.opole.edziennik.data.NotificationHistoryStore
import pl.opole.edziennik.ui.components.AppIconButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lista zdarzeń wykrytych przez `SyncWorker` w tle (zmiany w planie, nowe
 * oceny) — najnowsze na górze. Otwierana też automatycznie po kliknięciu w
 * powiadomienie systemowe (patrz `MainActivity.EXTRA_OPEN_NOTIFICATIONS`).
 */
@Composable
fun NotificationsScreen(cacheDir: File, navController: NavHostController) {
    val store = remember { NotificationHistoryStore(File(cacheDir, "notification_history")) }
    var events by remember { mutableStateOf<List<NotificationEvent>>(emptyList()) }

    LaunchedEffect(Unit) {
        events = withContext(Dispatchers.IO) { store.readAll() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Powiadomienia") },
                navigationIcon = {
                    Box(Modifier.padding(start = 4.dp)) {
                        AppIconButton(R.drawable.ic_back, "Wstecz") { navController.popBackStack() }
                    }
                },
            )
        },
    ) { padding ->
        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Brak powiadomień. Sprawdzamy plan i oceny co 12h w tle.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(events) { event ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val (iconRes, background) = if (event.type == "grade") {
                        R.drawable.ic_grades to MaterialTheme.colorScheme.secondary
                    } else {
                        R.drawable.ic_schedule_change to MaterialTheme.colorScheme.primary
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(background, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Column {
                        Text(event.message, fontWeight = FontWeight.Medium)
                        Text(
                            formatTimestamp(event.timestamp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("pl")).format(Date(millis))
