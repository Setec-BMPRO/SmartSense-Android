package com.smartsense.app.domain.model

import com.smartsense.app.data.local.entity.SensorEntity

/**
 * Sensor measurement quality, declared in best-to-worst order to match the firmware's
 * Setec protocol (byte 30 high nibble): `0x1=POOR, 0x2=GOOD, 0x3=EXCELLENT`. We don't
 * have a FAIR tier — the protocol only defines three steps so neither do we. `null` at
 * the UI layer signals "unknown" (no rating yet) and renders as "—".
 */
enum class ReadQuality {
    EXCELLENT,
    GOOD,
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


data class UiState(
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)