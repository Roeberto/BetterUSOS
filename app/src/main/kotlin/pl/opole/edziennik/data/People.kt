package pl.opole.edziennik.data

import org.json.JSONObject

/**
 * Kolory awatarów-inicjałów dla osób bez zdjęcia w USOS — stałe, wybierane
 * deterministycznie z imienia i nazwiska, żeby ta sama osoba miała zawsze
 * ten sam kolor. Odpowiednik `AVATAR_PALETTE` z app.py. ARGB (Int), nie
 * Compose Color — warstwa danych nie powinna zależeć od UI frameworka.
 */
val avatarPalette = listOf(
    0xFF3D6B8F.toInt(), 0xFF8A5A2F.toInt(), 0xFF3F7A5C.toInt(), 0xFF7A4A6B.toInt(),
    0xFF6B6B2F.toInt(), 0xFF7A3A4A.toInt(), 0xFF4A5A7A.toInt(), 0xFF5C7A3F.toInt(),
)

data class Person(
    val id: Int?,
    val name: String,
    val initials: String,
    val photoUrl: String?,
    val titles: String,
    val avatarColor: Int,
)

data class EmploymentPosition(val faculty: String, val position: String)

data class GroupDetail(
    val courseName: String,
    val classType: String,
    val groupNumber: Int?,
    val termId: String?,
    val lecturers: List<Person>,
    val participants: List<Person>,
)

data class PersonDetail(
    val person: Person,
    val employment: List<EmploymentPosition>,
    val officeHours: String,
    val room: String?,
    val phoneNumbers: List<String>,
    val email: String?,
    // Bezpośredni `email` bywa pusty mimo scope other_emails, jeśli dana
    // osoba ma ustawioną w USOS prywatność e-maila (np. wymaga captchy) —
    // wtedy pokazujemy link do strony USOS zamiast po prostu nic.
    val emailUrl: String?,
)

/**
 * Formatuje osobę (prowadzącego/uczestnika) zwróconą przez USOS API do
 * postaci gotowej do wyświetlenia — imię i nazwisko, tytuł, zdjęcie albo
 * awatar z inicjałami. Odpowiednik `format_person()` z app.py.
 */
fun formatPerson(p: JSONObject): Person {
    val first = p.optString("first_name", "").trim()
    val last = p.optString("last_name", "").trim()
    val name = "$first $last".trim().ifEmpty { "—" }
    val initials = (first.take(1) + last.take(1)).uppercase().ifEmpty { "?" }

    var photo: String? = null
    if (p.optBoolean("has_photo", false)) {
        val photoUrls = p.optJSONObject("photo_urls")
        if (photoUrls != null) {
            photo = photoUrls.optStringOrNull("100x100") ?: photoUrls.optStringOrNull("50x50")
            if (photo == null) {
                val keys = photoUrls.keys()
                if (keys.hasNext()) photo = photoUrls.optString(keys.next())
            }
        }
    }

    val titlesText = plText(p.opt("titles"))
    val colorIndex = name.sumOf { it.code } % avatarPalette.size

    return Person(
        id = p.optIntOrNull("id"),
        name = name,
        initials = initials,
        photoUrl = photo,
        titles = titlesText,
        avatarColor = avatarPalette[colorIndex],
    )
}
