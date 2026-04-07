package com.smartsense.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.data.worker.TankAlertTrigger
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.UnitSystem
import com.smartsense.app.domain.usecase.ScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val useCase: ScanUseCase,
    private val userPreferences: UserPreferences,
    private val alertTrigger: TankAlertTrigger,
) : ViewModel() {

    companion object {
        private const val TAG = "Scan1ViewModel"
    }

    // -------------------------------------------------------------------------
    // 🔒 Private State Holders
    // -------------------------------------------------------------------------

    private val _uiState = MutableStateFlow(SensorListUiState())
    private val _filterQuery = MutableStateFlow("")
    private val _collapsedGroups = MutableStateFlow<Set<String>>(emptySet())

    private var scanJob: Job? = null
    private var observeJob: Job? = null
    private var autoPairDone = false

    // -------------------------------------------------------------------------
    // 🌎 Public Observables (UI State)
    // -------------------------------------------------------------------------

    val uiState: StateFlow<SensorListUiState> = _uiState.asStateFlow()
    val collapsedGroups = _collapsedGroups.asStateFlow()

    // Single Source of Truth for the filtered sensor list
    val filteredSensors: StateFlow<List<Sensor>> = useCase.filterSensors(
        sensorsFlow = uiState.map { it.sensors }.distinctUntilChanged(),
        queryFlow = _filterQuery
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    // -------------------------------------------------------------------------
    // ⚙️ Reactive Preferences
    // -------------------------------------------------------------------------

    val unitSystem = userPreferences.unitSystem
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSystem.METRIC)

    val groupFilterEnabled = userPreferences.groupFilterEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // REFACTORED: Removed runBlocking for better performance
    val deviceSearchFilterEnabled = userPreferences.deviceSearchFilterEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        _uiState.update { it.copy(isBluetoothEnabled = useCase.isBluetoothEnabled) }
    }

    // -------------------------------------------------------------------------
    // 📡 Scanning & Observation Logic
    // -------------------------------------------------------------------------

    fun startObserveRegisteredSensors() {
        if (observeJob?.isActive == true) return

        observeJob = viewModelScope.launch {
            val interval = userPreferences.scanInterval.first().value.toLong() * 1000
            Timber.tag(TAG).d("Starting observation with interval: $interval ms")

            useCase.observeRegisteredSensors(interval)
                .collect { sensors ->
                    _uiState.update { it.copy(sensors = sensors) }
                    if (sensors.isNotEmpty()) autoPairDone = true

                    sensors.forEach { scannedSensor ->
                        val level = scannedSensor.tankLevel?.percentage?.toInt() ?: -1
                        alertTrigger.checkAndTrigger(
                            address = scannedSensor.address,
                            currentLevel = level
                        )
                    }
                }
        }
    }

    fun stopObserveRegisteredSensors() {
        Timber.tag(TAG).d("Stopping sensor observation")
        observeJob?.cancel()
        observeJob = null
    }

    private fun autoStartScan() {
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val interval = userPreferences.scanInterval.first().value.toLong() * 1000

            useCase.startScan(interval)
                .catch { e ->
                    Timber.tag(TAG).e(e, "Scan failed")
                    _uiState.update { it.copy(error = e.message, isScanning = false) }
                }
                .collect { freshlyScannedSensors ->
                    handleAutoPairing(freshlyScannedSensors)
                    _uiState.update { state ->
                        state.copy(
                            discoveredSensors = freshlyScannedSensors.sortedByDescending { it.name }
                        )
                    }
                }
        }
    }

    private fun handleAutoPairing(sensors: List<Sensor>) {
        sensors.firstOrNull { it.syncPressed }?.let { syncSensor ->
            Timber.tag(TAG).d("Auto-pairing syncPressed sensor detected: ${syncSensor.address}")
            autoPairDone = true
            registerSensor(syncSensor.address, "New LPG Device")
        }
    }

    // -------------------------------------------------------------------------
    // ✍️ UI Action Methods
    // -------------------------------------------------------------------------

    fun setFilterQuery(query: String) {
        _filterQuery.value = query
    }

    fun toggleGroup(groupName: String) {
        _collapsedGroups.update { current ->
            if (current.contains(groupName)) current - groupName else current + groupName
        }
    }

    fun onPermissionsGranted() {
        if (scanJob?.isActive != true) {
            autoStartScan()
        }
    }

    fun registerSensor(address: String, name: String) {
        viewModelScope.launch {
            Timber.tag(TAG).i("Registering sensor: $address ($name)")
            autoPairDone = true
            val uploadEnabled = userPreferences.uploadSensorData.first()
            useCase.registerSensor(address, name, uploadEnabled)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        Timber.tag(TAG).d("ViewModel cleared, stopping BLE scan")
        useCase.stopScan()
        super.onCleared()
    }
}

// -------------------------------------------------------------------------
// 📦 UI State Data Class
// -------------------------------------------------------------------------

data class SensorListUiState(
    val sensors: List<Sensor> = emptyList(),
    val discoveredSensors: List<Sensor> = emptyList(),
    val isScanning: Boolean = false,
    val showDiscovery: Boolean = false,
    val isBluetoothEnabled: Boolean = true,
    val error: String? = null,
    val sortByLevel: Boolean = false
)