package pl.opole.edziennik.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.opole.edziennik.data.DayGroup
import pl.opole.edziennik.data.UsosRepository
import java.time.YearMonth

/** Odpowiednik trasy `/plan` z aplikacji webowej — jeden miesiąc naraz,
 * z zakładkami do przełączania (patrz `academic_year_months` w app.py). */
data class PlanUiState(
    val isLoading: Boolean = true,
    val yearMonth: YearMonth = YearMonth.now(),
    val days: List<DayGroup> = emptyList(),
    val error: String? = null,
)

fun academicYearStart(yearMonth: YearMonth): Int =
    if (yearMonth.monthValue >= 10) yearMonth.year else yearMonth.year - 1

fun academicYearMonths(startYear: Int): List<YearMonth> =
    (10..12).map { YearMonth.of(startYear, it) } + (1..9).map { YearMonth.of(startYear + 1, it) }

class PlanViewModel(private val repository: UsosRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(PlanUiState())
    val uiState: StateFlow<PlanUiState> = _uiState

    init {
        load(YearMonth.now())
    }

    /** `forceRefresh = true` (przycisk "⟳") pomija cache i zawsze pyta USOS
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
