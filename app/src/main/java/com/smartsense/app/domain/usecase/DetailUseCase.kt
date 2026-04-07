package com.smartsense.app.domain.usecase

import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.domain.model.Sensor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DetailUseCase @Inject constructor(
    private val repository: SensorRepository,
    private val sharedUseCase: SharedUseCase
) {
    fun observeDetailSensor(address: String, scanIntervalMillis: Long): Flow<Sensor?> =
        repository.observeSensorForDetail(address,scanIntervalMillis)

    suspend fun unregisterSensor(address: String, uploadSensorData: Boolean):Result<Boolean> = sharedUseCase.unregisterSensor(address,uploadSensorData)

    suspend fun getTankConfig(sensorAddress: String) = repository.getTankConfig(sensorAddress)
}
