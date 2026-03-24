package com.smartsense.app.domain.model

data class Tank(
    val sensorAddress: String,
    val name: String = "",
    val type: TankType = TankType.KG_3_7,
    val customHeightMeters: Double = 0.0,
    val orientation: TankOrientation = TankOrientation.VERTICAL,
    val alarmThresholdPercent: Int = 20,
    val region: TankRegion = TankRegion.AUSTRALIA,
    val levelUnit: TankLevelUnit = TankLevelUnit.PERCENT,
    val notificationsEnabled: Boolean = true,
    val notificationFrequency: NotificationFrequency = NotificationFrequency.EVERY_12_HOURS
) {
    val effectiveHeightMeters: Double
        get() = if (type == TankType.ARBITRARY) customHeightMeters else type.heightMeters

    val effectiveOrientation: TankOrientation
        get() = if (type == TankType.ARBITRARY) orientation else type.orientation
}
