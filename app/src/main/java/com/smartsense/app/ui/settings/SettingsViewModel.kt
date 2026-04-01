package com.smartsense.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.domain.model.AppTheme
import com.smartsense.app.domain.model.ScanIntervals
import com.smartsense.app.domain.model.SortPreference
import com.smartsense.app.domain.model.UnitSystem
import com.smartsense.app.domain.usecase.SettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val settingsUseCase: SettingsUseCase

) : ViewModel() {

    // --- StateFlows using Enums ---
    val unitSystem: StateFlow<UnitSystem> = userPreferences.unitSystem
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSystem.METRIC)

    val scanInterval: StateFlow<ScanIntervals> = userPreferences.scanInterval
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScanIntervals.default())

    val appTheme: StateFlow<AppTheme> = userPreferences.appTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.SYSTEM)

    val sortPreference: StateFlow<SortPreference> = userPreferences.sortPreference
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortPreference.NAME)

    // --- Boolean States ---
    val notificationsEnabled: StateFlow<Boolean> = userPreferences.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val uploadSensorData: StateFlow<Boolean> = userPreferences.uploadSensorData
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val groupFilterEnabled: StateFlow<Boolean> = userPreferences.groupFilterEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val deviceSearchFilterEnabled: StateFlow<Boolean> = userPreferences.deviceSearchFilterEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // --- Update Functions ---
    fun setUnitSystem(unit: UnitSystem) = viewModelScope.launch { userPreferences.setUnitSystem(unit) }

    fun setScanInterval(interval: ScanIntervals) = viewModelScope.launch { userPreferences.setScanInterval(interval) }

    fun setAppTheme(theme: AppTheme) = viewModelScope.launch { userPreferences.setAppTheme(theme) }

    fun setSortPreference(sort: SortPreference) = viewModelScope.launch { userPreferences.setSortPreference(sort) }

    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch { userPreferences.setNotificationsEnabled(enabled) }

    fun setUploadSensorData(enabled: Boolean) = viewModelScope.launch { userPreferences.setUploadSensorData(enabled) }

    fun setGroupFilterEnabled(enabled: Boolean) = viewModelScope.launch { userPreferences.setGroupFilterEnabled(enabled) }

    fun setDeviceSearchFilterEnabled(enabled: Boolean) = viewModelScope.launch { userPreferences.setDeviceSearchFilterEnabled(enabled) }

    fun deleteAllSensors() = viewModelScope.launch {  settingsUseCase.unregisterAllSensors()  }



}



