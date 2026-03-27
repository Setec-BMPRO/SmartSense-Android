package com.smartsense.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.data.repository.Sensor1Repository
import com.smartsense.app.domain.model.Sensor1
import com.smartsense.app.domain.model.UnitSystem
import com.smartsense.app.domain.usecase.SensorDetailUseCase

import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val userCase: SensorDetailUseCase,
    savedStateHandle: SavedStateHandle,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val sensorAddress: String = savedStateHandle.get<String>("sensorAddress") ?: ""

    val unitSystem: UnitSystem = runBlocking {
        userPreferences.unitSystem.first()
    }
    private val _uiState = MutableStateFlow(SensorDetailUiState())
    val uiState: StateFlow<SensorDetailUiState> = _uiState.asStateFlow()

    fun loadSensor() {
        viewModelScope.launch {
            userCase.observeSensorForDetail(sensorAddress)
                .collect { sensor ->
                    _uiState.update {
                        it.copy(sensor = sensor, isLoading = false)
                    }
                }
        }
    }

    fun unregisterSensor() {
        viewModelScope.launch {
            userCase.unregisterSensor(sensorAddress)
        }
    }
}


data class SensorDetailUiState(
    val sensor: Sensor1? = null,
    val isLoading: Boolean = true
)