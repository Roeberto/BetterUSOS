package pl.opole.edziennik.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** "Ledgerowa" estetyka wersji webowej używa ostrych, prawie prostych rogów
 * — 2px dla przycisków/plakietek, 3px dla kart (`border-radius` w
 * style.css) — celowo NIE zaokrąglonych kart domyślnych dla Material3
 * (8dp+). */
val PillShape = RoundedCornerShape(2.dp)
val CardShape = RoundedCornerShape(3.dp)
