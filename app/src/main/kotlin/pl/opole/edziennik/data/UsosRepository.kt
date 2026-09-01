package pl.opole.edziennik.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pl.opole.edziennik.network.UsosApiClient
import pl.opole.edziennik.network.UsosResponse
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class Payment(
    val typeLabel: String,
    val description: String?,
    val amount: Double,
    val currency: String,
    val paymentDeadline: String?,
)

data class DistributionBar(val symbol: String, val percent: Double)

private val paymentTypeLabels = mapOf(
    "dormitory" to "Akademik",
    "tuition_fee" to "Czesne",
    "deposit" to "Kaucja",
    "interest" to "Odsetki",
    "retake_of_study_period" to "Powtarzanie semestru",
    "conditional_promotion" to "Warunkowe zaliczenie",
    "course_registration" to "Rejestracja na zajęcia",
    "course_retake" to "Powtarzanie przedmiotu",
    "credit_point" to "Punkt kredytowy",
    "token" to "Żeton",
    "others" to "Inne",
)

private val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Odpowiednik funkcji `fetch_*` z app.py — pobiera i parsuje dane z USOS API.
 *
 * Każda publiczna metoda `fetch*` przyjmuje `forceRefresh: Boolean = false`:
 * `false` (normalne wejście na ekran) zwraca dane z trwałego cache na dysku,
 * jeśli już tam są, i sięga do sieci tylko gdy ich brak; `true` (przycisk
 * odświeżania) zawsze pyta USOS i nadpisuje cache świeżym wynikiem. Cache
 * nie ma czasu wygasania — trwa, dopóki ktoś ręcznie nie odświeży.
 */
class UsosRepository(private val client: UsosApiClient, cacheDir: File) {

    private val cache = DiskCache(File(cacheDir, "api_cache"))

    /** Jedyne miejsce, które faktycznie woła sieć — wszystkie metody niżej
     * przechodzą przez to zamiast wołać `client.get()` bezpośrednio, żeby
     * cache'owanie działało jednolicie dla każdego zapytania. */
    private fun get(method: String, params: Map<String, String> = emptyMap(), forceRefresh: Boolean = false): UsosResponse {
        val key = cacheKeyFor(method, params)
        if (!forceRefresh) {
            cache.read(key)?.let { return UsosResponse(isSuccessful = true, body = it) }
        }
        val resp = client.get(method, params)
        if (resp.isSuccessful) cache.write(key, resp.body)
        return resp
    }

