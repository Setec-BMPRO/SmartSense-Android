package com.smartsense.app.domain.model

enum class SortPreference(val displayName: String) {
    NAME("Name"),
    LEVEL("Level");
}


enum class AppTheme(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System");
}

/**
 * UI refresh cadence for the scan list and detail view.
 *
 * [AUTO] (default) means "match the sensor's own broadcast cadence":
 *  - Detail view → uses that sensor's [SensorReading.reportingIntervalSeconds]
 *    (G300 reports this in the BLE advert; falls back to [AUTO_FALLBACK_SECONDS]).
 *  - List view → picks the smallest reporting interval among registered sensors,
 *    again falling back to [AUTO_FALLBACK_SECONDS].
 *
 * The other entries are explicit overrides for users who want a fixed cadence.
 */
enum class ScanIntervals(val value: Int, val displayName: String) {
    AUTO(0, "Auto"),
    ONE(1, "Immediately"),
    FIVE(5, "5 seconds"),
    TEN(10, "10 seconds"),
    FIFTEEN(15, "15 seconds"),
    TWENTY(20, "20 seconds");

    val isAuto: Boolean get() = this == AUTO

    companion object {
        /** Default ticker when [AUTO] is selected but no sensor reports an interval yet. */
        const val AUTO_FALLBACK_SECONDS = 3
        fun default(): ScanIntervals = AUTO
    }
}

enum class UnitSystem(val displayName: String) {
    METRIC("Metric"),
    IMPERIAL("Imperial");
}
