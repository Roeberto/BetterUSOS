package pl.opole.edziennik.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mały baner błędu — NIE zastępuje danych na ekranie, tylko pojawia się nad
 * nimi. Odróżnia to od pierwszej wersji ekranów, gdzie błąd całkowicie
 * przesłaniał ostatnio pokazane (skądinąd wciąż zachowane w ViewModelu)
 * dane, np. po nieudanym wymuszonym odświeżeniu bez internetu.
 *
 * Celowo NIE pokazuje surowej treści błędu (np. "Unable to resolve host...")
 * — to szczegół techniczny bez znaczenia dla użytkownika telefonu; jeśli
 * kiedyś przyda się do debugowania, wystarczy zajrzeć do Logcata.
 */
@Composable
fun ErrorBanner(modifier: Modifier = Modifier) {
    Text(
        "Nie udało się odświeżyć.",
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
            .padding(12.dp),
        color = MaterialTheme.colorScheme.onErrorContainer,
        fontSize = 13.sp,
    )
}
