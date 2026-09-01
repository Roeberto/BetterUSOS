package pl.opole.edziennik.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.opole.edziennik.data.GroupDetail
import pl.opole.edziennik.data.UsosRepository

/** Odpowiednik trasy `/grupa/<unit_id>/<group_number>` z aplikacji webowej. */
data class GroupDetailUiState(
    val isLoading: Boolean = true,
    val detail: GroupDetail? = null,
    val error: String? = null,
)

class GroupDetailViewModel(
    private val repository: UsosRepository,
    private val unitId: Int,
    private val groupNumber: Int,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupDetailUiState())
    val uiState: StateFlow<GroupDetailUiState> = _uiState

    init {
        refresh(forceRefresh = false)
    }

    /** `forceRefresh = true` (przycisk odświeżania) pomija cache i zawsze pyta USOS
     * na nowo; przy błędzie zachowuje ostatnio pokazane dane grupy zamiast
     * czyścić ekran. */
    fun refresh(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(isLoading = true)

            val result = repository.fetchGroupDetail(unitId, groupNumber, forceRefresh)
            _uiState.value = current.copy(
                isLoading = false,
                detail = result.getOrNull() ?: current.detail,
                error = result.exceptionOrNull()?.message,
            )
        }
    }
}

class GroupDetailViewModelFactory(
    private val repository: UsosRepository,
    private val unitId: Int,
    private val groupNumber: Int,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GroupDetailViewModel(repository, unitId, groupNumber) as T
    }
}
