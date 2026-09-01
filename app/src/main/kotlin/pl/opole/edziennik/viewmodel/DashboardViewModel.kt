package pl.opole.edziennik.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.opole.edziennik.data.DayGroup
import pl.opole.edziennik.data.Payment
import pl.opole.edziennik.data.UsosRepository
import java.time.LocalDate

/** Odpowiednik trasy `/dashboard` z aplikacji webowej. */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val payments: List<Payment> = emptyList(),
    val paymentsTotal: Double = 0.0,
    val paymentsError: String? = null,
    val schedule: List<DayGroup> = emptyList(),
    val scheduleError: String? = null,
)

class DashboardViewModel(private val repository: UsosRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        refresh()
    }

    /**
     * `forceRefresh = false` (wejście na ekran) pokazuje dane z trwałego
     * cache, jeśli już tam są. `forceRefresh = true` (przycisk "⟳") zawsze
     * pyta USOS na nowo. Jeśli wymuszone odświeżenie zawiedzie, zachowujemy
     * ostatnio pokazane dane zamiast czyścić ekran — użytkownik widzi stare
     * dane razem z komunikatem błędu.
     */
    fun refresh(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(isLoading = true)

            val paymentsResult = repository.fetchOutstandingPayments(forceRefresh)
            val today = LocalDate.now()
            val scheduleResult = repository.fetchSchedule(today, today.plusDays(6), forceRefresh)

            _uiState.value = current.copy(
                isLoading = false,
                payments = paymentsResult.getOrNull()?.first ?: current.payments,
                paymentsTotal = paymentsResult.getOrNull()?.second ?: current.paymentsTotal,
                paymentsError = paymentsResult.exceptionOrNull()?.message,
                schedule = scheduleResult.getOrNull() ?: current.schedule,
                scheduleError = scheduleResult.exceptionOrNull()?.message,
            )
        }
    }
}

class DashboardViewModelFactory(private val repository: UsosRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DashboardViewModel(repository) as T
    }
}
