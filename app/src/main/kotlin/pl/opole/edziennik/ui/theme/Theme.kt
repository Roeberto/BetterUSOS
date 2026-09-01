package pl.opole.edziennik.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
    error = Oxblood,
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
    error = OxbloodDark,
)

/** Automatyczny tryb ciemny wg preferencji systemu — bez ręcznego
 * przełącznika (w przeciwieństwie do wersji webowej, gdzie trzeba było
 * to dopisać ręcznie; Compose/Material3 dostaje to za darmo). */
@Composable
fun EdziennikTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
