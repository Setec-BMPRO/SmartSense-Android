package com.smartsense.app.data.quality

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Derives a 0-3 "quality" star rating for an ultrasonic sensor from the **deviation of its
 * recent height readings** rather than the single quality byte the device sometimes broadcasts.
 *
 * Rationale (from product spec):
 *
 *   "The quality indication in the app is the quality of the ultrasonic sensor measurements.
 *    If you store the last 10 readings over a fixed amount of time you would expect little
 *    deviation for a good quality ultrasonic signal. If you see the value deviating from the
 *    mean, the quality is degraded — it may indicate the sensor isn't sealing well to the
 *    bottle, or the bottle is getting close to empty and the readings are becoming less
 *    accurate."
 *
 * Implementation
 *
 * - Per-address ring buffer of the last [MAX_SAMPLES] readings, **also** capped at a
 *   sliding window so a sensor that has gone silent stops scoring on stale data. The
 *   window length is **derived per-sensor** from the BLE reporting-interval byte the
 *   sensor itself broadcasts:
 *
 *     (reportingIntervalSeconds + MARGIN_S) × MAX_SAMPLES
 *
 *   For a 3 s G300 → 40 s window; for a 60 s configured sensor → 610 s window. The most
 *   recent interval reported by the sensor is cached per address. If we've never seen one
 *   (e.g. CC2540/NRF52, or before the first broadcast) we fall back to
 *   [DEFAULT_REPORTING_INTERVAL_S]. In steady state the time-based pass is a no-op (10 ×
 *   3 s ≈ 30 s, well under 40 s); it kicks in only when broadcasts slow or stop.
 * - Quality is mapped from the sample standard deviation of `rawHeightMeters`:
 *     stddev ≤ [STDDEV_GOOD_M]  → 3 (GOOD)
 *     stddev ≤ [STDDEV_FAIR_M]  → 2 (FAIR)
 *     stddev >  [STDDEV_FAIR_M] → 1 (POOR)
 * - Until at least [MIN_SAMPLES] data points are buffered we fall back to GOOD so a freshly
 *   paired sensor doesn't render as "POOR" for the first few seconds.
 *
 * The thresholds are **placeholder values**. The spec calls for sampling real gas bottles to
 * decide the right cutoffs — every `compute(...)` logs the observed stddev at Timber's verbose
 * level so the values can be eyeballed during field testing and `STDDEV_GOOD_M` /
 * `STDDEV_FAIR_M` tuned without redeploying the data model.
 */
@Singleton
class ReadingQualityCalculator @Inject constructor() {

    private data class Sample(val timestampMillis: Long, val heightMeters: Double)

    /** Per-address ring buffer. ArrayDeque + manual eviction is fine for ≤ 10 entries. */
    private val buffers = ConcurrentHashMap<String, ArrayDeque<Sample>>()

    /**
     * Latest reporting interval (in seconds) we've seen on a broadcast from each address.
     * Drives the sliding-window eviction in [evict]. 0 / absent → fall back to
     * [DEFAULT_REPORTING_INTERVAL_S].
     */
    private val reportingIntervalSeconds = ConcurrentHashMap<String, Int>()

    /**
     * Record a new height reading for [address] and return the freshly-computed quality.
     *
     * Returns the same value `compute(address)` would — exposed as a single call so callers
     * doing "ingest + read" don't have to lock twice.
     */
    /**
     * Record a new height reading for [address] and return the freshly-computed quality.
     *
     * Pass [reportingIntervalSeconds] from the BLE broadcast (G300 carries this in byte 30).
     * The most recent value is cached and used to size the sliding-window eviction for this
     * sensor. Pass 0 if unknown — the previously cached value (or
     * [DEFAULT_REPORTING_INTERVAL_S] when nothing has ever been reported) is then used.
     */
    fun addSample(address: String, heightMeters: Double, reportingIntervalSeconds: Int = 0): Int {
        if (reportingIntervalSeconds > 0) {
            this.reportingIntervalSeconds[address] = reportingIntervalSeconds
        }
        val buffer = buffers.getOrPut(address) { ArrayDeque() }
        synchronized(buffer) {
            val now = System.currentTimeMillis()
            buffer.addLast(Sample(now, heightMeters))
            evict(buffer, now, address)
            return computeLocked(address, buffer)
        }
    }

    /** Read the current quality for [address] without recording a new sample. */
    fun compute(address: String): Int {
        val buffer = buffers[address] ?: return DEFAULT_QUALITY
        synchronized(buffer) {
            evict(buffer, System.currentTimeMillis(), address)
            return computeLocked(address, buffer)
        }
    }

    /** Drop the history for [address] — call when the sensor is unregistered. */
    fun clear(address: String) {
        buffers.remove(address)
        reportingIntervalSeconds.remove(address)
    }

