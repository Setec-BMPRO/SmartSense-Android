package com.smartsense.app.domain.usecase
import com.smartsense.app.data.repository.Sensor1Repository
import com.smartsense.app.domain.model.Sensor1
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SensorDetailUseCase @Inject constructor(
    private val repository: Sensor1Repository
) {
    fun observeSensorForDetail(address: String): Flow<Sensor1?> = repository.observeSensorForDetail(address)
    suspend fun unregisterSensor(address: String) = repository.unregisterSensor(address)
}
