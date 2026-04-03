package com.smartsense.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.data.repository.Sensor1Repository

import com.smartsense.app.domain.model.Sensor1
import com.smartsense.app.domain.model.UnitSystem
import com.smartsense.app.domain.usecase.SensorScanUseCase
import com.smartsense.app.ui.detail.TankSettingsFragment.Companion.EXTRA_SENSOR_ADDRESS

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject


@HiltViewModel
class Sensor1DetailViewModel @Inject constructor(
    private val userCase: SensorScanUseCase,
    savedStateHandle: SavedStateHandle,
    private val userPreferences: UserPreferences,
    private val sensor1Repository: Sensor1Repository
) : ViewModel() {

    val sensorAddress: String =
        savedStateHandle[EXTRA_SENSOR_ADDRESS] ?: ""

    val unitSystem: UnitSystem = runBlocking {
        userPreferences.unitSystem.first()
    }

    private val _uiState = MutableStateFlow(SensorDetailUiState())
    val uiState: StateFlow<SensorDetailUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    // --------------------------------------
    // 🔍 SENSOR OBSERVATION
    // --------------------------------------

    fun startObserveDetailSensor() {
        if (observeJob?.isActive == true) return

        observeJob = viewModelScope.launch {
            // Get the interval once
            val interval = userPreferences.scanInterval.first().value.toLong() * 1000

            // Collect the flow directly
            userCase.observeDetailSensor(sensorAddress, interval)
                .collect { sensor ->
                    _uiState.update {
                        it.copy(sensor = sensor, isLoading = false)
                    }
                }
        }
    }

    fun stopObserveDetailSensor() {
        observeJob?.cancel()
        observeJob = null
    }

    // --------------------------------------
    // ⚙️ ACTIONS
    // --------------------------------------

    fun unregisterSensor() {
        viewModelScope.launch {
            userCase.unregisterSensor(sensorAddress)
        }
    }
    fun triggerSync() {
        viewModelScope.launch {
            userCase.triggerSync()
        }
    }
}

data class SensorDetailUiState(
    val sensor: Sensor1? = null,
    val isLoading: Boolean = true
)