package com.smartsense.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
            val upCount = repository.uploadPendingChanges()
            val downCount = repository.downloadRemoteChanges()

            // Create the output map
            val outputData = workDataOf(
                "KEY_UPLOADED_COUNT" to upCount,
                "KEY_DOWNLOADED_COUNT" to downCount
            )

            Result.success(outputData)
        } catch (e: Exception) {
            Result.retry()
        }
    }
}