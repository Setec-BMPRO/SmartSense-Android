package com.smartsense.app.data.ble

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide diagnostics for the BLE scan pipeline. Two distinct jobs:
 *
 * 1. **Classification data** — read by `Sensor.offlineCause` to tell apart a phone-side
 *    scan stall (the OS demoted us to `SCAN_MODE_OPPORTUNISTIC`; *all* device's
 *    callbacks dry up at once) from a sensor-side stop (only one specific sensor stops
 *    broadcasting while others keep streaming).
 * 2. **Auto-recovery hook** — exposes [requestScanRestart] so UI code that detects the
 *    "scanner stalled" condition can ask the BleManager watchdog to kick the scanner
 *    immediately instead of waiting up to one watchdog interval. The flag is consumed
 *    by [consumeScanRestartRequest] which clears it atomically.
 *
 * All fields are atomic / concurrent because writers come from the BLE scan callback
 * thread and the watchdog coroutine while readers come from the UI thread (per-second
 * heartbeat).
 */
object BleScanHealth {
    private const val TAG = "BleHealth"

    private val lastAnyCallbackMs = AtomicLong(0)

    /** Wall-clock of the **first** scan callback this process saw — set once, never
     *  refreshed. Read by `Sensor.isStale`'s cold-start grace path to ask "has the
     *  scanner been alive long enough for this sensor to have broadcasted by now?"
     *  Using `lastAnyCallbackMs` for that check is wrong: while another sensor is
     *  broadcasting healthily, `lastAnyCallbackMs` keeps refreshing and `(now - last)`
     *  stays small forever — the grace would never elapse and a genuinely-silent
     *  sensor would never get an OFFLINE pill while a healthy neighbour was streaming. */
    private val firstAnyCallbackMs = AtomicLong(0)

    /** Per-device callback counters since process start. */
    private val callbackCounts = ConcurrentHashMap<String, AtomicLong>()

    /** Per-device wall-clock of the latest callback we saw for that address. */
    private val lastSeenPerDeviceMs = ConcurrentHashMap<String, AtomicLong>()

    /** # of times the watchdog has stopped + re-registered the scan since process start. */
    private val watchdogRestartCount = AtomicLong(0)
    private val lastWatchdogRestartMs = AtomicLong(0)

    /** UI-driven request for an immediate scan restart. Set by [requestScanRestart],
     *  cleared by [consumeScanRestartRequest]. */
    private val restartRequested = AtomicBoolean(false)

    /**
     * Update on every BLE scan callback (per-device variant). Use this instead of the
     * legacy [recordCallback] whenever the caller knows which device advertised — it
     * keeps per-sensor stats up to date as well as the global "any callback" pulse.
     */
    fun recordDeviceCallback(address: String) {
        val now = System.currentTimeMillis()
        lastAnyCallbackMs.set(now)
        firstAnyCallbackMs.compareAndSet(0L, now)
        callbackCounts.getOrPut(address) { AtomicLong(0) }.incrementAndGet()
        lastSeenPerDeviceMs.getOrPut(address) { AtomicLong(0) }.set(now)
    }

    /** Legacy "I got a callback but don't have an address handy" entrypoint. Prefer
     *  [recordDeviceCallback]. */
    fun recordCallback() {
        val now = System.currentTimeMillis()
        lastAnyCallbackMs.set(now)
        firstAnyCallbackMs.compareAndSet(0L, now)
    }

    /** Called by BleManager whenever its watchdog stops + restarts the scanner so
     *  `Sensor.offlineCause` can refine its SCANNER_STALLED vs SENSOR_QUIET answer. */
    fun recordWatchdogRestart(reason: String) {
        val n = watchdogRestartCount.incrementAndGet()
        lastWatchdogRestartMs.set(System.currentTimeMillis())
        val silentMs = System.currentTimeMillis() - lastAnyCallbackMs.get()
        Timber.tag(TAG).w("watchdog restart #$n reason=$reason silentFor=${silentMs / 1000}s")
    }

    /** Tell the watchdog "next time you check, restart the scan even if your time-based
     *  threshold hasn't fired yet." Called from UI code that observes SCANNER_STALLED. */
    fun requestScanRestart() {
        if (restartRequested.compareAndSet(false, true)) {
            Timber.tag(TAG).i("scan restart requested by UI")
        }
    }

    /** Watchdog calls this on every tick; returns `true` once and clears the flag. */
    fun consumeScanRestartRequest(): Boolean = restartRequested.getAndSet(false)

    /** 0 until the first callback arrives. */
    fun lastAnyCallbackAt(): Long = lastAnyCallbackMs.get()

    /** Wall-clock of the very first scan callback this process saw — does not refresh.
     *  0 if the scanner hasn't received anything yet. */
    fun firstAnyCallbackAt(): Long = firstAnyCallbackMs.get()

    fun lastWatchdogRestartAt(): Long = lastWatchdogRestartMs.get()

    fun watchdogRestarts(): Long = watchdogRestartCount.get()

    fun lastSeenForDevice(address: String): Long = lastSeenPerDeviceMs[address]?.get() ?: 0L

    fun callbackCountForDevice(address: String): Long = callbackCounts[address]?.get() ?: 0L

    /** Snapshot for diagnostic logging. Returns a defensive copy of the maps so callers
     *  can iterate without worrying about concurrent updates from the scan thread. */
    fun snapshot(): Snapshot = Snapshot(
        lastAnyCallbackMs = lastAnyCallbackMs.get(),
        lastWatchdogRestartMs = lastWatchdogRestartMs.get(),
        watchdogRestarts = watchdogRestartCount.get(),
        deviceCallbackCounts = callbackCounts.mapValues { it.value.get() },
        deviceLastSeen = lastSeenPerDeviceMs.mapValues { it.value.get() }
    )

    data class Snapshot(
        val lastAnyCallbackMs: Long,
        val lastWatchdogRestartMs: Long,
        val watchdogRestarts: Long,
        val deviceCallbackCounts: Map<String, Long>,
        val deviceLastSeen: Map<String, Long>
    )
}
