package com.smartsense.app.domain.model

import androidx.annotation.StringRes
import com.smartsense.app.R

/**
 * Why a sensor is currently "offline" (failing [Sensor.isStale]).
 *
 * Derived by comparing the sensor's last-known broadcast time against the process-wide
 * "last callback for any device" timestamp tracked in `BleScanHealth`. If the global
 * timestamp is also stale we know the BLE scanner has been silenced (Android's
 * opportunistic demotion); if it's fresh, only this particular sensor has stopped
 * broadcasting.
 */
enum class OfflineCause(@StringRes val labelRes: Int) {
    /** Android demoted our scan to `SCAN_MODE_OPPORTUNISTIC` — no device's adverts get through. */
    SCANNER_STALLED(R.string.offline_cause_scanner_stalled),

    /** Other devices are still being picked up; this specific sensor isn't broadcasting. */
    SENSOR_QUIET(R.string.offline_cause_sensor_not_responding);
}
