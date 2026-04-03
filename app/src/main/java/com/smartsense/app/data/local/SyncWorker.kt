package com.smartsense.app.data.local

import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartsense.app.data.repository.Sensor1Repository
import com.smartsense.app.data.repository.SensorRepository

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: Sensor1Repository
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