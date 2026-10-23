package si.jacen.aura.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import si.jacen.aura.domain.model.GeoCity
import si.jacen.aura.domain.usecase.SearchCityUseCase
import si.jacen.aura.util.userMessage
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchCity: SearchCityUseCase,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val results: List<GeoCity> = emptyList(),
        val searching: Boolean = false,
        val searched: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var debounceJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        debounceJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), searched = false, error = null) }
            return
        }
        debounceJob = viewModelScope.launch {
            delay(400)
            search(query)
        }
    }

    fun searchNow() {
        debounceJob?.cancel()
        val q = _uiState.value.query
        if (q.isNotBlank()) viewModelScope.launch { search(q) }
    }

    private suspend fun search(query: String) {
        _uiState.update { it.copy(searching = true, error = null) }
        try {
            val results = searchCity(query)
            _uiState.update {
                it.copy(searching = false, searched = true, results = results)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(searching = false, searched = true, error = e.userMessage()) }
        }
    }
}
