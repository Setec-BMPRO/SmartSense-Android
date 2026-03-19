package com.smartsense.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.domain.model.UnitSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val unitSystem: StateFlow<UnitSystem> = userPreferences.unitSystem
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UnitSystem.METRIC)

    val scanInterval: StateFlow<Int> = userPreferences.scanInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val appTheme: StateFlow<String> = userPreferences.appTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, "System")

    fun setUnitSystem(unitSystem: UnitSystem) {
        viewModelScope.launch {
            userPreferences.setUnitSystem(unitSystem)
        }
    }

    fun setScanInterval(seconds: Int) {
        viewModelScope.launch {
            userPreferences.setScanInterval(seconds)
        }
    }

    fun setAppTheme(theme: String) {
        viewModelScope.launch {
            userPreferences.setAppTheme(theme)
        }
    }
}
