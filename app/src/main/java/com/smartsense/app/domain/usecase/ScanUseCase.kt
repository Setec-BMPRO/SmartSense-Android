package com.smartsense.app.domain.usecase

import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.domain.firebase.AuthRepository
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.Tank
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

class ScanUseCase @Inject constructor(
    private val repository: SensorRepository,
    private val authRepository: AuthRepository,
    private val sharedUseCase: SharedUseCase

) {
    val isBluetoothEnabled=repository.isBluetoothEnabled

    fun startScan(scanIntervalMillis: Long): Flow<List<Sensor>> = repository.discoverSensors(scanIntervalMillis)

    fun stopScan() = repository.stopScan()

    fun observeRegisteredSensors(scanIntervalMillis: Long): Flow<List<Sensor>> = repository.observeRegisteredSensors(scanIntervalMillis)

    suspend fun registerSensor(address: String, name: String, uploadSensorData: Boolean) {
        Timber.i("🛰️ UseCase: Registering sensor $address")

        try {
            // 1. Database work always happens first
            repository.registerSensor(address, name)

            // 2. Decide if we trigger the Cloud Sync
            val currentUser = authRepository.getCurrentUser()

            when {
                !uploadSensorData -> {
                    Timber.tag("SyncTrigger").v("ℹ️ Sync skipped: User preference 'Upload' is OFF.")
                }
                currentUser == null -> {
                    Timber.tag("SyncTrigger").w("⚠️ Sync delayed: User is signed out.")
                }
                else -> {
                    Timber.tag("SyncTrigger").d("🚀 Sync triggered for registered/resurrected sensor.")
                    sharedUseCase.triggerSync()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "🔥 UseCase Error: Registration failed for $address")
            throw e
        }
    }

    fun filterSensors(sensorsFlow: Flow<List<Sensor>>,
                      queryFlow: Flow<String>): Flow<List<Sensor>> = repository.filterSensors(sensorsFlow,queryFlow)

}
