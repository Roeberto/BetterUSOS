package pl.opole.edziennik.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import pl.opole.edziennik.data.DayGroup
import pl.opole.edziennik.ui.theme.CardShape

/** Cały dzień planu w jednej obwódce: wycentrowany, wyróżniony kolorem pasek
 * z nazwą dnia ("Sobota 10 października") i pod nim karty zajęć. Wspólny dla
 * Pulpitu, Planu i strony osoby, żeby wszędzie wyglądał tak samo. */
@Composable
fun DaySection(day: DayGroup, navController: NavHostController, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.primary, CardShape),
    ) {
        Text(
            "${day.weekday} ${day.dateLabel}",
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            day.entries.forEach { entry ->
                SessionCard(
                    entry = entry,
                    onClick = sessionCardClickHandler(navController, entry.unitId, entry.groupNumber),
                )
            }
        }
    }
}
