package pl.opole.edziennik.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.opole.edziennik.data.TermSection
import pl.opole.edziennik.data.UsosRepository
import pl.opole.edziennik.data.computeWeightedAverage
import pl.opole.edziennik.data.groupGradesByTerm

/** Odpowiednik trasy `/oceny` z aplikacji webowej. */
data class GradesUiState(
    val isLoading: Boolean = true,
    val termSections: List<TermSection> = emptyList(),
    val weightedAverage: Double? = null,
    val error: String? = null,
)

class GradesViewModel(private val repository: UsosRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(GradesUiState())
    val uiState: StateFlow<GradesUiState> = _uiState

    init {
        refresh()
    }

    /** `forceRefresh = true` (przycisk odświeżania) pomija cache i zawsze pyta USOS
     * na nowo; przy błędzie zachowuje ostatnio pokazane oceny zamiast
     * czyścić ekran. */
    fun refresh(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(isLoading = true, error = null)

            val gradesResult = repository.fetchAllGrades(forceRefresh)
            val grades = gradesResult.getOrNull()
            if (grades == null) {
                _uiState.value = current.copy(isLoading = false, error = gradesResult.exceptionOrNull()?.message)
                return@launch
            }

            val ectsPoints = repository.fetchEctsPoints(forceRefresh)
            val termSections = groupGradesByTerm(grades, ectsPoints)
            val average = computeWeightedAverage(termSections)

            _uiState.value = GradesUiState(
                isLoading = false,
                termSections = termSections,
                weightedAverage = average,
            )
        }
    }
}

class GradesViewModelFactory(private val repository: UsosRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GradesViewModel(repository) as T
    }
}
