package pl.opole.edziennik.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import pl.opole.edziennik.ui.theme.PillShape

/**
 * Kwadratowy przycisk-ikona z cienką obwódką — odpowiednik `.icon-btn` z
 * style.css (36×36, obwódka `--rule`, róg 2px). Zbudowany na `Box.clickable`,
 * NIE na `IconButton` — ten drugi wymusza minimalny rozmiar dotykowy 48dp
 * niezależnie od podanego `Modifier.size`, co robiło z tego zbyt duży
 * kwadrat nachodzący na sąsiedni tekst w pasku górnym.
 */
@Composable
fun AppIconButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant), PillShape)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