    /**
     * Pobiera plan z zakresu dat, odpytując `tt/user` w kawałkach po
     * (najwyżej) 7 dni — to limit tej metody w tej instalacji USOS
     * (patrz `fetch_full_schedule` w app.py).
     */
    suspend fun fetchSchedule(start: LocalDate, end: LocalDate, forceRefresh: Boolean = false): Result<List<DayGroup>> =
        withContext(Dispatchers.IO) {
            try {
                val entries = mutableListOf<JSONObject>()
                var cursor = start
                while (!cursor.isAfter(end)) {
                    val remaining = ChronoUnit.DAYS.between(cursor, end).toInt() + 1
                    val days = minOf(7, remaining)

                    val resp = get(
                        "tt/user",
                        mapOf(
                            "start" to cursor.format(isoDate),
                            "days" to days.toString(),
                            "fields" to ("start_time|end_time|name|building_name|room_number" +
                                "|classtype_name|unit_id|group_number|lecturer_ids"),
                        ),
                        forceRefresh,
                    )
                    if (!resp.isSuccessful) return@withContext Result.failure(Exception(resp.body))

                    val chunk = JSONArray(resp.body)
                    for (i in 0 until chunk.length()) entries.add(chunk.getJSONObject(i))
                    cursor = cursor.plusDays(days.toLong())
                }
                Result.success(groupByDay(entries, forceRefresh))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Odpowiednik `group_by_day()` + `attach_lecturer_names()` z app.py. */
    private suspend fun groupByDay(entries: List<JSONObject>, forceRefresh: Boolean): List<DayGroup> {
        val lecturerIds = mutableSetOf<String>()
        entries.forEach { entry ->
            entry.optJSONArray("lecturer_ids")?.let { arr ->
                for (i in 0 until arr.length()) lecturerIds.add(arr.get(i).toString())
            }
        }
        val lecturerNames = if (lecturerIds.isNotEmpty()) resolveUserNames(lecturerIds, forceRefresh) else emptyMap()

        val byDate = sortedMapOf<LocalDate, MutableList<SessionEntry>>()
        for (entry in entries) {
            val startTime = entry.optString("start_time", "")
            val date = runCatching { LocalDate.parse(startTime.take(10)) }.getOrNull() ?: continue

            val name = plText(entry.opt("name"))
            val classtype = plText(entry.opt("classtype_name")).ifEmpty { null }
            val type = splitClassType(name, classtype)

            val ids = entry.optJSONArray("lecturer_ids")
            val lecturers = if (ids != null) {
                (0 until ids.length()).mapNotNull { lecturerNames[ids.get(it).toString()] }
            } else {
                emptyList()
            }

            val session = SessionEntry(
                startTime = startTime,
                endTime = entry.optString("end_time", ""),
                displayName = type.displayName,
                typeLabel = type.label,
                typeAbbr = type.abbreviation,
                colorKey = type.colorKey,
                buildingName = plText(entry.opt("building_name")),
                roomNumber = entry.optString("room_number", ""),
                lecturersDisplay = lecturers.joinToString(", "),
                unitId = entry.optIntOrNull("unit_id"),
                groupNumber = entry.optIntOrNull("group_number"),
            )
            byDate.getOrPut(date) { mutableListOf() }.add(session)
        }

        return byDate.map { (date, sessions) ->
            DayGroup(
                date = date,
                weekday = weekdayLabel(date),
                dateLabel = dayLabel(date),
                entries = sessions.sortedBy { it.startTime },
            )
        }
    }

    /** Jedno zbiorcze zapytanie do `users/users` dla wszystkich ID naraz —
     * nie osobne zapytanie na każde zajęcia (patrz `resolve_user_names` w app.py). */
    private suspend fun resolveUserNames(ids: Set<String>, forceRefresh: Boolean = false): Map<String, String> =
        withContext(Dispatchers.IO) {
            val resp = get(
                "users/users",
                mapOf("user_ids" to ids.joinToString("|"), "fields" to "first_name|last_name"),
                forceRefresh,
            )
            if (!resp.isSuccessful) return@withContext emptyMap()

            val obj = JSONObject(resp.body)
            val result = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val u = obj.optJSONObject(id) ?: continue
                result[id] = "${u.optString("first_name")} ${u.optString("last_name")}".trim()
            }
            result
        }

    /**
     * Odpowiednik `fetch_outstanding_payments()` z app.py.
     *
     * UWAGA: `saldo_amount` NIE oznacza "ile zostało do zapłaty" — na
     * realnych danych okazało się, że dla płatności ze `state == "paid"`
     * `saldo_amount` jest równe `total_amount` (to po prostu kwota tej
     * płatności, nie pozostały dług). Właściwym kryterium jest pole
     * `state` — pomijamy wszystko z `state == "paid"`.
     */
    suspend fun fetchOutstandingPayments(forceRefresh: Boolean = false): Result<Pair<List<Payment>, Double>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = get(
                    "payments/user_payments",
                    mapOf("fields" to "type|description|saldo_amount|total_amount|state|currency|payment_deadline"),
                    forceRefresh,
                )
                if (!resp.isSuccessful) return@withContext Result.failure(Exception(resp.body))

                val arr = JSONArray(resp.body)
                val payments = mutableListOf<Payment>()
                var total = 0.0

                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)

                    // `type` bywa zwrócone jako obiekt (np. {"id": "tuition_fee", ...})
                    // albo LangDict, a nie sam string enum — ten sam wzorzec, co
                    // przy `modification_author` w ocenach po stronie webowej.
                    val rawType = p.opt("type")
                    val typeKey = if (rawType is JSONObject) {
                        rawType.optStringOrNull("id") ?: rawType.optStringOrNull("key")
                    } else {
                        rawType?.toString()
                    }
                    val typeLabel = paymentTypeLabels[typeKey]
                        ?: (if (rawType is JSONObject) plText(rawType).ifEmpty { null } else null)
                        ?: typeKey
                        ?: "Płatność"

                    val rawState = p.opt("state")
                    val state = if (rawState is JSONObject) rawState.optStringOrNull("id") else rawState?.toString()
                    if (state == "paid") continue

                    val saldo = p.optDouble("saldo_amount", Double.NaN)
                    val totalAmount = p.optDouble("total_amount", Double.NaN)
                    val amount = when {
                        !saldo.isNaN() && saldo != 0.0 -> saldo
                        !totalAmount.isNaN() -> totalAmount
                        else -> continue
                    }
                    total += amount

