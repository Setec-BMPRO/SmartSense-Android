package com.smartsense.app.domain.usecase
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.data.worker.SyncWorker
import com.smartsense.app.domain.firebase.AuthRepository
import com.smartsense.app.domain.model.Sensor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SharedUseCase @Inject constructor(
    private val repository: SensorRepository,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) {
    suspend fun unregisterSensor(address: String, uploadSensorData: Boolean): Result<Boolean> {
        Timber.i("🗑️ UseCase: Unregistering sensor $address")

        return try {
            // 1. Local update
            repository.markSensorTankAsDeleted(address)

            // 2. Sync Logic
            val currentUser = authRepository.getCurrentUser()
            val syncTriggered = when {
                !uploadSensorData -> {
                    Timber.tag("SyncTrigger").v("ℹ️ Sync skipped: Upload disabled.")
                    false
                }
                currentUser == null -> {
                    Timber.tag("SyncTrigger").w("⚠️ Sync delayed: User signed out.")
                    false
                }
                else -> {
                    Timber.tag("SyncTrigger").d("🚀 Sync triggered for $address")
                    triggerSync()
                    true
                }
            }

            // Return success with a boolean indicating if sync was actually started
            Result.success(syncTriggered)

        } catch (e: Exception) {
            Timber.e(e, "🔥 UseCase Error: Failed to unregister $address")
            Result.failure(e)
        }
    }

    fun getAllRegisteredSensors(): Flow<List<Sensor>> =
        repository.getAllRegisteredSensors()


    fun triggerSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }


    companion object{
        const val SYNC_WORK_NAME = "sensor_sync_job"
    }

}
