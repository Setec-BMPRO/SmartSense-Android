package com.smartsense.app.data.quality

import com.smartsense.app.domain.model.SensorReading

/**
 * Single source of truth for "has this sensor gone quiet?" — the freshness check used by:
 *
 * - **[ReadingQualityCalculator]** to flip computed quality to POOR when no recent samples
 *   have arrived (instead of misleadingly defaulting to GOOD because the buffer happens to
 *   be empty).
 * - **`Sensor.isStale`** (in the domain model) to drive the "Offline" badge on the list +
 *   detail screens.
 *
 * Threshold is derived from the sensor's own reported broadcast cadence:
 *
 *     staleThresholdMs = (reportingIntervalSeconds [or DEFAULT_REPORTING_INTERVAL_S])
 *                      × STALE_MULTIPLIER × 1000
 *
 * With STALE_MULTIPLIER = 5: a G300 at 3 s flips stale after 15 s of silence; a sensor
 * configured at 60 s flips after 5 min. 5 intervals is roomy enough to absorb the
 * occasional dropped packet (BLE drops 1-2 advs per minute are normal) without flapping,
 * but short enough that a genuine outage shows up well before the user notices stale data.
 */
object SensorFreshness {

    /** How many reporting intervals of silence we tolerate before declaring "stale". */
    const val STALE_MULTIPLIER = 5

    /** Fallback for sensors that don't broadcast a reporting interval (CC2540 / NRF52). */
    const val DEFAULT_REPORTING_INTERVAL_S = 3

    fun staleThresholdMs(reportingIntervalSeconds: Int): Long {
        val interval = reportingIntervalSeconds.takeIf { it > 0 } ?: DEFAULT_REPORTING_INTERVAL_S
        return interval.toLong() * STALE_MULTIPLIER * 1000L
    }

    /**
     * `true` when the last accepted broadcast for this reading is older than the per-sensor
     * threshold. Returns `false` for null readings — sensors that have never spoken can't be
     * "stale", they're just unreported.
     */
    fun isStale(reading: SensorReading?, now: Long = System.currentTimeMillis()): Boolean {
        val ts = reading?.timestampMillis ?: return false
        return (now - ts) > staleThresholdMs(reading.reportingIntervalSeconds)
    }
}
