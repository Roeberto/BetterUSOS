package pl.opole.edziennik.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import pl.opole.edziennik.data.DayGroup
import pl.opole.edziennik.data.SessionEntry
import pl.opole.edziennik.ui.theme.CardShape
import java.time.Duration
import java.time.LocalDateTime

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
            day.entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    breakMinutes(day.entries[index - 1], entry)?.let { BreakBlock(it) }
                }
                SessionCard(
                    entry = entry,
                    onClick = sessionCardClickHandler(navController, entry.unitId, entry.groupNumber),
                )
            }
        }
    }
}

/** Przerwa w minutach między końcem poprzednich a początkiem następnych zajęć
 * — `null`, gdy zajęcia nachodzą na siebie / stykają się albo czasu nie da
 * się sparsować (wtedy po prostu nie rysujemy bloczka). */
private fun breakMinutes(previous: SessionEntry, next: SessionEntry): Long? =
    runCatching {
        val end = LocalDateTime.parse(previous.endTime.replace(' ', 'T'))
        val start = LocalDateTime.parse(next.startTime.replace(' ', 'T'))
        Duration.between(end, start).toMinutes()
    }.getOrNull()?.takeIf { it > 0 }

private fun formatBreak(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

/** Bloczek przerwy między zajęciami — wysokość rośnie z długością przerwy
 * (od 30 dp dla krótkiej do 110 dp dla kilkugodzinnej), żeby na planie od
 * razu było widać "okienka". */
@Composable
private fun BreakBlock(minutes: Long) {
    val height = (30 + minutes / 15 * 6).coerceAtMost(110).toInt().dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceContainer, CardShape)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Przerwa ${formatBreak(minutes)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
