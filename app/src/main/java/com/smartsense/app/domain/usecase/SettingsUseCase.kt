package com.smartsense.app.domain.usecase
import com.smartsense.app.data.repository.SensorRepository
import javax.inject.Inject

class SettingsUseCase @Inject constructor(
    private val repository: SensorRepository
) {

    suspend fun unregisterAllSensors() = repository.unregisterAllSensors()
}
