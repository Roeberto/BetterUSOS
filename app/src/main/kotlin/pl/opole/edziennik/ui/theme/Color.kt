package pl.opole.edziennik.ui.theme

import androidx.compose.ui.graphics.Color

// Ta sama paleta co w static/style.css aplikacji webowej.
val Paper = Color(0xFFEEF0EA)
val PaperRaised = Color(0xFFF6F7F2)
val Ink = Color(0xFF20242A)
val InkMuted = Color(0xFF5C6259)
val Rule = Color(0xFFCFD0C3)
val Navy = Color(0xFF1F3A5C)
val NavyDark = Color(0xFF16293F)
val Oxblood = Color(0xFF7A3B34)
// Tło "podniesionej" karty (np. spód karty zajęć z salą, karta płatności) —
// stały krok jaśniejszy/ciemniejszy od Paper, żeby się od niego odcinał.
val CardSub = Color(0xFFE2E4DA)

val PaperDark = Color(0xFF1A1C18)
val InkDark = Color(0xFFE7E6DF)
val InkMutedDark = Color(0xFFA3A69B)
val RuleDark = Color(0xFF3A3D34)
val NavyAccentDark = Color(0xFF8FB4DD)
val OxbloodDark = Color(0xFFE0897A)
val CardSubDark = Color(0xFF24261F)

// Kolory form zajęć — te same wartości co --type-*-bg w style.css.
val classTypeColors = mapOf(
    "wyklad" to Color(0xFF2F5A86),
    "cwiczenia" to Color(0xFF8A6A1A),
    "laboratorium" to Color(0xFF1F7A68),
    "projekt" to Color(0xFF8A4A3F),
    "seminarium" to Color(0xFF6B3F78),
    "konwersatorium" to Color(0xFF7A5636),
    "lektorat" to Color(0xFF47681F),
    "egzamin" to Color(0xFFA13A3A),
    "zaliczenie" to Color(0xFFA13A3A),
    "inne" to Color(0xFF55584C),
)

fun colorForType(key: String): Color = classTypeColors[key] ?: classTypeColors.getValue("inne")
