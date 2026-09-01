package pl.opole.edziennik.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.opole.edziennik.data.PersonDetail
import pl.opole.edziennik.data.UsosRepository

/** Odpowiednik trasy `/osoba/<user_id>` z aplikacji webowej. */
data class PersonDetailUiState(
    val isLoading: Boolean = true,
    val detail: PersonDetail? = null,
    val error: String? = null,
)

class PersonDetailViewModel(
    private val repository: UsosRepository,
    private val userId: Int,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PersonDetailUiState())
    val uiState: StateFlow<PersonDetailUiState> = _uiState

    init {
        refresh(forceRefresh = false)
    }

    /** `forceRefresh = true` (przycisk odświeżania) pomija cache i zawsze pyta USOS
     * na nowo; przy błędzie zachowuje ostatnio pokazane dane osoby zamiast
     * czyścić ekran. */
    fun refresh(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(isLoading = true)

            val result = repository.fetchPersonDetail(userId, forceRefresh)
            _uiState.value = current.copy(
                isLoading = false,
                detail = result.getOrNull() ?: current.detail,
                error = result.exceptionOrNull()?.message,
            )
        }
    }
}

class PersonDetailViewModelFactory(
    private val repository: UsosRepository,
    private val userId: Int,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PersonDetailViewModel(repository, userId) as T
    }
}
