package com.smartsense.app.data.worker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartsense.app.R
import com.smartsense.app.data.local.dao.SensorDao
import com.smartsense.app.data.local.entity.TankEntity
import com.smartsense.app.domain.model.NotificationFrequency
import com.smartsense.app.domain.model.TriggerAlarmUnit
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class TankAlertService : Service() {

    @Inject lateinit var sensorDao: SensorDao

    private val alertChannelId = "tank_alerts_channel"
    private val serviceChannelId = "tank_service_channel"

    // Use a SupervisorJob so one failure doesn't kill the whole scope
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra("EXTRA_ADDRESS") ?: ""
        val level = intent?.getIntExtra("EXTRA_LEVEL", -1) ?: -1

        if (address.isNotEmpty() && level != -1) {
            // 1. Enter Foreground immediately to satisfy Android 12 requirements
            startForeground(1001, createServiceNotification())

            // 2. Process the scan
            processScan(address, level, startId)
        } else {
            // No valid data, stop immediately
            stopSelf(startId)
        }

        // START_NOT_STICKY: If OS kills service, don't restart until a new Intent comes
        return START_NOT_STICKY
    }

    private fun processScan(address: String, currentLevel: Int, startId: Int) {
        serviceScope.launch {
            try {
                val tank = sensorDao.getTank(address) ?: return@launch

                // Logic check: Above or Below
                val isTriggered = when (tank.triggerAlarmUnit.lowercase().trim()) {
                    TriggerAlarmUnit.ABOVE.name.lowercase() -> currentLevel >= tank.alarmThresholdPercent
                    TriggerAlarmUnit.BELOW.name.lowercase() -> currentLevel < tank.alarmThresholdPercent
                    else -> false
                }

                if (isTriggered) {
                    if (shouldNotify(tank, currentLevel)) {
                        sendAlertNotification(tank, currentLevel)
                        saveAlertState(tank.sensorAddress, currentLevel)
                    }
                } else {
                    // Logic: 90 -> 59 -> 89. Reset if we exit the alert zone.
                    resetAlertState(tank.sensorAddress)
                }
            } catch (e: Exception) {
                Timber.e("Error processing scan: ${e.message}")
            } finally {
                // 3. PERFORMANCE: Stop the service once this specific task is finished
                // If 5 scans are running, it only stops after the last startId is reached
                stopSelf(startId)
            }
        }
    }

    private fun shouldNotify(tank: TankEntity, currentLevel: Int): Boolean {
        val prefs = getSharedPreferences("tank_alerts", MODE_PRIVATE)
        val lastLevel = prefs.getInt("last_level_${tank.sensorAddress}", -1)
        val lastTime = prefs.getLong("last_time_${tank.sensorAddress}", 0L)

        val cooldown = NotificationFrequency.fromString(tank.notificationFrequency).timeMillis
        val intervalExpired = (System.currentTimeMillis() - lastTime) >= cooldown

        // Rule: Trigger if First Time, Level Changed, or Interval Expired
        return (lastLevel == -1) || (currentLevel != lastLevel) || intervalExpired
    }

    private fun saveAlertState(address: String, level: Int) {
        getSharedPreferences("tank_alerts", MODE_PRIVATE).edit()
            .putInt("last_level_$address", level)
            .putLong("last_time_$address", System.currentTimeMillis())
            .apply()
    }

    private fun resetAlertState(address: String) {
        getSharedPreferences("tank_alerts", MODE_PRIVATE).edit()
            .putInt("last_level_$address", -1)
            .putLong("last_time_$address", 0L)
            .apply()
    }

    private fun sendAlertNotification(tank: TankEntity, level: Int) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, alertChannelId)
            .setSmallIcon(R.drawable.ic_cloud)
            .setContentTitle("Tank Alert: ${tank.name}")
            .setContentText("Level is ${tank.triggerAlarmUnit} ${tank.alarmThresholdPercent}% (Current: $level%)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        manager.notify(tank.sensorAddress.hashCode(), notification)
    }

    private fun createServiceNotification(): Notification {
        return NotificationCompat.Builder(this, serviceChannelId)
            .setContentTitle("Checking Levels...")
            .setSmallIcon(R.drawable.ic_close) // Use a small, simple icon
            .setPriority(NotificationCompat.PRIORITY_MIN) // Minimize tray presence
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val alertChan = NotificationChannel(alertChannelId, "Tank Level Alerts", NotificationManager.IMPORTANCE_HIGH)

            // Service channel is IMPORTANCE_MIN so it doesn't make sound/vibrate while checking
            val serviceChan = NotificationChannel(serviceChannelId, "Background Tasks", NotificationManager.IMPORTANCE_MIN)

            manager?.createNotificationChannels(listOf(alertChan, serviceChan))
        }
    }

    override fun onBind(intent: Intent): IBinder? = null
}