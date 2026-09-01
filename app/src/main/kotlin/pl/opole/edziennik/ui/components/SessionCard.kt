package pl.opole.edziennik.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import pl.opole.edziennik.data.SessionEntry
import pl.opole.edziennik.data.hm
import pl.opole.edziennik.ui.theme.colorForType

/** Zwraca handler nawigacji do strony grupy (`/grupa/<unit_id>/<group_number>`
 * w wersji webowej) dla `SessionCard.onClick` — `null`, jeśli wpis planu nie
 * ma tych ID (np. egzamin bez przypisanej grupy), więc karta zostaje
 * nieklikalna zamiast prowadzić donikąd. */
fun sessionCardClickHandler(navController: NavHostController, unitId: Int?, groupNumber: Int?): (() -> Unit)? =
    if (unitId != null && groupNumber != null) {
        { navController.navigate("group/$unitId/$groupNumber") }
    } else {
        null
    }

/** Odpowiednik `.session-card` z static/style.css — kolorowy nagłówek wg
 * formy zajęć + przyciemniony pasek z salą i prowadzącym pod spodem.
 * Klikalna, jeśli podano `onClick` (patrz strona grupy: `/grupa/<unit_id>/
 * <group_number>` w app.py) — dostępne tylko, gdy wpis planu ma
 * `unitId`/`groupNumber`. */
@Composable
fun SessionCard(entry: SessionEntry, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val accent = colorForType(entry.colorKey)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, accent, RoundedCornerShape(4.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(accent)
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${hm(entry.startTime)}–${hm(entry.endTime)}", color = Color.White, fontSize = 13.sp)
                if (entry.typeAbbr.isNotEmpty()) {
                    Text(entry.typeAbbr, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(entry.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(12.dp),
        ) {
            if (entry.lecturersDisplay.isNotEmpty()) {
                Text(entry.lecturersDisplay, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            if (entry.buildingName.isNotEmpty()) {
                Text(entry.buildingName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.roomNumber.isNotEmpty()) {
                Text(entry.roomNumber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
