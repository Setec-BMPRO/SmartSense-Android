package com.smartsense.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smartsense.app.domain.model.DEFAULT_ALARM_THRESHOLD_PERCENT
import com.smartsense.app.domain.model.DEFAULT_NOTIFICATION_ENABLED
import com.smartsense.app.domain.model.NotificationFrequency
import com.smartsense.app.domain.model.QualityThreshold
import com.smartsense.app.domain.model.TankLevelUnit
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankRegion
import com.smartsense.app.domain.model.TankType
import com.smartsense.app.domain.model.TriggerAlarmUnit

@Entity(tableName = "tanks")
data class TankEntity(
    @PrimaryKey
    val sensorAddress: String,
    val name: String = "",
    val tankType: String = TankType.KG_3_7.name, // TankType enum name
    val customHeightMeters: Double = 0.0,
    val orientation: String = TankOrientation.default().name, // TankOrientation enum name
    val alarmThresholdPercent: Int = DEFAULT_ALARM_THRESHOLD_PERCENT,
    val region: String = TankRegion.default().name, // TankRegion enum name
    val levelUnit: String = TankLevelUnit.default().name, // TankLevelUnit enum name
    val notificationsEnabled: Boolean = DEFAULT_NOTIFICATION_ENABLED,
    val notificationFrequency: String = NotificationFrequency.default().name, // NotificationFrequency enum name
    val triggerAlarmUnit: String= TriggerAlarmUnit.default().name,
    val qualityThreshold: String= QualityThreshold.default().name
)
