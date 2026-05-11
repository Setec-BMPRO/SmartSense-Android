package com.smartsense.app.data.quality

import com.smartsense.app.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
class ReadingQualityCalculator @Inject constructor(
    userPreferences: UserPreferences
) {
    /** Long-lived scope tied to the singleton's process lifetime so we can collect the
     *  developer-tunable threshold flows from [UserPreferences] into local caches without
     *  blocking each [compute] call on a `first()`. SupervisorJob keeps the scope alive if
     *  one collector fails. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Cached cutoff (meters) for q=3 GOOD — updated by the flow collector in [init].
     *  `@Volatile` so the calculator thread reading from a non-synchronized path sees the
     *  freshest value the Settings UI just wrote. */
    @Volatile
    private var stddevGoodMeters: Double = UserPreferences.DEFAULT_STDDEV_GOOD_MM.toDouble() / 1000.0

    /** Cached cutoff (meters) for q=2 FAIR. Above this → q=1 POOR. */
    @Volatile
    private var stddevFairMeters: Double = UserPreferences.DEFAULT_STDDEV_FAIR_MM.toDouble() / 1000.0

    /** Cached cap (`n`) on the rolling window. Read by [evict] and the per-sensor window
     *  duration in [windowMsFor]. Defaults to the value the calculator originally
     *  shipped with so the math doesn't change until the user opts in. */
    @Volatile
    private var maxSamples: Int = UserPreferences.DEFAULT_MAX_SAMPLES

    init {
        // Hydrate the cached thresholds from DataStore and keep them in sync with whatever
        // the developer-mode Settings UI writes. UserPreferences stores values in mm; we
        // cache them in meters to match the calculator's existing units and avoid a div
        // on every compute() call.
        userPreferences.stddevGoodMm
            .onEach { stddevGoodMeters = it.toDouble() / 1000.0 }
            .launchIn(scope)
        userPreferences.stddevFairMm
            .onEach { stddevFairMeters = it.toDouble() / 1000.0 }
            .launchIn(scope)
        userPreferences.maxSamples
            .onEach { maxSamples = it.coerceAtLeast(MIN_SAMPLES) }
            .launchIn(scope)
    }

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
     * Wall-clock of the most recent sample we've recorded for each address — kept
     * independently of the buffer so we can still detect "gone silent" even after every
     * sample has aged out of the window. Without this, computeLocked() would see an empty
     * buffer for a freshly-paired sensor (correctly → GOOD) and an empty buffer for a
     * long-silent sensor (incorrectly → GOOD too).
     */
    private val lastSampleAtMillis = ConcurrentHashMap<String, Long>()

    /**
     * Most recent "data serial number" (Setec spec byte 15) per sensor. The sensor only
     * increments this on a genuinely new measurement; identical re-broadcasts share the
     * same value. Used by [addSample] to skip pushing duplicate samples into the rolling
     * buffer — without this, a sensor sitting on the same reading for a minute would fill
     * the buffer with 10 identical heights, drive stddev to 0, and report GOOD even when
     * the sensor wasn't actually doing anything. A null serial means the broadcast
     * protocol doesn't expose one (CC2540 / NRF52) → behave as before, every adv is a
     * new sample.
     */
    private val lastSerialPerAddress = ConcurrentHashMap<String, Int>()

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
     *
     * Pass [dataSerial] from `SensorReading.dataSerial` (Setec spec byte 15) when available.
     * The sensor only bumps this on a genuinely new measurement, so we use it to dedupe:
     * an adv with the same serial as the previous one is a re-broadcast of the previous
     * reading — we refresh [lastSampleAtMillis] (the sensor is still alive) but don't push
     * a duplicate sample into the rolling buffer. Pass `null` to disable dedup (CC2540 /
     * NRF52 don't have a serial; for those, every adv counts as a fresh sample).
     */
    fun addSample(
        address: String,
        heightMeters: Double,
        reportingIntervalSeconds: Int = 0,
        dataSerial: Int? = null
    ): Int {
        if (reportingIntervalSeconds > 0) {
            this.reportingIntervalSeconds[address] = reportingIntervalSeconds
        }
        val buffer = buffers.getOrPut(address) { ArrayDeque() }
        synchronized(buffer) {
            val now = System.currentTimeMillis()
            // Always update the last-seen wall-clock — even a re-broadcast means the sensor
            // is currently online. SensorFreshness.isStale depends on this, so dedup must
            // NOT make a healthy sensor look offline.
            lastSampleAtMillis[address] = now

            val previousSerial = lastSerialPerAddress[address]
            val isDuplicate = dataSerial != null && previousSerial == dataSerial
            if (isDuplicate) {
                Timber.tag(TAG).v(
                    "%s rebroadcast serial=%d height=%.4fm — refreshing timestamp",
                    address, dataSerial, heightMeters
                )
                // Refresh the most-recent sample's timestamp so the sliding-window
                // eviction doesn't drop it while the sensor is actively re-broadcasting.
                // Without this, a sensor whose serial is "stuck" (no new measurement
                // — common for an empty tank that the ultrasonic can't measure) would
                // have its sole sample age out of the window after `windowMsFor(...)`
                // and the buffer would stay at n=0 forever (dedup blocks future adds
                // with the same serial). Refreshing keeps the latest distinct
                // measurement visible in the debug card for as long as the sensor is
                // alive — staleness still falls back to STALE_QUALITY via
                // `lastSampleAtMillis` (set just above) when broadcasts actually stop.
                if (buffer.isNotEmpty()) {
                    val last = buffer.removeLast()
                    buffer.addLast(last.copy(timestampMillis = now))
                }
                evict(buffer, now, address)
                return computeLocked(address, buffer)
            }
            if (dataSerial != null) lastSerialPerAddress[address] = dataSerial
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
        lastSampleAtMillis.remove(address)
        lastSerialPerAddress.remove(address)
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
            val latestSerial = lastSerialPerAddress[address]
            if (buffer.isEmpty()) {
                // No live samples — but if we *have* seen this address recently it just
                // means we're inside the warm-up window or the sensor has gone silent.
                // Either way the debug card should reflect the quality computeLocked()
                // would return for those situations (DEFAULT_QUALITY or STALE_QUALITY).
                return QualitySnapshot.EMPTY.copy(
                    quality = computeLocked(address, buffer),
                    latestSerial = latestSerial
                )
            }
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
                quality = quality,
                latestSerial = latestSerial
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
        val quality: Int,
        /** Latest "data serial number" we recorded for this address. `null` if the protocol
         *  doesn't expose one (CC2540 / NRF52) or we haven't ingested any samples yet. */
        val latestSerial: Int? = null
    ) {
        companion object {
            val EMPTY = QualitySnapshot(
                samples = emptyList(),
                meanMeters = 0.0,
                stdDevMeters = 0.0,
                quality = DEFAULT_QUALITY,
                latestSerial = null
            )
        }
    }

    /**
     * Sliding-window length for [address], derived from the latest broadcast cadence:
     *   (reportingIntervalSeconds + [MARGIN_S]) × maxSamples
     * Falls back to [DEFAULT_REPORTING_INTERVAL_S] when we haven't seen one yet. Uses the
     * runtime-tunable [maxSamples] cache rather than the legacy constant so changing `n`
     * in the developer Settings UI immediately widens / narrows the eviction window too.
     */
    private fun windowMsFor(address: String): Long {
        val interval = reportingIntervalSeconds[address] ?: DEFAULT_REPORTING_INTERVAL_S
        return (interval + MARGIN_S).toLong() * maxSamples * 1_000L
    }

    /**
     * Two-stage eviction:
     * 1. Drop entries older than the per-sensor sliding window so a sensor that has
     *    stopped broadcasting eventually empties the buffer rather than scoring on
     *    stale data.
     * 2. Cap to the most recent [maxSamples] entries (the "rolling last n" rule —
     *    runtime-tunable from developer Settings; default 10).
     */
    private fun evict(buffer: ArrayDeque<Sample>, now: Long, address: String) {
        val cutoff = now - windowMsFor(address)
        while (buffer.isNotEmpty() && buffer.first().timestampMillis < cutoff) {
            buffer.removeFirst()
        }
        val cap = maxSamples
        while (buffer.size > cap) {
            buffer.removeFirst()
        }
    }

    /** Caller must hold the buffer monitor. */
    private fun computeLocked(address: String, buffer: ArrayDeque<Sample>): Int {
        // Quality fall-through, in order:
        // 1. Never seen this address → DEFAULT_QUALITY (no penalty for new pairings).
        // 2. Seen before but silent for too long → STALE_QUALITY (= POOR). Without this
        //    check, a sensor whose entire buffer has aged out would still return
        //    DEFAULT_QUALITY via the size<MIN_SAMPLES branch below — misleading.
        // 3. Warming up (a couple of samples in) → DEFAULT_QUALITY.
        // 4. Enough samples → map stddev to 3 / 2 / 1.
        val lastSeen = lastSampleAtMillis[address] ?: return DEFAULT_QUALITY
        val staleThresholdMs = SensorFreshness.staleThresholdMs(
            reportingIntervalSeconds[address] ?: 0
        )
        if (System.currentTimeMillis() - lastSeen > staleThresholdMs) {
            Timber.tag(TAG).v(
                "%s STALE (silent for %ds, threshold %ds) → q=%d",
                address, (System.currentTimeMillis() - lastSeen) / 1000,
                staleThresholdMs / 1000, STALE_QUALITY
            )
            return STALE_QUALITY
        }
        if (buffer.size < MIN_SAMPLES) return DEFAULT_QUALITY
        val heights = buffer.map { it.heightMeters }
        val mean = heights.average()
        val variance = heights.map { (it - mean).pow(2) }.average()
        val stddev = sqrt(variance)
        // Thresholds are read from the @Volatile caches (init-collected from
        // UserPreferences). Mutating them via the developer-mode Settings UI takes effect
        // on the very next compute call, no app restart required.
        val good = stddevGoodMeters
        val fair = stddevFairMeters
        val quality = when {
            stddev <= good -> 3
            stddev <= fair -> 2
            else -> 1
        }
        Timber.tag(TAG).v(
            "%s n=%d mean=%.4fm stddev=%.4fm good=%.4fm fair=%.4fm → q=%d",
            address, buffer.size, mean, stddev, good, fair, quality
        )
        return quality
    }

    companion object {
        private const val TAG = "Quality"

        /**
         * Legacy fixed ring-buffer cap. Spec called for "last 10 readings"; the live cap
         * now comes from [UserPreferences.maxSamples] via the volatile cache. Kept as the
         * canonical default value.
         */
        @Deprecated("Use UserPreferences.maxSamples — runtime-tunable.")
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
         * Quality assigned when [SensorFreshness] says the sensor has gone silent. POOR (1)
         * rather than UNKNOWN/0 so it flows through the existing 1-3 mapping and triggers
         * the existing low-quality warnings.
         */
        const val STALE_QUALITY = 1

        /**
         * Legacy fixed cutoffs (metres) — kept for documentation of where the runtime
         * defaults originated, but no longer read at compute time. The live thresholds
         * are sourced from [UserPreferences.stddevGoodMm] / [UserPreferences.stddevFairMm]
         * via the volatile caches above, with the same numeric values seeded as defaults.
         */
        @Deprecated("Use UserPreferences.stddevGoodMm / stddevFairMm — runtime-tunable.")
        const val STDDEV_GOOD_M = 0.005   // 5 mm

        @Deprecated("Use UserPreferences.stddevGoodMm / stddevFairMm — runtime-tunable.")
        const val STDDEV_FAIR_M = 0.015   // 15 mm

        const val DEFAULT_QUALITY = 3
    }
}
