package com.smartsense.app.data.ble

import com.smartsense.app.domain.model.Sensor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real BLE scanner implementation stub.
 * Delegates to MockBleScanner for now. When real Mopeka sensors are available,
 * implement actual BLE scanning using BluetoothLeScanner and parse
 * manufacturer-specific advertisement data.
 */
@Singleton
class BleScannerImpl @Inject constructor(
    private val mockBleScanner: MockBleScanner
) : BleScanner {

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    override fun getInitialSensors(): List<Sensor> = mockBleScanner.getInitialSensors()

    override fun startScan(): Flow<List<Sensor>> {
        // TODO: Implement real BLE scanning with BluetoothLeScanner
        // - Request BLUETOOTH_SCAN permission (API 31+)
        // - Use ScanFilter for Mopeka service UUIDs
        // - Parse ScanResult manufacturer data
        // For now, delegate to mock
        return mockBleScanner.startScan()
    }

    override fun stopScan() {
        mockBleScanner.stopScan()
    }
}
