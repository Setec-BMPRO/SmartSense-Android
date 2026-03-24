package com.smartsense.app.domain.usecase

import com.smartsense.app.data.repository.Sensor1Repository
import com.smartsense.app.domain.model.Sensor1
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScanForSensors1UseCase @Inject constructor(
    private val repository: Sensor1Repository
) {
    fun startScan(): Flow<List<Sensor1>> = repository.discoverSensors()

    fun stopScan() = repository.stopScan()
}
