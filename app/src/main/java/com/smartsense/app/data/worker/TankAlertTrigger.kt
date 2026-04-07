package com.smartsense.app.data.worker

import android.content.Context
import android.content.Intent
import android.os.Build

import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.domain.usecase.DetailUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TankAlertTrigger @Inject constructor(
    private val userPreferences: UserPreferences,
    private val detailUseCase: DetailUseCase,
    @ApplicationContext private val context: Context
    ) {

    /**
     * Orchestrates the notification rules.
     * Checks Global Settings -> Checks Item Settings -> Triggers Service.
     */
    suspend fun checkAndTrigger(address: String, currentLevel: Int) {
        try {
            // 1. RULE: Check Global App Notification Setting
            val isGlobalNotificationEnabled = userPreferences.notificationsEnabled.first()
            if (!isGlobalNotificationEnabled) {
                Timber.d("⏭️ Global notifications are OFF. Skipping alert for $address")
                return
            }

            // 2. RULE: Check Specific Item (Tank) Notification Setting
            // Fetching config from Database via UseCase
            val tankConfig = detailUseCase.getTankConfig(address)
            val isItemNotificationEnabled = tankConfig?.notificationsEnabled ?: false

            if (!isItemNotificationEnabled) {
                Timber.d("⏭️ Item notification is OFF for ${tankConfig?.name ?: address}")
                return
            }

            // 3. BOTH RULES MET: Start the Foreground Service to process the alarm
            Timber.d("✅ Rules Met. Sending to Service -> Address: $address, Level: $currentLevel%")
            startAlertService(address, currentLevel)

        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to execute TankAlertTrigger for $address")
        }
    }

    /**
     * Handles the platform-specific logic for starting a Foreground Service.
     */
    private fun startAlertService(address: String, level: Int) {
        val intent = Intent(context, TankAlertService::class.java).apply {
            action = "ACTION_CHECK_ALARM"
            putExtra("EXTRA_ADDRESS", address)
            putExtra("EXTRA_LEVEL", level)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Timber.e("❌ Could not start TankAlertService: ${e.message}")
        }
    }
}