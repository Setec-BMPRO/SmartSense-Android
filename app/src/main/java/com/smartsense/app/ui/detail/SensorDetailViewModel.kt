package com.smartsense.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.data.worker.TankAlertTrigger
import com.smartsense.app.domain.model.ScanIntervals
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.Tank
import com.smartsense.app.domain.model.UiState
import com.smartsense.app.domain.model.UnitSystem
import com.smartsense.app.domain.usecase.CalculateTankUseCase
import com.smartsense.app.domain.usecase.DetailUseCase
import com.smartsense.app.domain.usecase.ScanUseCase
import com.smartsense.app.ui.detail.TankSettingsFragment.Companion.EXTRA_SENSOR_ADDRESS
import com.smartsense.app.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject


@HiltViewModel
class Sensor1DetailViewModel @Inject constructor(
    private val useCase: DetailUseCase,
    savedStateHandle: SavedStateHandle,
    private val userPreferences: UserPreferences,
    private val alertTrigger: TankAlertTrigger,
    private val calculateTankUseCase: CalculateTankUseCase
) : ViewModel() {

    val sensorAddress: String =
        savedStateHandle[EXTRA_SENSOR_ADDRESS] ?: ""

    val unitSystem: UnitSystem = runBlocking {
        userPreferences.unitSystem.first()
    }

    val scanIntervals: ScanIntervals = runBlocking {
        userPreferences.scanInterval.first()
    }

    private val _uiState = MutableStateFlow(SensorDetailUiState())
    val uiState: StateFlow<SensorDetailUiState> = _uiState.asStateFlow()

    private val _removeUiState = MutableStateFlow(UiState())
    // 2. Public Read-only StateFlow for the UI
    val removeUiState = _removeUiState.asStateFlow()

    private var observeJob: Job? = null
    private val tickerFlow = flow {
        while (true) {
            emit(Unit)
            delay(1000L)
        }
    }

    // Map the UI state to include the formatted time string
    val lastUpdatedTime: Flow<String> = combine(uiState, tickerFlow) { state, _ ->
        val timestamp = state.sensor?.reading?.timestampMillis
        TimeUtils.getLastUpdatedText(timestamp)
    }.distinctUntilChanged()

    // --------------------------------------
    // 🔍 SENSOR OBSERVATION
    // --------------------------------------

    fun startObserveDetailSensor() {
        if (observeJob?.isActive == true) return

        observeJob = viewModelScope.launch {
            // Get the interval once
            val interval = userPreferences.scanInterval.first().value.toLong() * 1000

            // Collect the flow directly
            useCase.observeDetailSensor(sensorAddress, interval)
                .collect { sensor ->
                    _uiState.update {
                        it.copy(sensor = sensor, isLoading = false)
                    }
                    val level = sensor?.tankLevel?.percentage?.toInt() ?: -1
                    alertTrigger.checkAndTrigger(
                        address = sensor!!.address,
                        currentLevel = level
                    )
                }
        }
    }

    fun stopObserveDetailSensor() {
        observeJob?.cancel()
        observeJob = null
    }


    fun loadTankConfig() {
        viewModelScope.launch {
            val tank = useCase.getTankConfig(sensorAddress)
            _uiState.update { state ->
                state.copy(tank = tank)
            }
        }
    }

    // --------------------------------------
    // ⚙️ ACTIONS
    // --------------------------------------

    fun unregisterSensor() {
        viewModelScope.launch {
            val result = useCase.unregisterSensor(sensorAddress,userPreferences.uploadSensorData.first())
            result.onSuccess { wasSyncTriggered ->
                Timber.d("✅ UI: Deletion successful for $sensorAddress. Sync triggered: $wasSyncTriggered")
                _removeUiState.update {
                    it.copy(
                        successMessage = if (wasSyncTriggered) "Device removed & Sync started" else "Device removed locally"
                    )
                }
            }.onFailure { error ->
                val errorMsg = error.message ?: "Unknown Error"
                Timber.e("❌ UI: Deletion failed for $sensorAddress. Error: $errorMsg")
                _removeUiState.update {
                    it.copy(errorMessage = errorMsg)
                }
            }
        }
    }
    fun clearMessages() {
        _removeUiState.update { it.copy(successMessage = null) }
    }

    fun calculateTankHeightMm(tank: Tank)=calculateTankUseCase.calculateTankHeightMm(tank)
}

data class SensorDetailUiState(
    val sensor: Sensor? = null,
    val tank: Tank? = null,
    val isLoading: Boolean = true
)