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
    val tankLevelPercentage: Int=0
){
    val signalStrength: SignalStrength
        get() = when {
            reading?.rssi!! >= -50 -> SignalStrength.EXCELLENT
            reading.rssi >= -65 -> SignalStrength.GOOD
            reading.rssi >= -80 -> SignalStrength.FAIR
            else -> SignalStrength.WEAK
        }

    fun temperatureFormatted(unitSystem: UnitSystem): String {
        return when (unitSystem) {
            UnitSystem.METRIC -> String.format("%.1f\u00B0C", reading?.temperatureCelsius?:0)
            UnitSystem.IMPERIAL -> String.format("%.1f\u00B0F",
                reading?.temperatureCelsius ?: (0 * 9f / 5f + 32f)
            )
        }
    }
}
