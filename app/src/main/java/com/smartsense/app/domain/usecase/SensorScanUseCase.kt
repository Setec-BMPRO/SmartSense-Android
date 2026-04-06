package com.smartsense.app.domain.usecase

import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.domain.model.Sensor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SensorScanUseCase @Inject constructor(
    private val repository: SensorRepository
) {
    val isBluetoothEnabled=repository.isBluetoothEnabled

    fun startScan(scanIntervalMillis: Long): Flow<List<Sensor>> = repository.discoverSensors(scanIntervalMillis)

    fun stopScan() = repository.stopScan()

    fun observeRegisteredSensors(scanIntervalMillis: Long): Flow<List<Sensor>> = repository.observeRegisteredSensors(scanIntervalMillis)

    suspend fun registerSensor(address: String, name: String,uploadSensorData: Boolean) = repository.registerSensor(address,name, uploadSensorData)

    fun filterSensors(sensorsFlow: Flow<List<Sensor>>,
                      queryFlow: Flow<String>): Flow<List<Sensor>> = repository.filterSensors(sensorsFlow,queryFlow)

    fun observeDetailSensor(address: String, scanIntervalMillis: Long): Flow<Sensor?> =
        repository.observeSensorForDetail(address,scanIntervalMillis)

    suspend fun unregisterSensor(address: String,uploadSensorData: Boolean) = repository.unregisterSensor(address,uploadSensorData)

    fun getAllRegisteredSensors():Flow<List<Sensor>> = repository.getAllRegisteredSensors()

    fun triggerSync()=repository.triggerSync()

    suspend fun getTankConfig(sensorAddress: String) = repository.getTankConfig(sensorAddress)
}
