package com.smartsense.app.domain.model

data class SensorReading(
    var levelPercent: Float,
    val rawHeightMeters: Double,
    val batteryVoltage: Float,
    val rssi: Int,
    val quality: Int, // 0-3 stars
    val temperatureCelsius: Float,
    val firmwareVersion: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val tankLevelPercentage: Int=0
)
