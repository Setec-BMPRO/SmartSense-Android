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
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class DetailUseCase @Inject constructor(
    private val repository: SensorRepository,
    private val sharedUseCase: SharedUseCase
) {
    fun observeDetailSensor(address: String, scanIntervalMillis: Long): Flow<Sensor?> =
        repository.observeSensorForDetail(address,scanIntervalMillis)

    suspend fun unregisterSensor(address: String, uploadSensorData: Boolean)=sharedUseCase.unregisterSensor(address,uploadSensorData)

}
