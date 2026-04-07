package com.smartsense.app.domain.usecase
import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.domain.firebase.AuthRepository
import com.smartsense.app.domain.model.Sensor
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

class SharedUseCase @Inject constructor(
    private val repository: SensorRepository,
    private val authRepository: AuthRepository,
    private val scanUseCase: ScanUseCase
) {
    suspend fun unregisterSensor(address: String, uploadSensorData: Boolean) {
        Timber.i("🗑️ UseCase: Unregistering sensor $address")

        try {
            // 1. Perform the local database update (Mark as DELETED)
            repository.markSensorTankAsDeleted(address)

            // 2. Business Logic: Should we trigger a Cloud Sync?
            val currentUser = authRepository.getCurrentUser()

            when {
                !uploadSensorData -> {
                    Timber.tag("SyncTrigger").v("ℹ️ Sync skipped: User disabled 'Upload Sensor Data'.")
                }
                currentUser == null -> {
                    Timber.tag("SyncTrigger").w("⚠️ Sync delayed: User signed out. Will sync on next login.")
                }
                else -> {
                    Timber.tag("SyncTrigger").d("🚀 Sync triggered: User authenticated and sync enabled.")
                    scanUseCase.triggerSync()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "🔥 UseCase Error: Failed to unregister $address")
            throw e
        }
    }

    fun getAllRegisteredSensors(): Flow<List<Sensor>> =
        repository.getAllRegisteredSensors()

}
