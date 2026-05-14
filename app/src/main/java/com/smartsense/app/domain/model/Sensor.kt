package com.smartsense.app.domain.model

import com.smartsense.app.data.local.entity.SensorEntity
import com.smartsense.app.data.local.entity.SyncStatus

data class Sensor(
    val address: String,
    var name: String?,
    val advertisedName: String?=null,
    val sensorType: MopekaSensorType?=null,
    val syncPressed: Boolean = false,
    val reading: SensorReading?=null,
    val tankLevel: TankLevel?=null,
    val readQuality: ReadQuality? =null,
    val tankType: String?=null,
    val orientation: TankOrientation? = null,
    val syncStatus: SyncStatus?=null

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

    /**
     * `true` when we haven't seen a broadcast for this sensor in a while, computed off
     * the sensor's own reported cadence — see [com.smartsense.app.data.quality.SensorFreshness].
     * Drives the "Offline" badge on the list + detail screens. Getter only; data class
     * equality is unaffected.
     *
     * **Cold-start grace.** Persisted [reading.timestampMillis] is whatever we wrote on the
     * last app run, often hours/days old. Naively running [SensorFreshness.isStale] against
     * that on launch would flash the OFFLINE badge for every paired sensor until the first
     * fresh adv arrived — confusing for the user. The grace window suppresses staleness
     * until either:
     *  1. We've heard from this specific address in the current process (best case — clears
     *     immediately when the first adv lands), or
     *  2. The BLE scanner has been receiving callbacks for *at least one freshness window*
     *     without ever hearing from this sensor (fallback — covers a paired sensor that
     *     genuinely won't broadcast). The window-since-first-callback is the right proxy
     *     because it measures "scanner time" not "wall-clock time", so a delayed permission
     *     prompt or a Bluetooth-off start doesn't artificially extend the grace.
     */
    val isStale: Boolean
        get() {
            val seenThisSession = com.smartsense.app.data.ble.BleScanHealth
                .lastSeenForDevice(address) > 0L
            if (seenThisSession) {
                return com.smartsense.app.data.quality.SensorFreshness.isStale(reading)
            }
            // Never heard from this address since process start. Don't trust the persisted
            // timestamp on its own. Use the **first** any-callback timestamp (not the
            // *last* — which keeps refreshing while a neighbouring sensor broadcasts, so
            // the diff stays small forever and the grace never elapses). The right
            // question is "has the scanner been alive long enough for this specific
            // sensor to have broadcasted by now?", which is measured from when the
            // scanner first started hearing anything.
            val firstAt = com.smartsense.app.data.ble.BleScanHealth.firstAnyCallbackAt()
            if (firstAt == 0L) return false // Scanner hasn't received anything yet — still warming up.
            val scannerUptimeMs = System.currentTimeMillis() - firstAt
            val staleThresholdMs = com.smartsense.app.data.quality.SensorFreshness.staleThresholdMs(
                reading?.reportingIntervalSeconds ?: 0
            )
            return scannerUptimeMs > staleThresholdMs
        }

    /**
     * Why the sensor is offline, when [isStale] is `true`. `null` when the sensor is healthy.
     *
     * Three-step classification using [com.smartsense.app.data.ble.BleScanHealth]:
     *
     * 1. **Recent watchdog restart still silent → SENSOR_QUIET.** If we already kicked
     *    the scanner inside the last [POST_RESTART_GRACE_MS] and we're *still* not hearing
     *    this sensor, the OS-side demotion isn't the issue — the sensor itself stopped
     *    talking. Without this rule, an offline sensor would keep getting classified as
     *    SCANNER_STALLED and we'd thrash the scan in a loop.
     * 2. **Any-callback timestamp is fresh → SENSOR_QUIET.** Other filtered devices are
     *    triggering callbacks, so the scan pipeline is healthy; only *this* sensor is
     *    quiet.
     * 3. **Any-callback timestamp is also stale → SCANNER_STALLED.** Nothing else is
     *    making it through either. Either the OS demoted us or there are no other BLE
     *    devices to compare against — the watchdog will restart the scan within
     *    `BleManager.WATCHDOG_INTERVAL_MS` and the classification self-heals after the
     *    grace window above.
     *
     * Threshold for "any device is also silent" is intentionally short (10 s) — a
     * populated BLE area produces multiple unrelated adverts per second, so 10 s of
     * total silence is already abnormal.
     */
    val offlineCause: OfflineCause?
        get() {
            if (!isStale) return null
            val now = System.currentTimeMillis()
            val lastRestartAt = com.smartsense.app.data.ble.BleScanHealth.lastWatchdogRestartAt()
            if (lastRestartAt > 0 && (now - lastRestartAt) < POST_RESTART_GRACE_MS) {
                return OfflineCause.SENSOR_QUIET
            }
            val lastAnyAt = com.smartsense.app.data.ble.BleScanHealth.lastAnyCallbackAt()
            val anySilentMs = now - lastAnyAt
            return if (anySilentMs > GLOBAL_SCAN_SILENT_THRESHOLD_MS)
                OfflineCause.SCANNER_STALLED
            else OfflineCause.SENSOR_QUIET
        }

    companion object {
        /** Past this many ms with no callback from any device we attribute staleness to a scanner demotion. */
        private const val GLOBAL_SCAN_SILENT_THRESHOLD_MS = 10_000L

        /** After a watchdog-driven scan restart, give the pipeline this long before
         *  classifying anything as SCANNER_STALLED again — otherwise a sensor that's
         *  genuinely off would loop the watchdog forever. */
        private const val POST_RESTART_GRACE_MS = 30_000L
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
