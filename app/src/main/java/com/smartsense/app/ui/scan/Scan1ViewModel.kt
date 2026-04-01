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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

data class SensorListUiState(
    val sensors: List<Sensor1> = emptyList(),
    val discoveredSensors: List<Sensor1> = emptyList(),
    val isScanning: Boolean = false,
    val showDiscovery: Boolean = false,
    val isBluetoothEnabled: Boolean = true,
    val error: String? = null,
    val sortByLevel: Boolean = false
)

@HiltViewModel
class Scan1ViewModel @Inject constructor(
    private val userCase: SensorScanUseCase,
    val userPreferences: UserPreferences
) : ViewModel() {

    companion object {
        private const val TAG = "SensorListVM"
    }

    private val _uiState = MutableStateFlow(SensorListUiState())
    val uiState: StateFlow<SensorListUiState> = _uiState.asStateFlow()

    val unitSystem: UnitSystem = runBlocking {
        userPreferences.unitSystem.first()
    }

    val deviceSearchFilterEnabled: Boolean = runBlocking {
        userPreferences.deviceSearchFilterEnabled.first()
    }

    val groupFilterEnabled: Boolean = runBlocking {
        userPreferences.groupFilterEnabled.first()
    }

    private val _filterQuery = MutableStateFlow("")

    // The UI should observe THIS instead of uiState.sensors
    val filteredSensors: StateFlow<List<Sensor1>> = combine(
        uiState.map { it.sensors }.distinctUntilChanged(),
        _filterQuery
    ) { sensors, query ->
        if (query.isBlank()) {
            sensors
        } else {
            sensors.filter { sensor ->
                sensor.name?.contains(query, ignoreCase = true) == true ||
                        sensor.address.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setFilterQuery(query: String) {
        _filterQuery.value = query
    }

    private var scanJob: Job? = null
    private var observeJob: Job? = null
    private var autoPairDone = false

    init {
        _uiState.update {
            it.copy(isBluetoothEnabled = userCase.isBluetoothEnabled)
        }

    }

    fun onPermissionsGranted() {
        if (scanJob?.isActive != true) {
            _uiState.update { currentState ->
                currentState.copy(
                    isScanning = true
                )
            }
            autoStartScan()
        }
    }

    fun startObserveRegisteredSensors() {
        if(observeJob?.isActive==true) return
        observeJob = viewModelScope.launch {
            userCase.observeRegisteredSensors(userPreferences.scanInterval.first().value.toLong()*1000)
                .collect { sensors ->
                    _uiState.update { it.copy(sensors = sensors) }
                    // Mark auto-pair done if we already have sensors
                    if (sensors.isNotEmpty()) autoPairDone = true
                }
        }
    }

    fun stopObserveRegisteredSensors() {
        observeJob?.cancel() // This "stops" the flow
        observeJob=null
    }

    private fun autoStartScan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            userCase.startScan(userPreferences.scanInterval.first().value.toLong()*1000)
                .catch { e -> _uiState.update { it.copy(error = e.message, isScanning = false) } }
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
        // Auto-pair: if syncPressed detected and no sensors registered yet,
        // and will auto close discovery after registered
        //if (!autoPairDone && _uiState.value.sensors.isEmpty()) {
            sensors.firstOrNull { it.syncPressed }?.let { syncSensor ->
                Timber.d("Auto-pairing syncPressed sensor: ${syncSensor.address}")
                autoPairDone = true
                registerSensor(syncSensor.address, "New LPG Device")
            }
        //}
    }

    fun registerSensor(address: String, name: String) {
        viewModelScope.launch {
            autoPairDone = true
            userCase.registerSensor(address, name)
            //closeDiscovery()
        }
    }

//    fun toggleSort() {
//        _uiState.update { state ->
//            val newSortByLevel = !state.sortByLevel
//            val sorted = if (newSortByLevel) {
//                state.sensors.sortedByDescending { it.reading?.levelPercent ?: 0f }
//            } else {
//                state.sensors.sortedBy { it.name }
//            }
//            state.copy(sortByLevel = newSortByLevel, sensors = sorted)
//        }
//    }


    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }


    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        userCase.stopScan()
    }
}
