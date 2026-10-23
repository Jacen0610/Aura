package si.jacen.aura.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import si.jacen.aura.BuildConfig
import si.jacen.aura.domain.model.TempUnit
import si.jacen.aura.domain.repository.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val unit: StateFlow<TempUnit> =
        settings.unit.stateIn(viewModelScope, SharingStarted.Eagerly, TempUnit.CELSIUS)

    fun setUnit(unit: TempUnit) {
        viewModelScope.launch { settings.setUnit(unit) }
    }

    val appVersion: String = BuildConfig.VERSION_NAME
}
