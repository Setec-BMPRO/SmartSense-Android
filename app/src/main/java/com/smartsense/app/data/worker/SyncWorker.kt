package com.smartsense.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartsense.app.data.repository.SensorRepository

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SensorRepository
) : CoroutineWorker(context, workerParams){

    override suspend fun doWork(): Result {
        return try {
            // 1. Push local changes up
            repository.uploadPendingChanges()

            // 2. Pull cloud changes down
            repository.downloadRemoteChanges()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}