package com.smartsense.app.data.ble

import com.smartsense.app.domain.model.Sensor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BleScanner {
    fun startScan(): Flow<List<Sensor>>
    fun stopScan()
    val isScanning: StateFlow<Boolean>
    fun getInitialSensors(): List<Sensor>
    fun removePairedSensor(address: String) {}
}
