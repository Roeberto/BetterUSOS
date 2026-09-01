package pl.opole.edziennik.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = PaperRaised,
    secondary = Oxblood,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Rule,
    onSurfaceVariant = InkMuted,
    // Tło "podniesionej" karty (pasek sali pod zajęciami, karta płatności) —
    // odpowiednik --card-sub-bg z style.css. surfaceVariant zostaje kolorem
    // linii/obwódek (--rule), to jest osobna rola.
    surfaceContainer = CardSub,
    error = Oxblood,
    errorContainer = CardSub,
    onErrorContainer = Ink,
)

private val DarkColors = darkColorScheme(
    primary = NavyAccentDark,
    onPrimary = PaperDark,
    secondary = OxbloodDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = RuleDark,
    onSurfaceVariant = InkMutedDark,
    surfaceContainer = CardSubDark,
    error = OxbloodDark,
    errorContainer = CardSubDark,
    onErrorContainer = InkDark,
)

/** Automatyczny tryb ciemny wg preferencji systemu — bez ręcznego
 * przełącznika (w przeciwieństwie do wersji webowej, gdzie trzeba było
 * to dopisać ręcznie; Compose/Material3 dostaje to za darmo). */
@Composable
fun EdziennikTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    // Pasek stanu (godzina/bateria) w kolorze tła appki, zamiast domyślnego
    // czarnego/białego paska systemowego, który wybijał się z reszty ekranu.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}
