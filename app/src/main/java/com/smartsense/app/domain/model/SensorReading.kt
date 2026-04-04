package com.smartsense.app.domain.model

data class SensorReading(
    val rawHeightMeters: Double=0.0,
    val batteryVoltage: Float,
    val rssi: Int,
    val quality: Int, // 0-3 stars
    val temperatureCelsius: Float,
    val firmwareVersion: String = "",
    var timestampMillis: Long = System.currentTimeMillis(),
    val tankLevelPercentage: Int=0,
    val deviceMAC: String=""
)
