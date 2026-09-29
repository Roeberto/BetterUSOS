package pl.opole.edziennik.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.opole.edziennik.data.DayGroup
import pl.opole.edziennik.data.UsosRepository
import java.time.LocalDate
import java.time.YearMonth

/** Odpowiednik trasy `/plan` z aplikacji webowej — jeden miesiąc naraz,
 * z zakładkami do przełączania (patrz `academic_year_months` w app.py).
 *
 * `initialSelectedDay` to dzień, na który ekran ma wylądować przy wejściu
 * (dziś, jeśli są dziś zajęcia, w przeciwnym razie najbliższy nadchodzący
 * dzień z zajęciami — patrz `loadInitial()`) — `null`, dopóki się nie
 * wyliczy, albo gdy w najbliższych 60 dniach nie ma żadnych zajęć. */
data class PlanUiState(
    val isLoading: Boolean = true,
    val yearMonth: YearMonth = YearMonth.now(),
    val days: List<DayGroup> = emptyList(),
    val error: String? = null,
    val initialSelectedDay: LocalDate? = null,
)

fun academicYearStart(yearMonth: YearMonth): Int =
    if (yearMonth.monthValue >= 10) yearMonth.year else yearMonth.year - 1

fun academicYearMonths(startYear: Int): List<YearMonth> =
    (10..12).map { YearMonth.of(startYear, it) } + (1..9).map { YearMonth.of(startYear + 1, it) }

class PlanViewModel(private val repository: UsosRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(PlanUiState())
    val uiState: StateFlow<PlanUiState> = _uiState

    init {
        loadInitial()
    }

    /** Przy wejściu na ekran ląduje od razu na dzisiejszym dniu zajęć, a
     * jeśli dziś nic nie ma — na najbliższym nadchodzącym dniu z zajęciami
     * (przeszukuje do 60 dni naprzód, może wypaść w kolejnym miesiącu).
     * Gdy w tym oknie nie ma żadnych zajęć, wraca do zwykłego widoku
     * bieżącego miesiąca bez zaznaczonego dnia. */
    private fun loadInitial() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val probe = repository.fetchSchedule(today, today.plusDays(60)).getOrNull()
            val target = probe?.filter { it.date >= today }?.minByOrNull { it.date }?.date

            _uiState.value = _uiState.value.copy(initialSelectedDay = target)
            load(target?.let { YearMonth.from(it) } ?: YearMonth.now())
        }
    }

    /** `forceRefresh = true` (przycisk odświeżania) pomija cache i zawsze pyta USOS
     * na nowo; przy błędzie zachowuje ostatnio pokazane dni zamiast czyścić
     * ekran. */
    fun load(yearMonth: YearMonth, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(isLoading = true, yearMonth = yearMonth)

            val start = yearMonth.atDay(1)
            val end = yearMonth.atEndOfMonth()
            val result = repository.fetchSchedule(start, end, forceRefresh)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                days = result.getOrNull() ?: current.days,
                error = result.exceptionOrNull()?.message,
            )
        }
    }
}

class PlanViewModelFactory(private val repository: UsosRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PlanViewModel(repository) as T
    }
}
