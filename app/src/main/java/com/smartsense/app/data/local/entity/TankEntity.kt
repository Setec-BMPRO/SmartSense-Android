package com.smartsense.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tanks")
data class TankEntity(
    @PrimaryKey
    val sensorAddress: String,
    val name: String = "",
    val tankType: String = "KG_3_7", // TankType enum name
    val customHeightMeters: Double = 0.0,
    val orientation: String = "VERTICAL", // TankOrientation enum name
    val alarmThresholdPercent: Int = 20,
    val region: String = "AUSTRALIA", // TankRegion enum name
    val levelUnit: String = "PERCENT", // TankLevelUnit enum name
    val notificationsEnabled: Boolean = true,
    val notificationFrequency: String = "EVERY_12_HOURS" // NotificationFrequency enum name
)
