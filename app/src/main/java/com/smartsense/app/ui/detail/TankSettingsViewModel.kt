package com.smartsense.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.repository.Sensor1Repository
import com.smartsense.app.domain.model.NotificationFrequency
import com.smartsense.app.domain.model.QualityThreshold
import com.smartsense.app.domain.model.Tank
import com.smartsense.app.domain.model.TankLevelUnit
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankRegion
import com.smartsense.app.domain.model.TankType
import com.smartsense.app.domain.model.TriggerAlarmUnit
import com.smartsense.app.ui.detail.TankSettingsFragment.Companion.EXTRA_SENSOR_ADDRESS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TankSettingsUiState(
    val sensorAddress: String = "",
    val name: String = "",
    val region: TankRegion = TankRegion.AUSTRALIA,
    val tankType: TankType = TankType.KG_3_7,
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

@HiltViewModel
class DetailTankSettingsViewModel @Inject constructor(
    private val repository: Sensor1Repository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sensorAddress: String = savedStateHandle.get<String>(EXTRA_SENSOR_ADDRESS) ?: ""

    private val _uiState = MutableStateFlow(TankSettingsUiState())
    val uiState: StateFlow<TankSettingsUiState> = _uiState.asStateFlow()

    fun loadTankConfig() {
        viewModelScope.launch {
            val tank = repository.getTankConfig(sensorAddress)
            _uiState.update {
                if (tank != null) {
                    it.copy(
                        sensorAddress = sensorAddress,
                        name = tank.name,
                        region = tank.region,
                        tankType = tank.type,
                        customHeightMeters = tank.customHeightMeters,
                        orientation = tank.orientation,
                        qualityThreshold = tank.qualityThreshold,
                        levelUnit = tank.levelUnit,
                        alarmThresholdPercent = tank.alarmThresholdPercent,
                        notificationsEnabled = tank.notificationsEnabled,
                        notificationFrequency = tank.notificationFrequency,
                        triggerAlarmUnit = tank.triggerAlarmUnit,
                        isLoading = false
                    )
                } else {
                    it.copy(
                        sensorAddress = sensorAddress,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name, isSaved = false) }
    }

    fun updateRegion(region: TankRegion) {
        _uiState.update { state ->
            // When region changes, reset tank type to first available for that region
            val availableTypes = TankType.forTankRegion(region)
            val newType = if (state.tankType in availableTypes) state.tankType else availableTypes.first()
            state.copy(
                region = region,
                tankType = newType,
                orientation = newType.orientation,
                isSaved = false
            )
        }
    }

    fun updateTankType(type: TankType) {
        _uiState.update {
            it.copy(
                tankType = type,
                orientation = type.orientation,
                isSaved = false
            )
        }
    }

    fun updateQuality(type: QualityThreshold) {
        _uiState.update {
            it.copy(
                qualityThreshold = type,
                isSaved = false
            )
        }
    }

    fun updateLevelUnit(unit: TankLevelUnit) {
        _uiState.update { it.copy(levelUnit = unit, isSaved = false) }
    }

    fun updateTriggerAlarmUnit(unit: TriggerAlarmUnit) {
        _uiState.update { it.copy(triggerAlarmUnit = unit, isSaved = false) }
    }

    fun updateCustomHeight(displayValue: String) {
        val value = displayValue.toDoubleOrNull() ?: return
        val meters = if (_uiState.value.useInches) {
            value / 39.3701
        } else {
            value / 100.0
        }
        _uiState.update { it.copy(customHeightMeters = meters, isSaved = false) }
    }

    fun updateOrientation(orientation: TankOrientation) {
        _uiState.update { it.copy(orientation = orientation, isSaved = false) }
    }

    fun updateAlarmThreshold(percent: Int) {
        _uiState.update { it.copy(alarmThresholdPercent = percent.coerceIn(0, 100), isSaved = false) }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled, isSaved = false) }
    }

    fun updateNotificationFrequency(frequency: NotificationFrequency) {
        _uiState.update { it.copy(notificationFrequency = frequency, isSaved = false) }
    }

    fun toggleUnits() {
        _uiState.update { it.copy(useInches = !it.useInches) }
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveTankConfig(
                Tank(
                    sensorAddress = state.sensorAddress,
                    name = state.name,
                    region = state.region,
                    // Tank type
                    type = state.tankType,
                    customHeightMeters = state.customHeightMeters,
                    orientation = state.orientation,
                    // Quality + Tank Level Unit
                    qualityThreshold = state.qualityThreshold,
                    levelUnit = state.levelUnit,
                    // Notification
                    notificationsEnabled = state.notificationsEnabled,
                    alarmThresholdPercent = state.alarmThresholdPercent,
                    notificationFrequency = state.notificationFrequency,
                    triggerAlarmUnit=state.triggerAlarmUnit
                )
            )
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
