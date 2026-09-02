package pl.opole.edziennik.data

private val diacritics = mapOf(
    'ą' to 'a', 'ć' to 'c', 'ę' to 'e', 'ł' to 'l', 'ń' to 'n',
    'ó' to 'o', 'ś' to 's', 'ź' to 'z', 'ż' to 'z',
)

private fun normalize(text: String): String =
    text.trim().lowercase().map { diacritics[it] ?: it }.joinToString("")

private val labels = mapOf(
    "wyklad" to "Wykład",
    "cwiczenia" to "Ćwiczenia",
    "laboratorium" to "Laboratorium",
    "projekt" to "Projekt",
    "seminarium" to "Seminarium",
    "konwersatorium" to "Konwersatorium",
    "lektorat" to "Lektorat",
    "egzamin" to "Egzamin",
    "zaliczenie" to "Zaliczenie",
)

private val abbreviations = mapOf(
    "wyklad" to "WYKŁ",
    "cwiczenia" to "ĆW",
    "laboratorium" to "LAB",
    "projekt" to "PROJ",
    "seminarium" to "SEM",
    "konwersatorium" to "KONW",
    "lektorat" to "LEKT",
    "egzamin" to "EGZ",
    "zaliczenie" to "ZAL",
)

data class ClassType(
    val displayName: String,
    val label: String,
    val abbreviation: String,
    val colorKey: String,
)

/**
 * Normalizuje dowolną nazwę formy zajęć (np. z `courses/classtypes_index` —
 * inny słownik niż `classtype_name` z planu, ale ta sama lista form) do
 * klucza koloru używanego przez `colorForType()`. Pozwala pokolorować formę
 * zajęć przy ocenie tym samym kolorem, co plakietkę w planie zajęć.
 */
fun classTypeColorKey(rawType: String): String {
    val key = normalize(rawType)
    return if (labels.containsKey(key)) key else "inne"
}

/**
 * Odpowiednik `split_class_type()` z aplikacji webowej — wyodrębnia formę
 * zajęć (wykład/ćwiczenia/...) z pola `classtype_name`, a w razie jego
 * braku z końcówki `name` (np. "... - Wykład"), i usuwa ją z wyświetlanej
 * nazwy, żeby się nie powtarzała.
 */
fun splitClassType(name: String, classtypeName: String?): ClassType {
    val nameSuffix = if (name.contains(" - ")) name.substringAfterLast(" - ") else null
    val rawType = classtypeName?.takeIf { it.isNotEmpty() } ?: nameSuffix ?: ""
    val key = normalize(rawType)

    val label = labels[key] ?: rawType
    val colorKey = if (labels.containsKey(key)) key else "inne"
    val abbreviation = abbreviations[key] ?: ""

    var displayName = name
    if (nameSuffix != null && normalize(nameSuffix) == key) {
        displayName = name.substringBeforeLast(" - ").trim()
    }

    return ClassType(displayName, label, abbreviation, colorKey)
}
