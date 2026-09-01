package pl.opole.edziennik.data

import java.time.DayOfWeek
import java.time.LocalDate

data class SessionEntry(
    val startTime: String,
    val endTime: String,
    val displayName: String,
    val typeLabel: String,
    val typeAbbr: String,
    val colorKey: String,
    val buildingName: String,
    val roomNumber: String,
    val lecturersDisplay: String,
    val unitId: Int?,
    val groupNumber: Int?,
)

data class DayGroup(
    val date: LocalDate,
    val weekday: String,
    val dateLabel: String,
    val entries: List<SessionEntry>,
)

private val weekdayNames = mapOf(
    DayOfWeek.MONDAY to "Poniedziałek",
    DayOfWeek.TUESDAY to "Wtorek",
    DayOfWeek.WEDNESDAY to "Środa",
    DayOfWeek.THURSDAY to "Czwartek",
    DayOfWeek.FRIDAY to "Piątek",
    DayOfWeek.SATURDAY to "Sobota",
    DayOfWeek.SUNDAY to "Niedziela",
)

private val monthsGenitive = mapOf(
    1 to "stycznia", 2 to "lutego", 3 to "marca", 4 to "kwietnia",
    5 to "maja", 6 to "czerwca", 7 to "lipca", 8 to "sierpnia",
    9 to "września", 10 to "października", 11 to "listopada", 12 to "grudnia",
)

fun dayLabel(date: LocalDate): String = "${date.dayOfMonth} ${monthsGenitive[date.monthValue]}"

fun weekdayLabel(date: LocalDate): String = weekdayNames[date.dayOfWeek] ?: date.dayOfWeek.toString()
