package com.smartsense.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.data.worker.TankAlertTrigger
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.UnitSystem
import com.smartsense.app.domain.usecase.CalculateTankUseCase
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
    private val calculateTankUseCase: CalculateTankUseCase
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
    private val pairingInFlight = mutableSetOf<String>()

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

    /** Hidden developer-mode flag (Settings → tap version 7×). Gates internal-only menu
     *  entries (Load/Unload mock data) so they're not surfaced to end users on release
     *  builds. Eagerly collected so the toolbar menu setup at view creation sees the
     *  correct value without waiting for a coroutine to fire. */
    val developerModeEnabled = userPreferences.developerModeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        _uiState.update { it.copy(isBluetoothEnabled = useCase.isBluetoothEnabled) }
    }

    // -------------------------------------------------------------------------
    // 📡 Scanning & Observation Logic
    // -------------------------------------------------------------------------

    fun startObserveRegisteredSensors() {
        if (observeJob?.isActive == true) return
        launchObserveJob(autoFallbackIntervalMs())
    }

    /**
     * Launch (or relaunch) the registered-sensors collector at [intervalMs]. The
     * collector re-evaluates the auto-derived interval after each emission and, if
     * the sensors now imply a different cadence, swaps the running flow for one at
     * the new interval.
     *
     * The "Update Rate" setting was retired; the cadence always tracks the smallest
     * `reportingIntervalSeconds` reported by registered sensors, falling back to
     * [com.smartsense.app.domain.model.ScanIntervals.AUTO_FALLBACK_SECONDS] when no
     * reading carries one yet (e.g. CC2540/NRF52, or before the first broadcast).
     */
    private fun launchObserveJob(intervalMs: Long) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            Timber.tag(TAG).d("Observing registered sensors at $intervalMs ms")
            useCase.observeRegisteredSensors(intervalMs)
                .collect { sensors ->
                    _uiState.update { it.copy(sensors = sensors) }
                    pairingInFlight -= sensors.map { it.address }.toSet()

                    sensors.forEach { scannedSensor ->
                        val level = scannedSensor.tankLevel?.percentage?.toInt() ?: -1
                        alertTrigger.checkAndTrigger(
                            address = scannedSensor.address,
                            currentLevel = level
                        )
                    }

                    val derived = sensors.deriveAutoIntervalMs()
                    if (derived != intervalMs && derived > 0) {
                        launchObserveJob(derived)
                        return@collect
                    }
                }
        }
    }

    private fun autoFallbackIntervalMs(): Long =
        com.smartsense.app.domain.model.ScanIntervals.AUTO_FALLBACK_SECONDS.toLong() * 1000L

    private fun List<Sensor>.deriveAutoIntervalMs(): Long {
        val intervals = mapNotNull { it.reading?.reportingIntervalSeconds?.takeIf { s -> s > 0 } }
        val seconds = intervals.minOrNull()
            ?: com.smartsense.app.domain.model.ScanIntervals.AUTO_FALLBACK_SECONDS
        return seconds.toLong() * 1000L
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
            // Discovery-side sampling is independent of the per-sensor reporting cadence —
            // use the AUTO fallback (~3 s) as a reasonable, fixed sampling rate so newly
            // pressed sync buttons surface promptly.
            val interval = autoFallbackIntervalMs()

            // Fast path: monitor raw BLE readings for sync-pressed devices
            // to trigger auto-pairing immediately without waiting for the sample interval
            launch {
                useCase.observeRawReadings()
                    .collect { scanned ->
                        if (scanned.parsed?.syncPressed == true) {
                            Timber.tag(TAG).d("🔔 Raw syncPressed reading: ${scanned.address} (rssi=${scanned.rssi})")
                        }
                        if (scanned.parsed?.syncPressed == true && shouldPair(scanned.address)) {
                            val sensorType = scanned.parsed.sensorType
                            Timber.tag(TAG).d("Fast auto-pairing syncPressed sensor: ${scanned.address}")
                            registerSensor(scanned.address, calculateTankUseCase.calculateName(sensorType))
                        }
                    }
            }

            useCase.startScan(interval)
                .catch { e ->
                    Timber.tag(TAG).e(e, "Scan failed")
                    val btAction = android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "BLE scan failed",
                            errorTip = "Toggle Bluetooth off and on in Settings, then reopen the app.",
                            settingsAction = btAction,
                            isScanning = false
                        )
                    }
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
        sensors.filter { it.syncPressed && shouldPair(it.address) }.forEach { syncSensor ->
            Timber.tag(TAG).d("Auto-pairing syncPressed sensor detected: ${syncSensor.address}")
            registerSensor(syncSensor.address, calculateTankUseCase.calculateName(syncSensor.sensorType))
        }
    }

    private fun shouldPair(address: String): Boolean {
        if (pairingInFlight.contains(address)) return false
        if (_uiState.value.sensors.any { it.address == address }) return false
        pairingInFlight += address
        return true
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

    val mockDataLoaded: StateFlow<Boolean> = _uiState
        .map { state -> state.sensors.any { it.address.startsWith("MO:CK:") } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleMockData() {
        viewModelScope.launch {
            if (mockDataLoaded.value) {
                Timber.tag(TAG).i("Unloading mock data")
                useCase.unloadMockData()
            } else {
                Timber.tag(TAG).i("Seeding mock data")
                useCase.seedMockData()
            }
        }
    }

    fun registerSensor(address: String, name: String) {
        viewModelScope.launch {
            Timber.tag(TAG).i("Registering sensor: $address ($name)")
            try {
                val uploadEnabled = userPreferences.uploadSensorData.first()
                useCase.registerSensor(address, name, uploadEnabled)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to register sensor $address")
                pairingInFlight -= address
            }
        }
    }

    fun setPermissionError(error: String, tip: String?, settingsAction: String) {
        _uiState.update {
            it.copy(error = error, errorTip = tip, settingsAction = settingsAction, isScanning = false)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, errorTip = null, settingsAction = null) }
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
    val errorTip: String? = null,
    val settingsAction: String? = null,
    val sortByLevel: Boolean = false
)