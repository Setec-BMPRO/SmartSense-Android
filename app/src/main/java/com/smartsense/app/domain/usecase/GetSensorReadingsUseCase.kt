package com.smartsense.app.domain.usecase
import com.smartsense.app.data.repository.Sensor1Repository
import com.smartsense.app.domain.model.Sensor1
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSensorReadingsUseCase @Inject constructor(
    private val repository: Sensor1Repository
) {
    fun observe(sensorAddress: String): Flow<Sensor1?> =
        repository.observeSensor(sensorAddress)
}