                    payments.add(
                        Payment(
                            typeLabel = typeLabel,
                            description = plText(p.opt("description")).ifEmpty { null },
                            amount = amount,
                            currency = p.optString("currency", "PLN"),
                            paymentDeadline = p.optStringOrNull("payment_deadline"),
                        )
                    )
                }

                payments.sortBy { it.paymentDeadline ?: "" }
                Result.success(payments to total)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Pobiera wszystkie oceny użytkownika przez `grades/terms2`.
     *
     * Ta metoda wymaga jawnej listy `term_ids`, więc najpierw pobieramy
     * wszystkie semestry zdefiniowane w tej instalacji USOS przez
     * `terms/terms_index`. Odpowiednik `fetch_all_grades()` z app.py.
     */
    suspend fun fetchAllGrades(forceRefresh: Boolean = false): Result<List<RawGrade>> = withContext(Dispatchers.IO) {
        try {
            val termsResp = get("terms/terms_index", mapOf("fields" to "id"), forceRefresh)
            if (!termsResp.isSuccessful) return@withContext Result.failure(Exception(termsResp.body))

            val termsArr = JSONArray(termsResp.body)
            val termIds = mutableListOf<String>()
            for (i in 0 until termsArr.length()) {
                val t = termsArr.optJSONObject(i) ?: continue
                termIds.add(t.optStringOrNull("id") ?: continue)
            }
            if (termIds.isEmpty()) return@withContext Result.success(emptyList())

            // UWAGA: "course" i "grades" to NIEPRAWIDŁOWE nazwy pól dla
            // obiektu oceny (walidowane wg schematu metody `grade`) —
            // prawdziwy kształt to `course_units_grades`/`course_grades`,
            // zwracane zawsze, niezależnie od `fields` (patrz
            // flattenTermsGrades). Tu prosimy tylko o dodatkowe pola SAMEJ
            // oceny, potwierdzone w dokumentacji metody `grade`.
            val gradesResp = get(
                "grades/terms2",
                mapOf(
                    "term_ids" to termIds.joinToString("|"),
                    "fields" to (
                        "value_symbol|value_description|counts_into_average" +
                            "|date_modified|modification_author|exam_id"
                        ),
                ),
                forceRefresh,
            )
            if (!gradesResp.isSuccessful) return@withContext Result.failure(Exception(gradesResp.body))

            val grades = flattenTermsGrades(JSONObject(gradesResp.body))

            val courseIds = grades.map { it.courseId }.toSet()
            val courseNames = resolveCourseNames(courseIds, forceRefresh)
            grades.forEach { it.courseName = courseNames[it.courseId] ?: it.courseId }

            val authorIds = grades.mapNotNull { it.modificationAuthorId }.toSet()
            val namesByAuthorId = if (authorIds.isNotEmpty()) resolveUserNames(authorIds, forceRefresh) else emptyMap()
            grades.forEach { g ->
                g.issuerName = g.modificationAuthorName
                    ?: g.modificationAuthorId?.let { namesByAuthorId[it] }
                    ?: ""
            }

            // Forma zajęć (wykład/ćwiczenia/...) — grades/terms2 daje tylko
            // opaque unit_id, więc trzeba dwóch dodatkowych zapytań:
            // unit_id -> classtype_id, potem classtype_id -> nazwa.
            val unitIds = grades.mapNotNull { it.unitId }.toSet()
            if (unitIds.isNotEmpty()) {
                val classTypeIdByUnit = resolveUnitClassTypeIds(unitIds, forceRefresh)
                val classTypeNames = fetchClassTypeNames(forceRefresh)
                grades.forEach { g ->
                    val classTypeId = g.unitId?.let { classTypeIdByUnit[it] }
                    g.classType = classTypeId?.let { classTypeNames[it] } ?: ""
                }
            }

            Result.success(grades)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Jedno zbiorcze zapytanie do `courses/units` — mapuje `unit_id` na
     * `classtype_id` (opaque kod formy zajęć, np. "lecture", "ĆWICZ207" —
     * jeszcze nie nazwę, tę trzeba osobno z `fetchClassTypeNames()`). */
    private suspend fun resolveUnitClassTypeIds(unitIds: Set<String>, forceRefresh: Boolean = false): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (unitIds.isEmpty()) return@withContext emptyMap()
            val resp = get(
                "courses/units",
                mapOf("unit_ids" to unitIds.joinToString("|"), "fields" to "classtype_id"),
                forceRefresh,
            )
            if (!resp.isSuccessful) return@withContext emptyMap()

            val obj = JSONObject(resp.body)
            val result = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val u = obj.optJSONObject(id) ?: continue
                u.optStringOrNull("classtype_id")?.let { result[id] = it }
            }
            result
        }

    /** Słownik WSZYSTKICH form zajęć w tej instalacji USOS (classtype_id ->
     * nazwa po polsku) — `courses/classtypes_index`, metoda publiczna
     * (bez wymaganej autoryzacji), ten sam trik co przy `examrep/exam` dla
     * rozkładu ocen. Krótka, stała lista — jedno zapytanie starcza na
     * wszystkie oceny naraz. */
    private suspend fun fetchClassTypeNames(forceRefresh: Boolean = false): Map<String, String> =
        withContext(Dispatchers.IO) {
            val resp = get("courses/classtypes_index", mapOf("fields" to "id|name"), forceRefresh)
            if (!resp.isSuccessful) return@withContext emptyMap()

            val obj = JSONObject(resp.body)
            val result = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val c = obj.optJSONObject(id) ?: continue
                result[id] = plText(c.opt("name"))
            }
            result
        }

    /** Jedno zbiorcze zapytanie do `courses/courses` — odpowiednik
     * `resolve_course_names()` z app.py. */
    private suspend fun resolveCourseNames(courseIds: Set<String>, forceRefresh: Boolean = false): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (courseIds.isEmpty()) return@withContext emptyMap()
            val resp = get(
                "courses/courses",
                mapOf("course_ids" to courseIds.joinToString("|"), "fields" to "id|name"),
                forceRefresh,
            )
            if (!resp.isSuccessful) return@withContext emptyMap()

            val obj = JSONObject(resp.body)
            val result = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val c = obj.optJSONObject(id) ?: continue
                result[id] = plText(c.opt("name"))
            }
            result
        }

    /**
     * Pobiera punkty ECTS dla wszystkich przedmiotów użytkownika przez
     * `courses/user_ects_points` (bez wymaganych parametrów poza
     * autoryzacją). Zwraca {(term_id, course_id): punkty} — pomijając
     * wpisy `null` (USOS zwraca null, jeśli nie da się wyliczyć ECTS dla
     * danego przedmiotu). Odpowiednik `fetch_ects_points()` z app.py.
     */
    suspend fun fetchEctsPoints(forceRefresh: Boolean = false): Map<Pair<String, String>, Double> = withContext(Dispatchers.IO) {
        val resp = get("courses/user_ects_points", emptyMap(), forceRefresh)
        if (!resp.isSuccessful) return@withContext emptyMap()

        val data = JSONObject(resp.body)
        val result = mutableMapOf<Pair<String, String>, Double>()
        val termKeys = data.keys()
        while (termKeys.hasNext()) {
            val termId = termKeys.next()
            val courses = data.optJSONObject(termId) ?: continue
            val courseKeys = courses.keys()
            while (courseKeys.hasNext()) {
                val courseId = courseKeys.next()
                if (courses.isNull(courseId)) continue
                val ects = courses.opt(courseId)?.toString()?.replace(",", ".")?.toDoubleOrNull() ?: continue
                result[termId to courseId] = ects
            }
        }
        result
    }

    /** Zwraca ID zalogowanego użytkownika w USOS — potrzebne tylko po to,
     * żeby przy liczeniu uczestników grupy odjąć samego siebie z listy.
     * Odpowiednik `current_user_id()` z app.py (tu bez osobnego
     * cache'owania w pamięci — sam plik na dysku wystarcza). */
    private suspend fun currentUserId(forceRefresh: Boolean = false): Int? = withContext(Dispatchers.IO) {
        val resp = get("users/user", mapOf("fields" to "id"), forceRefresh)
        if (!resp.isSuccessful) return@withContext null
        JSONObject(resp.body).optIntOrNull("id")
    }

    /** Odpowiednik trasy `/grupa/<unit_id>/<group_number>` z app.py. */
    suspend fun fetchGroupDetail(unitId: Int, groupNumber: Int, forceRefresh: Boolean = false): Result<GroupDetail> =
        withContext(Dispatchers.IO) {
            try {
                val resp = get(
                    "groups/group",
                    mapOf(
                        "course_unit_id" to unitId.toString(),
                        "group_number" to groupNumber.toString(),
                        "fields" to "course_name|group_number|class_type|term_id|lecturers|participants",
                    ),
                    forceRefresh,
                )
                if (!resp.isSuccessful) return@withContext Result.failure(Exception(resp.body))

                val data = JSONObject(resp.body)
                val myId = currentUserId(forceRefresh)

                val lecturers = mutableListOf<Person>()
                data.optJSONArray("lecturers")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { lecturers.add(formatPerson(it)) }
                    }
                }

                val participants = mutableListOf<Person>()
                data.optJSONArray("participants")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val p = arr.optJSONObject(i) ?: continue
                        if (p.optIntOrNull("id") == myId) continue
                        participants.add(formatPerson(p))
                    }
                }

                Result.success(
                    GroupDetail(
                        courseName = plText(data.opt("course_name")),
                        classType = plText(data.opt("class_type")),
                        groupNumber = data.optIntOrNull("group_number"),
                        termId = data.optStringOrNull("term_id"),
                        lecturers = lecturers,
                        participants = participants,
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Odpowiednik trasy `/osoba/<user_id>` z app.py (na razie głównie
     * przydatne dla prowadzących: miejsce zatrudnienia, dyżur, kontakt). */
    suspend fun fetchPersonDetail(userId: Int, forceRefresh: Boolean = false): Result<PersonDetail> =
        withContext(Dispatchers.IO) {
            try {
                val resp = get(
                    "users/user",
                    mapOf(
                        "user_id" to userId.toString(),
                        "fields" to (
                            "id|first_name|last_name|titles|has_photo|photo_urls" +
                                "|employment_positions|office_hours|phone_numbers|room" +
                                "|email|has_email|email_url"
                            ),
                    ),
                    forceRefresh,
                )
                if (!resp.isSuccessful) return@withContext Result.failure(Exception(resp.body))

                val data = JSONObject(resp.body)

                val employment = mutableListOf<EmploymentPosition>()
                data.optJSONArray("employment_positions")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val pos = arr.optJSONObject(i) ?: continue
                        val faculty = pos.optJSONObject("faculty")?.opt("name")
                        val position = pos.optJSONObject("position")?.opt("name")
                        employment.add(EmploymentPosition(plText(faculty), plText(position)))
                    }
                }

                val phoneNumbers = mutableListOf<String>()
                data.optJSONArray("phone_numbers")?.let { arr ->
                    for (i in 0 until arr.length()) phoneNumbers.add(arr.optString(i))
                }

                val hasEmail = data.optBoolean("has_email", false)
                val email = data.optStringOrNull("email")
                val emailUrl = if (hasEmail && email.isNullOrEmpty()) data.optStringOrNull("email_url") else null

                Result.success(
                    PersonDetail(
                        person = formatPerson(data),
                        employment = employment,
                        officeHours = plText(data.opt("office_hours")),
                        room = data.optStringOrNull("room"),
                        phoneNumbers = phoneNumbers,
                        email = email,
                        emailUrl = emailUrl,
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Rozkład ocen (procentowy) dla danego egzaminu/zaliczenia. Metoda
     * `examrep/exam` (stary, nie-"2" moduł) ma gotowe pole
     * `grades_distribution` — nie trzeba znać ani numeru grupy, ani żadnego
     * innego ID poza `exam_id`, który już mamy z ocen. Odpowiednik
     * `/rozklad-ocen-json/<exam_id>` z app.py.
     */
    suspend fun fetchGradeDistribution(examId: Int, forceRefresh: Boolean = false): Result<List<DistributionBar>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = get(
                    "examrep/exam",
                    mapOf("id" to examId.toString(), "fields" to "grades_distribution"),
                    forceRefresh,
                )
                if (!resp.isSuccessful) return@withContext Result.failure(Exception(resp.body))

                val data = JSONObject(resp.body)
                val distribution = data.optJSONArray("grades_distribution")
                    ?: return@withContext Result.failure(Exception("Nieoczekiwany kształt odpowiedzi."))

                val bars = mutableListOf<DistributionBar>()
                for (i in 0 until distribution.length()) {
                    val item = distribution.optJSONObject(i) ?: continue
                    val symbol = item.optStringOrNull("grade_symbol") ?: continue
                    bars.add(DistributionBar(symbol, item.optDouble("percentage", 0.0)))
                }
                if (bars.isEmpty()) return@withContext Result.failure(Exception("Brak rozpoznanych wpisów."))

                bars.sortBy { parseGradeValue(it.symbol) ?: -1.0 }
                Result.success(bars)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
