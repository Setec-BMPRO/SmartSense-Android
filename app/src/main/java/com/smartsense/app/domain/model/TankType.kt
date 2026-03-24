package com.smartsense.app.domain.model

enum class TankOrientation {
    VERTICAL,
    HORIZONTAL
}

enum class TankLevelUnit(val displayName: String) {
    PERCENT("Percent"),
    CENTIMETERS("Centimeters"),
    INCHES("Inches")
}

enum class NotificationFrequency(val displayName: String) {
    EVERY_HOUR("Every Hour"),
    EVERY_6_HOURS("Every 6 Hours"),
    EVERY_12_HOURS("Every 12 Hours"),
    EVERY_24_HOURS("Every 24 Hours")
}

enum class TankRegion(val displayName: String) {
    NORTH_AMERICA("North America"),
    AUSTRALIA("Australia");

    val regionCode: String
        get() = when (this) {
            NORTH_AMERICA -> "en"
            AUSTRALIA -> "au"
        }
}

enum class TankType(
    val displayName: String,
    val heightMeters: Double,
    val orientation: TankOrientation,
    val region: String = "en"
) {
    // North America vertical tanks
    LB_20("20 lb", 0.254, TankOrientation.VERTICAL),
    LB_30("30 lb", 0.381, TankOrientation.VERTICAL),
    LB_40("40 lb", 0.508, TankOrientation.VERTICAL),
    LB_100("100 lb", 0.8128, TankOrientation.VERTICAL),
    GAL_120_V("120 gal vertical", 1.2192 * 0.8, TankOrientation.VERTICAL),

    // North America horizontal tanks
    GAL_120_H("120 gal horizontal", 0.6096, TankOrientation.HORIZONTAL),
    GAL_150_H("150 gal horizontal", 0.6096, TankOrientation.HORIZONTAL),
    GAL_250_H("250 gal horizontal", 0.762, TankOrientation.HORIZONTAL),
    GAL_500_H("500 gal horizontal", 0.9398, TankOrientation.HORIZONTAL),
    GAL_1000_H("1000 gal horizontal", 1.0414, TankOrientation.HORIZONTAL),

    // Australia/NZ tanks
    KG_3_7("3.7 kg", 0.235, TankOrientation.VERTICAL, "au"),
    KG_8_5("8.5 kg", 0.342, TankOrientation.VERTICAL, "au"),

    // Custom
    ARBITRARY("Custom", 0.0, TankOrientation.VERTICAL);

    companion object {
        fun forRegion(region: String): List<TankType> {
            return entries.filter { it.region == region || it == ARBITRARY }
        }

        fun forTankRegion(tankRegion: TankRegion): List<TankType> {
            return forRegion(tankRegion.regionCode)
        }
    }
}
