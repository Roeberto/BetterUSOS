package pl.opole.edziennik.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import pl.opole.edziennik.ui.theme.PillShape

/**
 * Kwadratowy przycisk-ikona z cienką obwódką — odpowiednik `.icon-btn` z
 * style.css (36×36, obwódka `--rule`, róg 2px). Używany w paskach górnych
 * zamiast emoji jako "ikon" (⟳, 🔔, ←) — te renderowały się małe i
 * niespójnie między telefonami.
 */
@Composable
fun AppIconButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant), PillShape),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
    }
}
