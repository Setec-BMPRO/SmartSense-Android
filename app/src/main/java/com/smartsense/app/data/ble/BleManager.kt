package com.smartsense.app.data.ble

// Import the Nordic versions instead of the native ones

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BleManager"

        // ------- BLE scan watchdog ----------------------------------------------------
        // Android (since Marshmallow) silently demotes long-running scan clients to
        // SCAN_MODE_OPPORTUNISTIC after a per-app duration threshold. Symptom: every
        // ScanCallback method stops being invoked, with NO onScanFailed and a single
        // "BtGatt.ScanManager: Moving scan client to opportunistic" line in system
        // logcat. The only escape is to stop+startScan as a fresh client. The watchdog
        // detects the stall and does exactly that.

        /** How often the watchdog checks whether callbacks have stalled. Tighter than
         *  the original 15 s so a UI-requested restart (via
         *  [BleScanHealth.requestScanRestart]) is picked up within a few seconds. */
        private const val WATCHDOG_INTERVAL_MS = 5_000L

        /**
         * If we haven't seen a callback in this long, assume opportunistic demotion.
         * 20 s is ~7× a typical G300 reporting interval, comfortably above the noise
         * floor of dropped advs in a populated BLE area but quick enough that an
         * unstuck recovery completes inside a single staleness window
         * (`SensorFreshness.staleThresholdMs` = 15 s for a 3 s interval) most of the
         * time.
         */
        private const val WATCHDOG_STALL_THRESHOLD_MS = 20_000L

        /** Brief settle between stop+start so the OS treats us as a new scanner. */
        private const val WATCHDOG_RESTART_DELAY_MS = 200L

        /** How often to emit a per-process snapshot of BLE callback stats. */
        private const val HEALTH_SNAPSHOT_INTERVAL_MS = 30_000L
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    // Nordic Scanner is accessed via getScanner()
    private val scanner = BluetoothLeScannerCompat.getScanner()

    private var activeScanCallback: ScanCallback? = null

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan(): Flow<ScannedSensor> = callbackFlow {
        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth not supported")
            close()
            return@callbackFlow
        }

        stopScan()

        // Wall-clock of the last callback we got. Updated from onScanResult /
        // onBatchScanResults. Watchdog (below) uses it to detect that Android has
        // silently demoted us to SCAN_MODE_OPPORTUNISTIC — observed in BtGatt logs as
        // "Moving scan client to opportunistic". That demotion produces no onScanFailed
        // and no log on our side; we only notice because callbacks stop.
        val lastCallbackAtMs = AtomicLong(System.currentTimeMillis())

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val now = System.currentTimeMillis()
                lastCallbackAtMs.set(now)
                // Per-device variant — feeds both the global "any callback" pulse and the
                // per-address stats that the 30 s health snapshot dumps to logcat. Without
                // the per-device side it's impossible to grep "did THIS sensor stop talking
                // 90 s before the global silence" from the post-incident logs.
                BleScanHealth.recordDeviceCallback(result.device.address)
                val record = result.scanRecord
                // DEBUG: Log all devices with any manufacturer data to find the Setec prototype
                val mfgSparse = record?.manufacturerSpecificData
                if (mfgSparse != null && mfgSparse.size() > 0) {
                    val known = setOf(0x000D, 0x0059) // Skip known Mopeka IDs to reduce noise
                    for (i in 0 until mfgSparse.size()) {
                        val companyId = mfgSparse.keyAt(i)
                        if (companyId !in known) {
                            val data = mfgSparse.valueAt(i)
                            val hex = data?.joinToString(",") { "%02X".format(it) } ?: "null"
                            Timber.d("BLE UNKNOWN mfg 0x${"%04X".format(companyId)} from ${result.device.address} name=${result.device.name ?: record?.deviceName ?: "?"} rssi=${result.rssi} data=[$hex]")
                        }
                    }
                }
                // Nordic's ScanResult is passed here
                parseScanResult(result)?.let { trySend(it) }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                lastCallbackAtMs.set(System.currentTimeMillis())
                results.forEach { result ->
                    BleScanHealth.recordDeviceCallback(result.device.address)
                    parseScanResult(result)?.let { trySend(it) }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.e("Nordic BLE scan failed: $errorCode")
                val reason = when (errorCode) {
                    1 -> "BLE scan already active"
                    2 -> "App could not register for BLE scanning"
                    3 -> "BLE scan internal error"
                    4 -> "BLE feature not supported on this device"
                    else -> "BLE scan error (code $errorCode)"
                }
                close(BleScanException(reason, errorCode))
            }
        }

        activeScanCallback = callback

        try {
            // Nordic scanner handles compatibility across Android versions internally
            scanner.startScan(buildScanFilters(), buildScanSettings(), callback)
            Timber.d("Nordic BLE scan started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start scan")
            close(e)
        }

        // Watchdog: every WATCHDOG_INTERVAL_MS, check that callbacks have arrived within
        // WATCHDOG_STALL_THRESHOLD_MS *or* that UI code has flagged a stall via
        // BleScanHealth.requestScanRestart. If either tripwire fires, the OS has either
        // demoted us to opportunistic or the user is staring at an offline sensor — stop
        // + re-register the scan to count as a new client and pull us back into active
        // scanning. Runs on the callbackFlow's coroutine scope so it dies with the flow.
        val watchdog = launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                val silentForMs = System.currentTimeMillis() - lastCallbackAtMs.get()
                val uiRequested = BleScanHealth.consumeScanRestartRequest()
                val timeoutHit = silentForMs > WATCHDOG_STALL_THRESHOLD_MS
                if (uiRequested || timeoutHit) {
                    val reason = when {
                        uiRequested && timeoutHit -> "ui+timeout(${silentForMs / 1000}s)"
                        uiRequested -> "ui-request"
                        else -> "timeout(${silentForMs / 1000}s)"
                    }
                    Timber.w("BLE scan stalled — restarting scanner [reason=$reason]")
                    BleScanHealth.recordWatchdogRestart(reason)
                    try {
                        scanner.stopScan(callback)
                    } catch (e: Exception) {
                        Timber.w(e, "stopScan during watchdog restart failed (continuing)")
                    }
                    // Brief settle before re-registering so the OS sees this as a fresh client.
                    delay(WATCHDOG_RESTART_DELAY_MS)
                    try {
                        scanner.startScan(buildScanFilters(), buildScanSettings(), callback)
                        lastCallbackAtMs.set(System.currentTimeMillis())
                        Timber.d("BLE scan re-registered by watchdog")
                    } catch (e: Exception) {
                        Timber.e(e, "Watchdog scan-restart failed")
                    }
                }
            }
        }

        // Periodic snapshot of BLE health — emits one line per 30 s with the global
        // "last callback" gap, watchdog restart counter, and per-device callback counts.
        // Designed for `adb logcat | grep BleHealth` post-incident grep: lets us answer
        // "did the radio go quiet across the board, or did this one address stop
        // talking while others kept streaming?" from the saved log.
        val healthLogger = launch {
            while (isActive) {
                delay(HEALTH_SNAPSHOT_INTERVAL_MS)
                logHealthSnapshot()
            }
        }

        awaitClose {
            watchdog.cancel()
            healthLogger.cancel()
            stopScan()
        }
    }

    /** Dump a one-shot picture of BLE callback stats to logcat under the BleHealth tag. */
    private fun logHealthSnapshot() {
        val snap = BleScanHealth.snapshot()
        val now = System.currentTimeMillis()
        val anyGapS = if (snap.lastAnyCallbackMs > 0) (now - snap.lastAnyCallbackMs) / 1000 else -1
        val restartGapS = if (snap.lastWatchdogRestartMs > 0)
            (now - snap.lastWatchdogRestartMs) / 1000 else -1
        Timber.tag("BleHealth").i(
            "snapshot anyGap=${anyGapS}s restarts=${snap.watchdogRestarts}" +
                    " lastRestart=${restartGapS}s ago devices=${snap.deviceCallbackCounts.size}"
        )
        snap.deviceCallbackCounts.forEach { (addr, count) ->
            val deviceGapS = snap.deviceLastSeen[addr]?.let { (now - it) / 1000 } ?: -1
            Timber.tag("BleHealth").i("  device=$addr callbacks=$count lastGap=${deviceGapS}s")
        }
    }

    fun stopScan() {
        val callback = activeScanCallback ?: return
        try {
            scanner.stopScan(callback)
            Log.d(TAG, "Nordic BLE scan stopped")
        } catch (e: Exception) {
            Log.w(TAG, "StopScan error: ${e.message}")
        }
        activeScanCallback = null
    }

    // --- Builders (Now using Nordic classes) ---

    private fun buildScanSettings(): ScanSettings =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setUseHardwareBatchingIfSupported(true) // Nordic specific optimization
            .build()

    private fun buildScanFilters(): List<ScanFilter> {
        // ScanFilters are OR'd by the OS — each advertisement is delivered once if it
        // matches *any* filter. Listing both service UUID and manufacturer-ID filters as
        // separate entries (rather than combining them in a single filter) avoids the
        // Android 11 quirk where mixed criteria inside one filter silently dropped the
        // manufacturer-data side.
        val emptyMfgPrefix = ByteArray(0)
        val emptyMfgMask = ByteArray(0)
        return listOf(
            // CC2540 (Texas Instruments)
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID_CC2540))
                .build(),
            ScanFilter.Builder()
                .setManufacturerData(BleConstants.MANUFACTURER_ID_CC2540, emptyMfgPrefix, emptyMfgMask)
                .build(),
            // NRF52 (Nordic Semiconductor)
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID_NRF52))
                .build(),
            ScanFilter.Builder()
                .setManufacturerData(BleConstants.MANUFACTURER_ID_NRF52, emptyMfgPrefix, emptyMfgMask)
                .build(),
            // Setec / Sigmawit. G300 broadcasts 0x3000 service UUID, 0x051F mfg ID,
            // and a fixed advertising name "G300". The manufacturer-data prefix locks
            // the filter to the Sigmawit company-ID slot (byte 1, bits 0-6 = 0x01;
            // bit 7 is the sync flag and stays masked out).
            //
            // Each criterion is its own ScanFilter so the OS has multiple quick paths
            // to recognise a G300 even if one field is occasionally trimmed.
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID_SETEC))
                .build(),
            ScanFilter.Builder()
                .setManufacturerData(
                    BleConstants.MANUFACTURER_ID_SETEC,
                    /* prefix */ byteArrayOf(
                        BleConstants.SETEC_DATA_TYPE_3RD_PARTY.toByte(),
                        BleConstants.SETEC_COMPANY_SIGMAWIT.toByte()
                    ),
                    /* mask   */ byteArrayOf(0xFF.toByte(), 0x7F.toByte())
                )
                .build(),
            ScanFilter.Builder()
                .setDeviceName(BleConstants.SETEC_G300_ADV_NAME)
                .build()
        )
    }

    // --- Parsing ---

    private fun parseScanResult(result: ScanResult): ScannedSensor? {
        val address = result.device.address
        val record = result.scanRecord ?: return null
        val hwType = detectHardwareType(record) ?: return null

        val mfgId = when (hwType) {
            HwType.CC2540 -> BleConstants.MANUFACTURER_ID_CC2540
            HwType.NRF52 -> BleConstants.MANUFACTURER_ID_NRF52
            HwType.SETEC -> BleConstants.MANUFACTURER_ID_SETEC
        }

        val mfgData = record.getManufacturerSpecificData(mfgId) ?: return null

        // Log 1: Raw Data (Hex format is best for BLE)
        val rawHex = mfgData.joinToString("") { "%02X ".format(it) }
        Timber.v("[$address] Raw MfgData ($hwType): $rawHex")

        val parsed = parseAdvertData(hwType, mfgData, result) ?: run {
            Timber.e("[$address] Parsing failed for raw: $rawHex")
            return null
        }

//        if (!parsed.sensorType.isLpg) return null

        return ScannedSensor(
            address = address,
            name = result.device.name ?: record.deviceName,
            rssi = result.rssi,
            parsed = parsed
        )
    }

    /**
     * Helper to convert ByteArray to Hex String: [0x01, 0xFF] -> "01FF"
     */
    private fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

    private fun detectHardwareType(record: no.nordicsemi.android.support.v18.scanner.ScanRecord): HwType? {
        val serviceUuids = record.serviceUuids
        return when {
            serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID_CC2540 } == true -> HwType.CC2540
            serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID_NRF52 } == true -> HwType.NRF52
            serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID_SETEC } == true -> HwType.SETEC
            record.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_SETEC) != null -> HwType.SETEC
            else -> null
        }
    }

    private fun parseAdvertData(hwType: HwType, data: ByteArray, result: ScanResult) =
        when (hwType) {
            HwType.CC2540 -> SensorAdvertParser.parseCC2540(data, result.rssi, result.device.address)
            HwType.NRF52 -> SensorAdvertParser.parseNRF52(data, result.rssi, result.device.address)
            HwType.SETEC -> SensorAdvertParser.parseSetec(data, result.rssi, result.device.address)
        }

    private enum class HwType { CC2540, NRF52, SETEC }
}
/**
 * Data model for a detected sensor
 */
data class ScannedSensor(
    val address: String,
    val name: String?,
    val rssi: Int = 0,
    val parsed: ParsedSensor?=null
)

class BleScanException(message: String, val errorCode: Int) : Exception(message)