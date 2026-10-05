package pl.opole.edziennik.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.opole.edziennik.BuildConfig
import pl.opole.edziennik.data.DayGroup
import pl.opole.edziennik.data.UsosRepository
import pl.opole.edziennik.update.UpdateChecker
import pl.opole.edziennik.update.UpdateInfo
import java.time.LocalDate

/** Odpowiednik trasy `/dashboard` z aplikacji webowej — sam plan na
 * najbliższe 7 dni. Płatności mają teraz własną zakładkę (patrz
 * `PaymentsViewModel`/`PaymentsScreen`). */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val schedule: List<DayGroup> = emptyList(),
    val scheduleError: String? = null,
    /** `null`, dopóki nie ma nowszego builda na GitHubie niż ten
     * zainstalowany (albo sprawdzenie jeszcze trwa/zawiodło). */
    val updateInfo: UpdateInfo? = null,
)

class DashboardViewModel(
    private val repository: UsosRepository,
    private val updateChecker: UpdateChecker = UpdateChecker(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        refresh()
        checkForUpdate()
    }

    /** Sprawdza raz, przy otwarciu Pulpitu, czy na GitHubie jest nowszy
     * build niż ten zainstalowany (patrz `UpdateChecker`) — niezależnie od
     * cache'u planu/ocen, nie wymaga odświeżenia ręcznego. */
    private fun checkForUpdate() {
        viewModelScope.launch {
            val info = updateChecker.checkForUpdate()
            if (info != null && info.versionCode > BuildConfig.VERSION_CODE) {
                _uiState.value = _uiState.value.copy(updateInfo = info)
            }
        }
    }

    /**
     * `forceRefresh = false` (wejście na ekran) pokazuje dane z trwałego
     * cache, jeśli już tam są. `forceRefresh = true` (przycisk odświeżania) zawsze
     * pyta USOS na nowo. Jeśli wymuszone odświeżenie zawiedzie, zachowujemy
     * ostatnio pokazane dane zamiast czyścić ekran — użytkownik widzi stare
     * dane razem z komunikatem błędu.
     */
    fun refresh(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.value = current.copy(isLoading = true)

            val today = LocalDate.now()
            val scheduleResult = repository.fetchSchedule(today, today.plusDays(6), forceRefresh)

            _uiState.value = current.copy(
                isLoading = false,
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
