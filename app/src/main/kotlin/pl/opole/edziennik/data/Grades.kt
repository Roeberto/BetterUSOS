package pl.opole.edziennik.data

import org.json.JSONObject

/**
 * Pojedyncza ocena, już wyciągnięta z zagnieżdżonej struktury `grades/terms2`
 * — patrz komentarz przy `flattenTermsGrades()` niżej i `_grade_entry()` /
 * `flatten_terms2_grades()` w app.py po pełne wyjaśnienie kształtu danych,
 * który wymagał kilku rund debugowania na żywych danych po stronie webowej.
 *
 * `courseName`/`issuerName` są `var`, bo dociągamy je dopiero po spłaszczeniu
 * (zbiorczym zapytaniem o wszystkie ID naraz — patrz `fetchAllGrades()` w
 * UsosRepository), żeby nie robić osobnego zapytania na każdą ocenę.
 */
data class RawGrade(
    val term: String,
    val courseId: String,
    val unitId: String?,
    val examId: Int?,
    val valueSymbol: String?,
    val valueDescription: String,
    val countsIntoAverage: Boolean,
    val dateModified: String,
    val modificationAuthorId: String?,
    val modificationAuthorName: String?,
    var courseName: String = "",
    var issuerName: String = "",
)

data class GradeEntry(
    val valueSymbol: String?,
    val valueDescription: String,
    val countsIntoAverage: Boolean,
    val dateModified: String,
    val issuerName: String,
    val examId: Int?,
)

data class CourseGrades(
    val courseName: String,
    val courseId: String?,
    val entries: List<GradeEntry>,
    val ectsPoints: Double?,
)

data class TermSection(
    val term: String?,
    val courses: List<CourseGrades>,
)

/**
 * Odpowiednik `flatten_terms2_grades()` z app.py. Rzeczywisty (potwierdzony
 * na żywych danych) kształt odpowiedzi `grades/terms2` to:
 * `{term_id: {course_id: {"course_units_grades": {unit_id: [{numer_sesji:
 * ocena_albo_null}]}, "course_grades": [...]}}}`. `course_units_grades`
 * grupuje oceny per jednostka zajęciowa (np. osobno wykład i laboratorium) i
 * numer sesji egzaminacyjnej (podejście pierwsze/drugie); wartość `null`
 * oznacza brak oceny w danej sesji, nie błąd. `course_grades` to (rzadziej
 * używana) płaska lista ocen dla całego przedmiotu, niezwiązanych z
 * konkretną jednostką.
 */
fun flattenTermsGrades(data: JSONObject): List<RawGrade> {
    val grades = mutableListOf<RawGrade>()

    val termKeys = data.keys()
    while (termKeys.hasNext()) {
        val termId = termKeys.next()
        val courses = data.optJSONObject(termId) ?: continue

        val courseKeys = courses.keys()
        while (courseKeys.hasNext()) {
            val courseId = courseKeys.next()
            val obj = courses.optJSONObject(courseId) ?: continue

            val units = obj.optJSONObject("course_units_grades")
            val courseGrades = obj.optJSONArray("course_grades")
            if (units == null && courseGrades == null) continue

            if (courseGrades != null) {
                for (i in 0 until courseGrades.length()) {
                    val g = courseGrades.optJSONObject(i) ?: continue
                    if (g.isNull("value_symbol")) continue
                    grades.add(toRawGrade(termId, courseId, null, g))
                }
            }

            if (units != null) {
                val unitKeys = units.keys()
                while (unitKeys.hasNext()) {
                    val unitId = unitKeys.next()
                    val attempts = units.optJSONArray(unitId) ?: continue
                    for (i in 0 until attempts.length()) {
                        val sessions = attempts.optJSONObject(i) ?: continue
                        val sessionKeys = sessions.keys()
                        while (sessionKeys.hasNext()) {
                            val g = sessions.optJSONObject(sessionKeys.next()) ?: continue
                            if (g.isNull("value_symbol")) continue
                            grades.add(toRawGrade(termId, courseId, unitId, g))
                        }
                    }
                }
            }
        }
    }
    return grades
}

private fun toRawGrade(termId: String, courseId: String, unitId: String?, g: JSONObject): RawGrade {
    // `modification_author` bywa zwrócone jako gotowy obiekt osoby
    // (first_name/last_name wprost), a nie samo ID, jak sugerowała
    // dokumentacja metody `grade` — obsługujemy oba warianty (patrz
    // `grades_page()` w app.py).
    val author = g.opt("modification_author")
    val authorId: String?
    val authorName: String?
    if (author is JSONObject) {
        authorId = null
        authorName = "${author.optString("first_name", "")} ${author.optString("last_name", "")}".trim()
    } else {
        authorId = author?.toString()
        authorName = null
    }

    return RawGrade(
        term = termId,
        courseId = courseId,
        unitId = unitId,
        examId = g.optIntOrNull("exam_id"),
        valueSymbol = g.optStringOrNull("value_symbol"),
        valueDescription = plText(g.opt("value_description")),
        countsIntoAverage = g.optBoolean("counts_into_average", false),
        dateModified = g.optString("date_modified", "").take(16),
        modificationAuthorId = authorId,
        modificationAuthorName = authorName,
    )
}

