package com.smartsense.app.domain.usecase
import com.smartsense.app.data.repository.Sensor1Repository
import com.smartsense.app.domain.model.Sensor1
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsUseCase @Inject constructor(
    private val repository: Sensor1Repository
) {

    suspend fun unregisterAllSensors() = repository.unregisterAllSensors()
}
