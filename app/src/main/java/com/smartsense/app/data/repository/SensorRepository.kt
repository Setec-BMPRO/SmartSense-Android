package com.smartsense.app.data.repository

import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.TankPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SensorRepository {
    fun getSensors(): Flow<List<Sensor>>
    fun getSensorByAddress(address: String): Flow<Sensor?>
    suspend fun updateTankPreset(address: String, preset: TankPreset)
    suspend fun removeSensor(address: String)
    fun scanForSensors(): Flow<List<Sensor>>
    fun stopScan()
    val isScanning: StateFlow<Boolean>
    val sensorCount: StateFlow<Int>
}
