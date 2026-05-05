package com.smartsense.app.domain.model

data class SensorReading(
    val rawHeightMeters: Double=0.0,
    val batteryVoltage: Float,
    val rssi: Int,
    val quality: Int, // 0-3 stars
    val temperatureCelsius: Float,
    val firmwareVersion: String = "",
    var timestampMillis: Long = System.currentTimeMillis(),
    var tankLevelPercentage: Int=0,
    val deviceMAC: String="",
    /** Setec/Sigmawit protocol version, formatted as "<major>.<minor>". Empty for non-Setec sensors. */
    val protocolVersion: String = "",
    /** Raw "Sensor type" byte (Setec spec byte 26). 0 means not reported. */
    val sensorTypeCode: Int = 0,
    /**
     * Reporting interval in seconds, decoded from the Setec/Sigmawit reporting-interval byte.
     * 0 means the sensor doesn't expose this field.
     */
    val reportingIntervalSeconds: Int = 0
)
