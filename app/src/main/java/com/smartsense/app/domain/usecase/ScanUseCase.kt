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
import com.smartsense.app.domain.model.Tank
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ScanUseCase @Inject constructor(
    private val repository: SensorRepository,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context

) {
    val isBluetoothEnabled=repository.isBluetoothEnabled

    fun startScan(scanIntervalMillis: Long): Flow<List<Sensor>> = repository.discoverSensors(scanIntervalMillis)

    fun stopScan() = repository.stopScan()

    fun observeRegisteredSensors(scanIntervalMillis: Long): Flow<List<Sensor>> = repository.observeRegisteredSensors(scanIntervalMillis)

    //suspend fun registerSensor(address: String, name: String,uploadSensorData: Boolean) = repository.registerSensor(address,name, uploadSensorData)

    // In SensorScanUseCase.kt
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
                    triggerSync()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "🔥 UseCase Error: Registration failed for $address")
            throw e
        }
    }

    fun filterSensors(sensorsFlow: Flow<List<Sensor>>,
                      queryFlow: Flow<String>): Flow<List<Sensor>> = repository.filterSensors(sensorsFlow,queryFlow)

    fun observeDetailSensor(address: String, scanIntervalMillis: Long): Flow<Sensor?> =
        repository.observeSensorForDetail(address,scanIntervalMillis)

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
                    triggerSync()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "🔥 UseCase Error: Failed to unregister $address")
            throw e
        }
    }


    suspend fun unregisterSensorTankPermanent(address: String) = repository.unregisterSensorTankPermanent(address)


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


    suspend fun getTankConfig(sensorAddress: String) = repository.getTankConfig(sensorAddress)

    suspend fun saveTankConfig(tank: Tank)=repository.saveTankConfig(tank)

    companion object{
        private const val SYNC_WORK_NAME = "sensor_sync_job"
    }
}
