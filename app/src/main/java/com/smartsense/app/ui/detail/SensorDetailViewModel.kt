package com.smartsense.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.TankPreset
import com.smartsense.app.domain.model.UnitSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensorDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SensorRepository,
    userPreferences: UserPreferences
) : ViewModel() {

    private val sensorAddress: String = savedStateHandle.get<String>("sensorAddress") ?: ""

    val sensor: StateFlow<Sensor?> = repository.getSensorByAddress(sensorAddress)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val unitSystem: StateFlow<UnitSystem> = userPreferences.unitSystem
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UnitSystem.METRIC)

    fun removeSensor() {
        viewModelScope.launch {
            repository.removeSensor(sensorAddress)
        }
    }

    fun updateTankPreset(preset: TankPreset) {
        viewModelScope.launch {
            repository.updateTankPreset(sensorAddress, preset)
        }
    }
}
