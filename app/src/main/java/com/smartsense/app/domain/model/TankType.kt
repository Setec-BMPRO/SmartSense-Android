package com.smartsense.app.domain.model

enum class TankOrientation {
    VERTICAL,
    HORIZONTAL;

    companion object {
        fun default():TankOrientation=VERTICAL
    }
}

enum class TankLevelUnit(val displayName: String,val shortName: String) {
    PERCENT("Percent","%"),
    CENTIMETERS("Centimeters","Cm"),
    INCHES("Inches","In");

    companion object{
        fun default():TankLevelUnit=PERCENT
    }

}

enum class TriggerAlarmUnit(val displayName: String) {
    ABOVE("Above"),
    BELOW("Below");
    companion object{
        fun default():TriggerAlarmUnit=ABOVE
    }
}

enum class NotificationFrequency(
    val displayName: String,
    val timeMillis: Long
) {
    EVERY_1_MINUTE("Every 1 Minute", 60_000L),
    EVERY_HOUR("Every Hour", 3_600_000L),
    EVERY_6_HOURS("Every 6 Hours", 6 * 3_600_000L),
    EVERY_12_HOURS("Every 12 Hours", 12 * 3_600_000L),
    EVERY_24_HOURS("Every 24 Hours", 24 * 3_600_000L);

    companion object {
        fun default(): NotificationFrequency = EVERY_12_HOURS

        /**
         * Safely converts a string (from DB/Prefs) to the Enum.
         * Falls back to default() if the string is invalid or null.
         */
        fun fromString(name: String?): NotificationFrequency {
            return try {
                valueOf(name ?: "")
            } catch (e: Exception) {
                default()
            }
        }
    }
}

enum class TankRegion(val displayName: String) {

    AUSTRALIA("Australia"),
    CANADA("Canada"),
    NEW_ZEALAND("New Zealand"),
    UNITED_STATE("United States"),
    OTHER_NORTH_AMERICA("Other-North America");

    val regionCode: String
        get() = when (this) {
            AUSTRALIA -> "au"
            CANADA -> ""
            NEW_ZEALAND -> "au"
            UNITED_STATE -> ""
            OTHER_NORTH_AMERICA -> "" // Common shorthand for North America
        }
    companion object{
        fun default(): TankRegion=AUSTRALIA
    }
}

enum class TankType(
    val displayName: String,
    val heightMeters: Double,
    val orientation: TankOrientation,
    val region: String = "",
    /** Height-to-width ratio for the tank silhouette view. */
    val silhouetteAspect: Float = 1.15f
) {

    // North America vertical tanks
    LB_20("20 lb, Vertical", 0.254, TankOrientation.VERTICAL, silhouetteAspect = 0.95f),
    LB_30("30 lb, Vertical", 0.381, TankOrientation.VERTICAL, silhouetteAspect = 1.15f),
    LB_40("40 lb, Vertical", 0.508, TankOrientation.VERTICAL, silhouetteAspect = 1.40f),

//    LB_100("100 lb", 0.8128, TankOrientation.VERTICAL),
//    GAL_120_V("120 gal vertical", 1.2192 * 0.8, TankOrientation.VERTICAL),
//
//    // North America horizontal tanks
//    GAL_120_H("120 gal horizontal", 0.6096, TankOrientation.HORIZONTAL),
//    GAL_150_H("150 gal horizontal", 0.6096, TankOrientation.HORIZONTAL),
//    GAL_250_H("250 gal horizontal", 0.762, TankOrientation.HORIZONTAL),
//    GAL_500_H("500 gal horizontal", 0.9398, TankOrientation.HORIZONTAL),
//    GAL_1000_H("1000 gal horizontal", 1.0414, TankOrientation.HORIZONTAL),

    // Australia/NZ tanks
    KG_3_7("4 kg", 0.235, TankOrientation.VERTICAL, "au", silhouetteAspect = 0.90f),
    KG_8_5("9 kg", 0.342, TankOrientation.VERTICAL, "au", silhouetteAspect = 1.15f),

    // Custom
    ARBITRARY("Custom", 0.0, TankOrientation.VERTICAL, silhouetteAspect = 1.15f);

    companion object {
        fun forRegion(region: String): List<TankType> {
            return entries.filter { it.region == region || it == ARBITRARY }
        }

        fun forTankRegion(tankRegion: TankRegion): List<TankType> {
            return forRegion(tankRegion.regionCode)
        }

        fun default():TankType=KG_3_7
    }
}

enum class QualityThreshold(val displayName: String) {
    DISABLE("Disable"),
    ONE("1"),
    TWO("2");

    companion object{
        fun default(): QualityThreshold= DISABLE
    }
}

const val DEFAULT_ALARM_THRESHOLD_PERCENT = 20
const val DEFAULT_NOTIFICATION_ENABLED = true