    /**
     * Snapshot of the buffer for [address] — used by the detail view's Debug section so
     * the user can see the exact samples that drove the current quality rating, which is
     * what the spec asks us to calibrate against. Cheap: copies at most [MAX_SAMPLES]
     * entries.
     */
    fun snapshot(address: String): QualitySnapshot {
        val buffer = buffers[address] ?: return QualitySnapshot.EMPTY
        synchronized(buffer) {
            evict(buffer, System.currentTimeMillis(), address)
            if (buffer.isEmpty()) return QualitySnapshot.EMPTY
            val heights = buffer.map { it.heightMeters }
            val mean = heights.average()
            val variance = heights.map { (it - mean).pow(2) }.average()
            val stddev = sqrt(variance)
            val quality = computeLocked(address, buffer)
            // Newest-first so the most recent samples are at the top of the table.
            val entries = buffer.toList().reversed().map { s ->
                SampleEntry(
                    timestampMillis = s.timestampMillis,
                    heightMeters = s.heightMeters,
                    deviationFromMeanMeters = s.heightMeters - mean
                )
            }
            return QualitySnapshot(
                samples = entries,
                meanMeters = mean,
                stdDevMeters = stddev,
                quality = quality
            )
        }
    }

    /** Single sample in the rolling window, plus its deviation from the window's mean. */
    data class SampleEntry(
        val timestampMillis: Long,
        val heightMeters: Double,
        val deviationFromMeanMeters: Double
    )

    /** Snapshot of the per-address quality buffer for diagnostic display. */
    data class QualitySnapshot(
        val samples: List<SampleEntry>,
        val meanMeters: Double,
        val stdDevMeters: Double,
        val quality: Int
    ) {
        companion object {
            val EMPTY = QualitySnapshot(
                samples = emptyList(),
                meanMeters = 0.0,
                stdDevMeters = 0.0,
                quality = DEFAULT_QUALITY
            )
        }
    }

    /**
     * Sliding-window length for [address], derived from the latest broadcast cadence:
     *   (reportingIntervalSeconds + [MARGIN_S]) × [MAX_SAMPLES]
     * Falls back to [DEFAULT_REPORTING_INTERVAL_S] when we haven't seen one yet.
     */
    private fun windowMsFor(address: String): Long {
        val interval = reportingIntervalSeconds[address] ?: DEFAULT_REPORTING_INTERVAL_S
        return (interval + MARGIN_S).toLong() * MAX_SAMPLES * 1_000L
    }

    /**
     * Two-stage eviction:
     * 1. Drop entries older than the per-sensor sliding window so a sensor that has
     *    stopped broadcasting eventually empties the buffer rather than scoring on
     *    stale data.
     * 2. Cap to the most recent [MAX_SAMPLES] entries (the "rolling last 10" rule).
     */
    private fun evict(buffer: ArrayDeque<Sample>, now: Long, address: String) {
        val cutoff = now - windowMsFor(address)
        while (buffer.isNotEmpty() && buffer.first().timestampMillis < cutoff) {
            buffer.removeFirst()
        }
        while (buffer.size > MAX_SAMPLES) {
            buffer.removeFirst()
        }
    }

    /** Caller must hold the buffer monitor. */
    private fun computeLocked(address: String, buffer: ArrayDeque<Sample>): Int {
        if (buffer.size < MIN_SAMPLES) return DEFAULT_QUALITY
        val heights = buffer.map { it.heightMeters }
        val mean = heights.average()
        val variance = heights.map { (it - mean).pow(2) }.average()
        val stddev = sqrt(variance)
        val quality = when {
            stddev <= STDDEV_GOOD_M -> 3
            stddev <= STDDEV_FAIR_M -> 2
            else -> 1
        }
        Timber.tag(TAG).v(
            "%s n=%d mean=%.4fm stddev=%.4fm → q=%d",
            address, buffer.size, mean, stddev, quality
        )
        return quality
    }

    companion object {
        private const val TAG = "Quality"

        /** Ring-buffer cap. Spec: "store the last 10 readings". */
        const val MAX_SAMPLES = 10

        /**
         * Fallback broadcast cadence (seconds) for sensors that don't expose one — used
         * to size the sliding-window eviction. Matches the G300's default 3 s.
         */
        const val DEFAULT_REPORTING_INTERVAL_S = 3

        /** Allowance, in seconds, for the broadcast slot to slip slightly without dropping. */
        const val MARGIN_S = 1

        /** Minimum samples before we trust the computed quality. Until then, default GOOD. */
        const val MIN_SAMPLES = 3

        /**
         * Placeholder thresholds — to be replaced with values derived from real bottle tests.
         * Per the spec: "we will need to sample typical readings on a gas bottle and determine
         * ourselves how the amount of deviation and deviation frequency correlates to quality."
         *
         * Initial guesses, in metres (raw height is reported in m by the parsers):
         * - 5 mm ≈ tightly stable signal on a sealed bottle
         * - 15 mm ≈ moderate jitter (worth flagging)
         */
        const val STDDEV_GOOD_M = 0.005   // 5 mm
        const val STDDEV_FAIR_M = 0.015   // 15 mm

        const val DEFAULT_QUALITY = 3
    }
}
