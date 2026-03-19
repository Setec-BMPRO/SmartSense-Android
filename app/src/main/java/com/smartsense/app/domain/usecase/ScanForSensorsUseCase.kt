package com.smartsense.app.domain.usecase

import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.domain.model.Sensor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScanForSensorsUseCase @Inject constructor(
    private val repository: SensorRepository
) {
    operator fun invoke(): Flow<List<Sensor>> = repository.scanForSensors()

    fun stop() = repository.stopScan()
}
