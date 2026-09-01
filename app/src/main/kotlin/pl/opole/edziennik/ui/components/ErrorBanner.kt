package pl.opole.edziennik.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.opole.edziennik.R

/**
 * Mały baner błędu — NIE zastępuje danych na ekranie, tylko pojawia się nad
 * nimi. Odróżnia to od pierwszej wersji ekranów, gdzie błąd całkowicie
 * przesłaniał ostatnio pokazane (skądinąd wciąż zachowane w ViewModelu)
 * dane, np. po nieudanym wymuszonym odświeżeniu bez internetu.
 *
 * Styl "z lewą kreską" jak `.error-banner`/`.flash` w style.css — pasek
 * koloru błędu z lewej, tło `--card-sub`, bez zaokrąglenia z tej strony.
 *
 * Celowo NIE pokazuje surowej treści błędu (np. "Unable to resolve host...")
 * — to szczegół techniczny bez znaczenia dla użytkownika telefonu; jeśli
 * kiedyś przyda się do debugowania, wystarczy zajrzeć do Logcata.
 */
@Composable
fun ErrorBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(
                MaterialTheme.colorScheme.errorContainer,
                RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp),
            ),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.error),
        )
        Row(
            modifier = Modifier.padding(start = 9.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Nie udało się odświeżyć.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 13.sp,
            )
        }
    }
}
