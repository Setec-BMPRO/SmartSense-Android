package com.smartsense.app.domain.model

import com.smartsense.app.data.local.entity.SensorEntity

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

enum class SensorLocation {
    LOCAL_ONLY,    // Only in Room
    CLOUD_ONLY,    // Only in Firestore
    BOTH           // Synced
}

data class SensorUIModel(
    val sensor: Sensor,
    val location: SensorLocation
)