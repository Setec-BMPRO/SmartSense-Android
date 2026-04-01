package com.smartsense.app.domain.model

data class Sensor1(
    val address: String,
    var name: String?,
    val advertisedName: String?=null,
    val sensorType: MopekaSensorType?,
    val syncPressed: Boolean = false,
    val reading: SensorReading?=null,
    val tankLevel: TankLevel?=null,
    val readQuality: ReadQuality? =null,
    val tankType: String?=null


    ){
    val batteryPercent: Int= (((reading?.batteryVoltage?:0f) - 2.2f)
            / 0.65f * 100f).coerceIn(0f, 100f).toInt()
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

    val groudName: String= when {
        sensorType?.isLpg == true -> {
            "Bottom Mount - LPG"
        }
        sensorType == MopekaSensorType.BOTTOM_UP_WATER -> {
            "Bottom Up - Water"
        }
        else -> {
            "Others"
        }
    }
}
