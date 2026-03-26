package com.smartsense.app.domain.model

data class Sensor1(
    val address: String,
    var name: String?,
    val advertisedName: String?=null,
    val sensorType: MopekaSensorType?,
    val syncPressed: Boolean = false,
    val reading: SensorReading?=null,
    val tank: Tank?=null,
    val lastSeenMillis: Long = System.currentTimeMillis(),
    val level: TankLevel= TankLevel(reading?.levelPercent?:0F),
    val tankLevelPercentage: Int=0,
    val readQuality: String? = when {
        reading?.quality == 3 -> "Excellent"
        reading?.quality == 2 -> "Good"
        reading?.quality == 1 -> "Poor"
        else -> "No Signal"
    }
){
    val signalStrength: SignalStrength
        get() = when {
            (reading?.rssi?:0) >= -50 -> SignalStrength.EXCELLENT
            (reading?.rssi?:0) >= -65 -> SignalStrength.GOOD
            (reading?.rssi?:0) >= -80 -> SignalStrength.FAIR
            else -> SignalStrength.WEAK
        }

    fun temperatureFormatted(unitSystem: UnitSystem): String {
        return try {
            val tempC = reading?.temperatureCelsius ?: 0f
            when (unitSystem) {
                UnitSystem.METRIC -> String.format("%.1f\u00B0C", tempC)
                UnitSystem.IMPERIAL -> {
                    val tempF = tempC * 9f / 5f + 32f
                    String.format("%.1f\u00B0F", tempF)
                }
            }
        } catch (e: Exception) {
            "--"
        }
    }

}
