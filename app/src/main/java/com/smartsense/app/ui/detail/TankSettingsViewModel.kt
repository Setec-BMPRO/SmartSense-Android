package com.smartsense.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.domain.model.NotificationFrequency
import com.smartsense.app.domain.model.QualityThreshold
import com.smartsense.app.domain.model.Tank
import com.smartsense.app.domain.model.TankLevelUnit
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankRegion
import com.smartsense.app.domain.model.TankType
import com.smartsense.app.domain.model.TriggerAlarmUnit
import com.smartsense.app.domain.usecase.SensorScanUseCase
import com.smartsense.app.ui.detail.TankSettingsFragment.Companion.EXTRA_SENSOR_ADDRESS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailTankSettingsViewModel @Inject constructor(
    private val repository: SensorRepository,
    savedStateHandle: SavedStateHandle,
    private val userCase: SensorScanUseCase
) : ViewModel() {

    private val sensorAddress: String =
        savedStateHandle[EXTRA_SENSOR_ADDRESS] ?: ""

    private val _uiState = MutableStateFlow(TankSettingsUiState())
    val uiState: StateFlow<TankSettingsUiState> = _uiState.asStateFlow()

    // --------------------------------------
    // 📥 LOAD
    // --------------------------------------

    fun loadTankConfig() {
        viewModelScope.launch {
            val tank = repository.getTankConfig(sensorAddress)

            _uiState.update { state ->
                tank?.let {
                    state.copy(
                        sensorAddress = sensorAddress,
                        name = it.name,
                        region = it.region,
                        tankType = it.type,
                        customHeightMeters = it.customHeightMeters,
                        orientation = it.orientation,
                        qualityThreshold = it.qualityThreshold,
                        levelUnit = it.levelUnit,
                        alarmThresholdPercent = it.alarmThresholdPercent,
                        notificationsEnabled = it.notificationsEnabled,
                        notificationFrequency = it.notificationFrequency,
                        triggerAlarmUnit = it.triggerAlarmUnit,
                        isLoading = false
                    )
                } ?: state.copy(
                    sensorAddress = sensorAddress,
                    isLoading = false
                )
            }
        }
    }

    // --------------------------------------
    // ✏️ UPDATE STATE
    // --------------------------------------

    fun updateName(name: String) =
        updateState { it.copy(name = name) }

    fun updateRegion(region: TankRegion) {
        _uiState.update { state ->
            val availableTypes = TankType.forTankRegion(region)
            val newType =
                if (state.tankType in availableTypes) state.tankType
                else availableTypes.first()

            state.copy(
                region = region,
                tankType = newType,
                orientation = newType.orientation,
                isSaved = false
            )
        }
    }

    fun updateTankType(type: TankType) =
        updateState {
            it.copy(
                tankType = type,
                orientation = type.orientation
            )
        }

    fun updateQuality(type: QualityThreshold) =
        updateState { it.copy(qualityThreshold = type) }

    fun updateLevelUnit(unit: TankLevelUnit) =
        updateState { it.copy(levelUnit = unit) }

    fun updateTriggerAlarmUnit(unit: TriggerAlarmUnit) =
        updateState { it.copy(triggerAlarmUnit = unit) }

    fun updateOrientation(orientation: TankOrientation) =
        updateState { it.copy(orientation = orientation) }

    fun updateAlarmThreshold(percent: Int) =
        updateState {
            it.copy(alarmThresholdPercent = percent.coerceIn(0, 100))
        }

    fun updateNotificationsEnabled(enabled: Boolean) =
        updateState { it.copy(notificationsEnabled = enabled) }

    fun updateNotificationFrequency(frequency: NotificationFrequency) =
        updateState { it.copy(notificationFrequency = frequency) }

    fun updateCustomHeight(displayValue: String) {
        val value = displayValue.toDoubleOrNull() ?: return

        val meters = if (_uiState.value.useInches) {
            value / 39.3701
        } else {
            value / 100.0
        }

        updateState {
            it.copy(customHeightMeters = meters)
        }
    }

    fun toggleUnits() {
        _uiState.update { it.copy(useInches = !it.useInches) }
    }

    // --------------------------------------
    // 💾 SAVE
    // --------------------------------------

    fun save() {
        val state = _uiState.value

        viewModelScope.launch {
            repository.saveTankConfig(
                Tank(
                    sensorAddress = state.sensorAddress,
                    name = state.name,
                    region = state.region,
                    type = state.tankType,
                    customHeightMeters = state.customHeightMeters,
                    orientation = state.orientation,
                    qualityThreshold = state.qualityThreshold,
                    levelUnit = state.levelUnit,
                    notificationsEnabled = state.notificationsEnabled,
                    alarmThresholdPercent = state.alarmThresholdPercent,
                    notificationFrequency = state.notificationFrequency,
                    triggerAlarmUnit = state.triggerAlarmUnit
                )
            )

            _uiState.update { it.copy(isSaved = true) }
            triggerSync()
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            userCase.triggerSync()
        }
    }

    // --------------------------------------
    // 🧩 INTERNAL HELPER
    // --------------------------------------

    private inline fun updateState(
        crossinline reducer: (TankSettingsUiState) -> TankSettingsUiState
    ) {
        _uiState.update { current ->
            reducer(current).copy(isSaved = false)
        }
    }
}

data class TankSettingsUiState(
    val sensorAddress: String = "",
    val name: String = "",
    val region: TankRegion = TankRegion.AUSTRALIA,
    val tankType: TankType = TankType.default(),
    val customHeightMeters: Double = 0.0,
    val orientation: TankOrientation = TankOrientation.VERTICAL,
    val qualityThreshold: QualityThreshold = QualityThreshold.DISABLE,
    val levelUnit: TankLevelUnit = TankLevelUnit.PERCENT,
    val triggerAlarmUnit: TriggerAlarmUnit = TriggerAlarmUnit.ABOVE,
    val alarmThresholdPercent: Int = 20,
    val notificationsEnabled: Boolean = true,
    val notificationFrequency: NotificationFrequency = NotificationFrequency.EVERY_12_HOURS,
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val useInches: Boolean = true
) {
    val customHeightDisplay: String
        get() = if (useInches) {
            "%.1f".format(customHeightMeters * 39.3701)
        } else {
            "%.1f".format(customHeightMeters * 100)
        }

    val availableTankTypes: List<TankType>
        get() = TankType.forTankRegion(region)
}
