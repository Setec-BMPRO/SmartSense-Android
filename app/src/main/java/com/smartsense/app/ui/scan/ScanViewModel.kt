package com.smartsense.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.UnitSystem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: SensorRepository,
    userPreferences: UserPreferences
) : ViewModel() {

    val isScanning: StateFlow<Boolean> = repository.isScanning
    val sensorCount: StateFlow<Int> = repository.sensorCount

    val sensors: StateFlow<List<Sensor>> = repository.getSensors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unitSystem: StateFlow<UnitSystem> = userPreferences.unitSystem
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UnitSystem.METRIC)

    fun startScan() {
        viewModelScope.launch {
            repository.scanForSensors().collect { /* auto-paired via repository */ }
        }
    }

    fun stopScan() {
        repository.stopScan()
    }
}
