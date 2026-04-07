package com.smartsense.app.domain.usecase

import com.google.firebase.auth.FirebaseUser
import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.domain.firebase.AuthRepository
import com.smartsense.app.domain.model.Sensor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AccountUseCase @Inject constructor(
    private val repository: SensorRepository,
    private val sharedUseCase: SharedUseCase,
    private val authRepository: AuthRepository
    ) {
    suspend fun unregisterSensor(address: String, uploadSensorData: Boolean):Result<Boolean> = sharedUseCase.unregisterSensor(address,uploadSensorData)
    suspend fun unregisterSensorTankPermanent(address: String) = repository.unregisterSensorTankPermanent(address)

    fun getAllRegisteredSensors(): Flow<List<Sensor>> =
        sharedUseCase.getAllRegisteredSensors()

    suspend fun resetLocalDataForNewAccount()=repository.resetLocalDataForNewAccount()


    fun getCurrentUser(): FirebaseUser?{
        return authRepository.getCurrentUser()
    }
}
