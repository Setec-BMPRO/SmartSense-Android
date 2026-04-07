package com.smartsense.app.domain.usecase
import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.domain.firebase.AuthRepository
import com.smartsense.app.domain.model.Sensor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

class SettingsUseCase @Inject constructor(
    private val repository: SensorRepository,
    private val authRepository: AuthRepository,
    private val scanUseCase: ScanUseCase,
    private val sharedUseCase: SharedUseCase
) {
    suspend fun unregisterAllSensors(uploadSensorData: Boolean) {
        Timber.i("🗑️ UseCase: Unregistering ALL sensors and tanks")

        try {
            // 1. Mark ALL local records as DELETED (Soft Delete)
            // This ensures they stay in the DB with 'DELETED' status until the SyncWorker confirms
            repository.markAllSensorsTanksAsDeleted()

            // 2. Business Logic: Sync Triggering
            val currentUser = authRepository.getCurrentUser()

            when {
                !uploadSensorData -> {
                    Timber.tag("SyncTrigger").v("ℹ️ Bulk Sync skipped: 'Upload Sensor Data' is OFF.")
                }
                currentUser == null -> {
                    Timber.tag("SyncTrigger").w("⚠️ Bulk Sync delayed: User signed out.")
                }
                else -> {
                    Timber.tag("SyncTrigger").d("🚀 Bulk Sync triggered for all deleted items.")
                    scanUseCase.triggerSync()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "🔥 UseCase Error: Failed to unregister all sensors")
            throw e
        }
    }

    fun getAllRegisteredSensors(): Flow<List<Sensor>> =
        sharedUseCase.getAllRegisteredSensors()

}
