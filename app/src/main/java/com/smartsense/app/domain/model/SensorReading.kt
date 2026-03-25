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
    val tankLevelPercentage: Int=0,
    val batteryPercent: Float=((batteryVoltage - 2.0f) / 1.6f * 100f).coerceIn(0f, 100f)
)
