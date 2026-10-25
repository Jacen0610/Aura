package si.jacen.aura.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import si.jacen.aura.domain.repository.AiRepository
import si.jacen.aura.domain.repository.SettingsRepository
import si.jacen.aura.util.userMessage
import javax.inject.Inject

@HiltViewModel
class AskViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** 一次问答记录 */
    data class QaEntry(val question: String, val answer: String)

    data class UiState(
        val city: si.jacen.aura.domain.model.GeoCity? = null,
        val input: String = "",
        val thinking: Boolean = false,
        val history: List<QaEntry> = emptyList(),
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var askJob: Job? = null

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(city = settings.lastCity.first()) }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text.take(100)) }
    }

    fun ask(question: String = _uiState.value.input) {
        val q = question.trim()
        if (q.isBlank() || _uiState.value.thinking) return
        val city = _uiState.value.city ?: run {
            _uiState.update { it.copy(error = "请先在天气页选择城市") }
            return
        }
        askJob?.cancel()
        askJob = viewModelScope.launch {
            _uiState.update { it.copy(thinking = true, error = null, input = "") }
            try {
                val answer = aiRepository.ask(city.coordinate, q)
                _uiState.update {
                    it.copy(thinking = false, history = it.history + QaEntry(q, answer))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(thinking = false, input = q, error = e.userMessage())
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
