package com.smartsense.app.domain.usecase
import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.domain.firebase.AuthRepository
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.Tank
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

class TankSettingUseCase @Inject constructor(
    private val repository: SensorRepository
) {
    suspend fun getTankConfig(sensorAddress: String) = repository.getTankConfig(sensorAddress)
    suspend fun saveTankConfig(tank: Tank)=repository.saveTankConfig(tank)

}
