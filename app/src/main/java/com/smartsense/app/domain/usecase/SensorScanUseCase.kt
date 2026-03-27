package com.smartsense.app.domain.usecase

import com.smartsense.app.data.repository.Sensor1Repository
import com.smartsense.app.domain.model.Sensor1
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SensorScanUseCase @Inject constructor(
    private val repository: Sensor1Repository
) {
    val isBluetoothEnabled=repository.isBluetoothEnabled

    fun startScan(): Flow<List<Sensor1>> = repository.discoverSensors()

    fun stopScan() = repository.stopScan()

    fun observeRegisteredSensors(): Flow<List<Sensor1>> = repository.observeRegisteredSensors()

    suspend fun registerSensor(address: String, name: String) = repository.registerSensor(address,name)
}
