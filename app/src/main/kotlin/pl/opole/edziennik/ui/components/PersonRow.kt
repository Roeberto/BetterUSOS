package pl.opole.edziennik.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import pl.opole.edziennik.data.Person

/**
 * Wiersz osoby (prowadzący/uczestnik) — zdjęcie z USOS albo awatar z
 * inicjałami w kolorze przypisanym po imieniu i nazwisku (patrz
 * `format_person()` w app.py). Klikalna, jeśli podano `onClick` — używane do
 * przejścia na stronę osoby (`/osoba/<user_id>` w wersji webowej).
 */
@Composable
fun PersonRow(person: Person, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (person.photoUrl != null) {
            AsyncImage(
                model = person.photoUrl,
                contentDescription = person.name,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(person.avatarColor)),
                contentAlignment = Alignment.Center,
            ) {
                Text(person.initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Column {
            Text(person.name, fontWeight = FontWeight.Medium)
            if (person.titles.isNotEmpty()) {
                Text(person.titles, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
