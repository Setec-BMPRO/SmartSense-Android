package com.smartsense.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.domain.model.*
import com.smartsense.app.domain.usecase.SettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val useCase: SettingsUseCase
) : ViewModel() {

    // -------------------------------------------------------------------------
    // 📊 Preference States (Enums)
    // -------------------------------------------------------------------------

    val unitSystem: StateFlow<UnitSystem> = userPreferences.unitSystem
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSystem.METRIC)

    val scanInterval: StateFlow<ScanIntervals> = userPreferences.scanInterval
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScanIntervals.default())

    val appTheme: StateFlow<AppTheme> = userPreferences.appTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.SYSTEM)

    val sortPreference: StateFlow<SortPreference> = userPreferences.sortPreference
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortPreference.NAME)

    val appLanguage: StateFlow<AppLanguage> = userPreferences.appLanguage
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.SYSTEM)
    val isSignedIn: StateFlow<Boolean> = userPreferences.isSignedIn
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = false)

    val hasRegisteredSensors: StateFlow<Boolean> = useCase.getAllRegisteredSensors()
        .map { size -> size.count() > 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )
    // -------------------------------------------------------------------------
    // 🎛️ Toggle States (Booleans)
    // -------------------------------------------------------------------------

    val notificationsEnabled: StateFlow<Boolean> = userPreferences.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val uploadSensorData: StateFlow<Boolean> = userPreferences.uploadSensorData
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val groupFilterEnabled: StateFlow<Boolean> = userPreferences.groupFilterEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val deviceSearchFilterEnabled: StateFlow<Boolean> = userPreferences.deviceSearchFilterEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Hidden developer-mode flag (see UserPreferences.developerModeEnabled). */
    val developerModeEnabled: StateFlow<Boolean> = userPreferences.developerModeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Developer-tunable stddev cutoff (mm) for GOOD quality. */
    val stddevGoodMm: StateFlow<Float> = userPreferences.stddevGoodMm
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences.DEFAULT_STDDEV_GOOD_MM)

    /** Developer-tunable stddev cutoff (mm) for FAIR quality. Above this → POOR. */
    val stddevFairMm: StateFlow<Float> = userPreferences.stddevFairMm
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences.DEFAULT_STDDEV_FAIR_MM)

    // -------------------------------------------------------------------------
    // ✍️ Update Functions
    // -------------------------------------------------------------------------

    fun setUnitSystem(unit: UnitSystem) =
        viewModelScope.launch { userPreferences.setUnitSystem(unit) }

    fun setScanInterval(interval: ScanIntervals) =
        viewModelScope.launch { userPreferences.setScanInterval(interval) }

    fun setAppTheme(theme: AppTheme) =
        viewModelScope.launch { userPreferences.setAppTheme(theme) }

    fun setSortPreference(sort: SortPreference) =
        viewModelScope.launch { userPreferences.setSortPreference(sort) }

    fun setAppLanguage(language: AppLanguage) =
        viewModelScope.launch { userPreferences.setAppLanguage(language) }

    fun setNotificationsEnabled(enabled: Boolean) =
        viewModelScope.launch { userPreferences.setNotificationsEnabled(enabled) }

    fun setUploadSensorData(enabled: Boolean) =
        viewModelScope.launch { userPreferences.setUploadSensorData(enabled) }

    fun setGroupFilterEnabled(enabled: Boolean) =
        viewModelScope.launch { userPreferences.setGroupFilterEnabled(enabled) }

    fun setDeviceSearchFilterEnabled(enabled: Boolean) =
        viewModelScope.launch { userPreferences.setDeviceSearchFilterEnabled(enabled) }

    fun setDeveloperModeEnabled(enabled: Boolean) =
        viewModelScope.launch { userPreferences.setDeveloperModeEnabled(enabled) }

    fun setStddevGoodMm(valueMm: Float) =
        viewModelScope.launch { userPreferences.setStddevGoodMm(valueMm) }

    fun setStddevFairMm(valueMm: Float) =
        viewModelScope.launch { userPreferences.setStddevFairMm(valueMm) }

    /** Reset the quality thresholds to the constants the app shipped with. Bound to a
     *  "Reset to defaults" button in the developer-mode UI so QA doesn't have to remember
     *  the original numbers after a calibration run. */
    fun resetQualityThresholds() = viewModelScope.launch {
        userPreferences.setStddevGoodMm(UserPreferences.DEFAULT_STDDEV_GOOD_MM)
        userPreferences.setStddevFairMm(UserPreferences.DEFAULT_STDDEV_FAIR_MM)
    }


    // -------------------------------------------------------------------------
    // 🗑️ Data Management
    // -------------------------------------------------------------------------

    fun unregisterAllSensors() =
        viewModelScope.launch { useCase.unregisterAllSensors(userPreferences.uploadSensorData.first()) }
}