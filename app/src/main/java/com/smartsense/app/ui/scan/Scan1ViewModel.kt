package com.smartsense.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.preferences.UserPreferences


import com.smartsense.app.domain.model.Sensor1
import com.smartsense.app.domain.model.UnitSystem
import com.smartsense.app.domain.usecase.SensorScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class Scan1ViewModel @Inject constructor(
    private val userCase: SensorScanUseCase,
    val userPreferences: UserPreferences
) : ViewModel() {

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5000L
    }

    // --- Private State Holders ---
    private val _uiState = MutableStateFlow(SensorListUiState())
    private val _filterQuery = MutableStateFlow("")
    private val _collapsedGroups = MutableStateFlow<Set<String>>(emptySet())

    // --- Public Observables ---
    val uiState: StateFlow<SensorListUiState> = _uiState.asStateFlow()
    val collapsedGroups = _collapsedGroups.asStateFlow()

    // Reactive Preferences (No longer blocking)
    val unitSystem = userPreferences.unitSystem
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSystem.METRIC)

    val groupFilterEnabled = userPreferences.groupFilterEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val deviceSearchFilterEnabled = runBlocking {
        userPreferences.deviceSearchFilterEnabled.first()
    }

    // Single Source of Truth for the List
    val filteredSensors: StateFlow<List<Sensor1>> = userCase.filterSensors(
        sensorsFlow = uiState.map { it.sensors }.distinctUntilChanged(),
        queryFlow = _filterQuery
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private var scanJob: Job? = null
    private var observeJob: Job? = null
    private var autoPairDone = false

    init {
        _uiState.update { it.copy(isBluetoothEnabled = userCase.isBluetoothEnabled) }
    }

    // --- Action Methods ---

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

    fun startObserveRegisteredSensors() {
        if (observeJob?.isActive == true) return
        observeJob = viewModelScope.launch {
            val interval = userPreferences.scanInterval.first().value.toLong() * 1000
            userCase.observeRegisteredSensors(interval)
                .collect { sensors ->
                    _uiState.update { it.copy(sensors = sensors) }
                    if (sensors.isNotEmpty()) autoPairDone = true
                }
        }
    }

    fun stopObserveRegisteredSensors() {
        observeJob?.cancel()
        observeJob = null
    }

    private fun loadAllRegisteredSensors(){
        viewModelScope.launch {
            _uiState.update { state ->
                Timber.i("-----loadAllRegisteredSensors-----")
                state.copy(
                    isScanning = true,
                    sensors = userCase.getAllRegisteredSensors().first()
                )
            }
        }
    }

    private fun autoStartScan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val interval = userPreferences.scanInterval.first().value.toLong() * 1000
            userCase.startScan(interval)
                .catch { e ->
                    Timber.e(e, "Scan failed")
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

    private fun handleAutoPairing(sensors: List<Sensor1>) {
        sensors.firstOrNull { it.syncPressed }?.let { syncSensor ->
            Timber.d("Auto-pairing syncPressed sensor: ${syncSensor.address}")
            autoPairDone = true
            registerSensor(syncSensor.address, "New LPG Device")
        }
    }

    fun registerSensor(address: String, name: String) {
        viewModelScope.launch {
            autoPairDone = true
            userCase.registerSensor(address, name)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        userCase.stopScan()
        super.onCleared()
    }
}

data class SensorListUiState(
    val sensors: List<Sensor1> = emptyList(),
    val discoveredSensors: List<Sensor1> = emptyList(),
    val isScanning: Boolean = false,
    val showDiscovery: Boolean = false,
    val isBluetoothEnabled: Boolean = true,
    val error: String? = null,
    val sortByLevel: Boolean = false
)
