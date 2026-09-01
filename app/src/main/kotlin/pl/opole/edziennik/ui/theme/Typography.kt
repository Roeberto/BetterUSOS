package pl.opole.edziennik.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pl.opole.edziennik.R

/** Ta sama para fontów co w wersji webowej (`static/style.css`) — Source
 * Serif 4 do nagłówków, Inter do treści. Pliki dołączone na stałe w
 * `res/font/` (nie przez Google Play Services downloadable fonts — mniej
 * ruchomych części, działa od razu, bez zależności od Play Services). */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val SourceSerif4Family = FontFamily(
    Font(R.font.source_serif_regular, FontWeight.Normal),
    Font(R.font.source_serif_semibold, FontWeight.SemiBold),
)

val Typography = Typography(
    headlineSmall = TextStyle(fontFamily = SourceSerif4Family, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = SourceSerif4Family, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = SourceSerif4Family, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = SourceSerif4Family, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = InterFamily, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = InterFamily, fontSize = 12.sp),
)
