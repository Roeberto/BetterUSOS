package pl.opole.edziennik.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.opole.edziennik.data.Payment
import pl.opole.edziennik.data.UsosRepository

/** Odpowiednik sekcji płatności z `/dashboard` w aplikacji webowej — tu
 * jako osobna zakładka (patrz pasek na dole Pulpitu), z podziałem na
 * należności nierozliczone i rozliczone. */
data class PaymentsUiState(
    val isLoading: Boolean = true,
    /** `state != "paid"` — należności do zapłaty. */
    val outstanding: List<Payment> = emptyList(),
    /** `state == "paid"` — należności już opłacone. */
    val settled: List<Payment> = emptyList(),
    /** Suma należności nierozliczonych — "do zapłaty łącznie". */
    val outstandingTotal: Double = 0.0,
    val error: String? = null,
)

class PaymentsViewModel(private val repository: UsosRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(PaymentsUiState())
    val uiState: StateFlow<PaymentsUiState> = _uiState

    init {
        refresh()
    }

    /** `forceRefresh = true` (przycisk odświeżania) pomija cache i zawsze
     * pyta USOS na nowo; przy błędzie zachowuje ostatnio pokazane płatności
     * zamiast czyścić ekran. */
    fun refresh(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(isLoading = true)

            val result = repository.fetchPayments(forceRefresh)
            val all = result.getOrNull()?.first
            _uiState.value = current.copy(
                isLoading = false,
                outstanding = all?.filter { it.state != "paid" } ?: current.outstanding,
                settled = all?.filter { it.state == "paid" } ?: current.settled,
                outstandingTotal = result.getOrNull()?.second ?: current.outstandingTotal,
                error = result.exceptionOrNull()?.message,
            )
        }
    }
}

class PaymentsViewModelFactory(private val repository: UsosRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PaymentsViewModel(repository) as T
    }
}
