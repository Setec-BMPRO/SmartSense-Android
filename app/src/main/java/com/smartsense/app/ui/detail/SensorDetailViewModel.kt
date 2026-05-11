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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
    private val calculateTankUseCase: CalculateTankUseCase,
    private val qualityCalculator: com.smartsense.app.data.quality.ReadingQualityCalculator
) : ViewModel() {

    /**
     * Diagnostic snapshot of the quality-buffer for this sensor. The detail view's
     * Debug section pulls this every time the bound sensor changes so the user can
     * see the exact samples (and their deviation from the mean) that drove the
     * currently-displayed quality rating.
     */
    fun qualitySnapshot(): com.smartsense.app.data.quality.ReadingQualityCalculator.QualitySnapshot =
        qualityCalculator.snapshot(sensorAddress)

    val sensorAddress: String =
        savedStateHandle[EXTRA_SENSOR_ADDRESS] ?: ""

    val unitSystem: UnitSystem = runBlocking {
        userPreferences.unitSystem.first()
    }

    /** Live flag for the hidden Developer Mode toggle (Settings → tap version 7×).
     *  Drives the Quality Buffer card's visibility on the detail screen. Eagerly
     *  collected so the first render after [bindSensor] already has the right value. */
    val developerModeEnabled: StateFlow<Boolean> = userPreferences.developerModeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // The Update-Rate setting was retired — the detail screen now ticks at the
    // sensor's own broadcast cadence (or the AUTO fallback before the first
    // broadcast arrives).
    private val initialIntervalMs: Long =
        ScanIntervals.AUTO_FALLBACK_SECONDS.toLong() * 1000L

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
        launchObserveDetailJob(initialIntervalMs)
    }

    /**
     * Observe the sensor's live readings at [intervalMs]. After each emission the
     * collector checks whether the sensor's reported broadcast cadence implies a
     * different interval and, if so, restarts itself with the new value.
     */
    private fun launchObserveDetailJob(intervalMs: Long) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            useCase.observeDetailSensor(sensorAddress, intervalMs)
                .collect { sensor ->
                    _uiState.update {
                        it.copy(sensor = sensor, isLoading = false)
                    }
                    sensor?.let {
                        val level = it.tankLevel?.percentage?.toInt() ?: -1
                        alertTrigger.checkAndTrigger(
                            address = it.address,
                            currentLevel = level
                        )
                    }

                    val derivedSeconds = sensor?.reading?.reportingIntervalSeconds ?: 0
                    if (derivedSeconds > 0) {
                        val derivedMs = derivedSeconds.toLong() * 1000L
                        if (derivedMs != intervalMs) {
                            launchObserveDetailJob(derivedMs)
                            return@collect
                        }
                    }
                }
        }
    }

    fun stopObserveDetailSensor() {
        observeJob?.cancel()
        observeJob = null
    }


    fun loadTankConfig(onLoaded: ((Tank?) -> Unit)? = null) {
        viewModelScope.launch {
            val tank = useCase.getTankConfig(sensorAddress)
            _uiState.update { state ->
                state.copy(tank = tank)
            }
            onLoaded?.invoke(tank)
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