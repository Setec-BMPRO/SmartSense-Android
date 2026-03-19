package com.smartsense.app.domain.model

data class Sensor(
    val address: String,
    val name: String,
    val tankPreset: TankPreset,
    val level: TankLevel,
    val batteryPercent: Int,
    val rssi: Int,
    val temperatureCelsius: Float,
    val readQuality: ReadQuality,
    val lastUpdated: Long,
    val isPaired: Boolean
) {
    val signalStrength: SignalStrength
        get() = when {
            rssi >= -50 -> SignalStrength.EXCELLENT
            rssi >= -65 -> SignalStrength.GOOD
            rssi >= -80 -> SignalStrength.FAIR
            else -> SignalStrength.WEAK
        }

    fun temperatureFormatted(unitSystem: UnitSystem): String {
        return when (unitSystem) {
            UnitSystem.METRIC -> String.format("%.1f\u00B0C", temperatureCelsius)
            UnitSystem.IMPERIAL -> String.format("%.1f\u00B0F", temperatureCelsius * 9f / 5f + 32f)
        }
    }

    fun levelHeightFormatted(unitSystem: UnitSystem): String {
        return when (unitSystem) {
            UnitSystem.METRIC -> String.format("%.0f mm", level.heightMm)
            UnitSystem.IMPERIAL -> String.format("%.1f in", level.heightMm / 25.4f)
        }
    }
}

enum class ReadQuality {
    GOOD,
    FAIR,
    POOR
}

enum class SignalStrength {
    EXCELLENT,
    GOOD,
    FAIR,
    WEAK
}
