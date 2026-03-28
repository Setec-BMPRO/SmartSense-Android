package com.smartsense.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BleManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "BleManager"
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? =
        bluetoothManager?.adapter

    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    // Persistent reference to the active callback to ensure stopScan works
    private var activeScanCallback: ScanCallback? = null

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    // --------------------------------------
    // 🔍 SCAN
    // --------------------------------------

    @SuppressLint("MissingPermission")
    fun startScan(): Flow<ScannedSensor> = callbackFlow {
        val leScanner = scanner ?: run {
            Log.w(TAG, "BLE scanner not available")
            close()
            return@callbackFlow
        }

        // 1. Stop any existing scan before starting a new one
        stopScan()

        // 2. Define the callback instance
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                parseScanResult(result)?.let { trySend(it) }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { result ->
                    parseScanResult(result)?.let { trySend(it) }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed: $errorCode")
                // You could choose to close(Exception("Scan failed $errorCode")) here
            }
        }

        // 3. Store reference and start
        activeScanCallback = callback

        try {
            leScanner.startScan(buildScanFilters(), buildScanSettings(), callback)
            Log.d(TAG, "BLE scan started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan", e)
            close(e)
        }

        // 4. Cleanup when flow is cancelled or closed
        awaitClose {
            Log.d(TAG, "Flow collection ended, stopping scan...")
            stopScan()
        }
    }

    /**
     * Public method to manually stop the scan.
     * Can be called from UI or via awaitClose.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        val leScanner = scanner ?: return
        val callback = activeScanCallback ?: return

        // Verify Bluetooth is still ON before trying to stop
        if (bluetoothAdapter?.state == BluetoothAdapter.STATE_ON) {
            try {
                leScanner.stopScan(callback)
                Log.d(TAG, "BLE scan stopped successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Error while calling stopScan: ${e.message}")
            }
        } else {
            Log.w(TAG, "Bluetooth is OFF, stopScan call skipped (hardware handled)")
        }

        activeScanCallback = null
    }

    // --------------------------------------
    // 🏗️ BUILDERS
    // --------------------------------------

    private fun buildScanSettings(): ScanSettings =
        ScanSettings.Builder()
            // SCAN_MODE_LOW_LATENCY: Continuous scanning (High battery use)
            // SCAN_MODE_BALANCED: Scans for ~2s, pauses for ~3s
            // SCAN_MODE_LOW_POWER: Scans for ~0.5s, pauses for ~4.5s
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setReportDelay(0)
            .build()

    private fun buildScanFilters(): List<ScanFilter> =
        listOf(
            createFilter(BleConstants.SERVICE_UUID_CC2540),
            createFilter(BleConstants.SERVICE_UUID_NRF52)
        )

    private fun createFilter(uuid: java.util.UUID): ScanFilter =
        ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(uuid))
            .build()

    // --------------------------------------
    // 🔎 PARSING
    // --------------------------------------

    @SuppressLint("MissingPermission")
    private fun parseScanResult(result: ScanResult): ScannedSensor? {
        val record = result.scanRecord ?: return null

        if (!isValidRssi(result.rssi)) return null

        val hwType = detectHardwareType(record.serviceUuids) ?: return null
        val mfgData = getManufacturerData(record, hwType) ?: return null
        val parsed = parseAdvertData(hwType, mfgData, result) ?: return null

        // Specific filtering for LPG sensors
        if (!parsed.sensorType.isLpg) return null

        val deviceName = resolveDeviceName(result, record)
        logScan(result, parsed)

        return ScannedSensor(
            address = result.device.address,
            name = deviceName,
            parsed = parsed
        )
    }

    private fun isValidRssi(rssi: Int): Boolean =
        rssi >= BleConstants.DEFAULT_RSSI_THRESHOLD

    private fun detectHardwareType(serviceUuids: List<ParcelUuid>?): HwType? {
        return when {
            serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID_CC2540 } == true -> HwType.CC2540
            serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID_NRF52 } == true -> HwType.NRF52
            else -> null
        }
    }

    private fun getManufacturerData(record: android.bluetooth.le.ScanRecord, hwType: HwType): ByteArray? {
        return when (hwType) {
            HwType.CC2540 -> record.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_CC2540)
            HwType.NRF52 -> record.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_NRF52)
        }
    }

    private fun parseAdvertData(hwType: HwType, data: ByteArray, result: ScanResult) =
        when (hwType) {
            HwType.CC2540 -> SensorAdvertParser.parseCC2540(data, result.rssi, result.device.address)
            HwType.NRF52 -> SensorAdvertParser.parseNRF52(data, result.rssi, result.device.address)
        }

    private fun resolveDeviceName(result: ScanResult, record: android.bluetooth.le.ScanRecord): String? {
        val bleName = try {
            result.device?.name
        } catch (_: SecurityException) {
            null
        }

        return bleName
            ?: record.deviceName
            ?: parseLocalNameFromBytes(record.bytes)
    }

    private fun logScan(result: ScanResult, parsed: ParsedSensor) {
        Log.d(TAG, "SCAN [${parsed.sensorType.displayName}] ${result.device.address} | RSSI: ${result.rssi}")
    }

    private fun parseLocalNameFromBytes(bytes: ByteArray?): String? {
        if (bytes == null) return null
        var offset = 0
        while (offset < bytes.size) {
            val length = bytes[offset].toInt() and 0xFF
            if (length == 0) break
            if (offset + length >= bytes.size) break

            val type = bytes[offset + 1].toInt() and 0xFF
            if (type == 0x09 || type == 0x08) {
                val nameBytes = bytes.copyOfRange(offset + 2, offset + 1 + length)
                return String(nameBytes, Charsets.UTF_8).trim()
            }
            offset += length + 1
        }
        return null
    }

    private enum class HwType { CC2540, NRF52 }
}

/**
 * Data model for a detected sensor
 */
data class ScannedSensor(
    val address: String,
    val name: String?,
    val parsed: ParsedSensor,
    val timestampMillis: Long = System.currentTimeMillis()
)