private val termIdYearRegex = Regex("^(\\d{4})")

/**
 * Wyciąga (rok, ranga_semestru) z ID semestru, żeby dało się je posortować
 * chronologicznie — obserwowany format to "RRRR/RRz" albo "RRRR/RRRRl" (z =
 * zimowy, l = letni). Odpowiednik `term_sort_key()` z app.py.
 */
fun termSortKey(termId: String?): Pair<Int, Int> {
    if (termId.isNullOrEmpty()) return 0 to 2
    val year = termIdYearRegex.find(termId)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    // W obrębie jednego roku semestr zimowy (z) jest wcześniej, letni (l)
    // później — l dostaje WYŻSZĄ rangę, żeby przy sortowaniu malejąco wyszedł
    // jako nowszy.
    val seasonRank = when (termId.last().lowercaseChar()) {
        'z' -> 0
        'l' -> 1
        else -> -1
    }
    return year to seasonRank
}

/**
 * Grupuje (już spłaszczone) oceny po semestrze, a w ramach semestru po
 * przedmiocie. Semestry są posortowane od najnowszego, a oceny w ramach
 * przedmiotu — od najświeższej daty wprowadzenia. Odpowiednik
 * `group_grades_by_term()` z app.py.
 */
fun groupGradesByTerm(grades: List<RawGrade>, ectsPoints: Map<Pair<String, String>, Double>): List<TermSection> {
    data class Key(val term: String, val courseName: String)

    val courseOrder = mutableListOf<Key>()
    val entriesByKey = LinkedHashMap<Key, MutableList<RawGrade>>()
    val courseIdByKey = mutableMapOf<Key, String>()

    for (g in grades) {
        val key = Key(g.term, g.courseName)
        if (key !in entriesByKey) {
            entriesByKey[key] = mutableListOf()
            courseOrder.add(key)
            courseIdByKey[key] = g.courseId
        }
        entriesByKey.getValue(key).add(g)
    }

    val courseGradesByKey = entriesByKey.mapValues { (key, rawEntries) ->
        val courseId = courseIdByKey[key]
        CourseGrades(
            courseName = key.courseName,
            courseId = courseId,
            entries = rawEntries.sortedByDescending { it.dateModified }.map {
                GradeEntry(
                    valueSymbol = it.valueSymbol,
                    valueDescription = it.valueDescription,
                    countsIntoAverage = it.countsIntoAverage,
                    dateModified = it.dateModified,
                    issuerName = it.issuerName,
                    examId = it.examId,
                )
            },
            ectsPoints = courseId?.let { ectsPoints[key.term to it] },
        )
    }

    val byTerm = LinkedHashMap<String, MutableList<CourseGrades>>()
    for (key in courseOrder) {
        byTerm.getOrPut(key.term) { mutableListOf() }.add(courseGradesByKey.getValue(key))
    }

    val termOrder = byTerm.keys.sortedWith(
        compareByDescending<String> { termSortKey(it).first }.thenByDescending { termSortKey(it).second },
    )
    return termOrder.map { TermSection(term = it, courses = byTerm.getValue(it)) }
}

/** Konwertuje symbol oceny USOS (np. "4,5") na liczbę, albo `null`, jeśli to
 * nie jest ocena liczbowa (np. "zal", "nzal"). Odpowiednik `parse_grade_value()`. */
fun parseGradeValue(symbol: String?): Double? =
    symbol?.replace(",", ".")?.toDoubleOrNull()

/**
 * Liczy średnią ważoną punktami ECTS z ocen liczących się do średniej
 * (`counts_into_average`). Jeśli przedmiot ma kilka takich ocen naraz (np.
 * osobno wykład i laboratorium), najpierw uśredniamy je w ramach przedmiotu,
 * a dopiero tę jedną wartość ważymy punktami ECTS całego przedmiotu — inaczej
 * wielokomponentowe przedmioty miałyby nieproporcjonalnie dużą wagę względem
 * jednoskładnikowych. Odpowiednik `compute_weighted_average()` z app.py
 * (bez `breakdown` — ta diagnostyczna lista została usunięta również w
 * wersji webowej, na wyraźną prośbę).
 */
fun computeWeightedAverage(termSections: List<TermSection>): Double? {
    var weightedSum = 0.0
    var weightSum = 0.0

    for (section in termSections) {
        for (course in section.courses) {
            val ects = course.ectsPoints
            if (ects == null || ects == 0.0) continue

            val values = course.entries
                .filter { it.countsIntoAverage }
                .mapNotNull { parseGradeValue(it.valueSymbol) }
            if (values.isEmpty()) continue

            val courseAvg = values.sum() / values.size
            weightedSum += courseAvg * ects
            weightSum += ects
        }
    }

    if (weightSum == 0.0) return null
    return kotlin.math.round(weightedSum / weightSum * 100) / 100
}
