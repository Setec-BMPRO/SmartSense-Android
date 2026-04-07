package com.smartsense.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smartsense.app.data.repository.SensorRepository

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SensorRepository
) : CoroutineWorker(context, workerParams){

    override suspend fun doWork(): Result {
        // Unique ID for this specific execution to track in logs
        val workId = id.toString().take(8)
        Timber.i("🔄 [SyncWorker-$workId] Work started.")

        return try {
            // 1. Uploading
            Timber.d("⏳ [SyncWorker-$workId] Uploading pending changes...")
            val upCount = repository.uploadPendingChanges()

            // 2. Downloading
            Timber.d("⏳ [SyncWorker-$workId] Downloading remote changes...")
            val downCount = repository.downloadRemoteChanges()

            // 3. Success & Data Packaging
            val outputData = workDataOf(
                "KEY_UPLOADED_COUNT" to upCount,
                "KEY_DOWNLOADED_COUNT" to downCount
            )

            Timber.i("✅ [SyncWorker-$workId] Success! (⬆️ Up: $upCount, ⬇️ Down: $downCount)")
            Result.success(outputData)

        } catch (e: Exception) {
            // Timber.e(exception, message) preserves the stack trace for debugging
            Timber.e(e, "❌ [SyncWorker-$workId] Critical failure during sync.")

            // Return retry to trigger WorkManager's backoff policy
            Timber.w("🔁 [SyncWorker-$workId] Scheduling retry...")
            Result.retry()
        }
    }
}