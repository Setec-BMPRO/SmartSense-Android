package com.mopeka.bmpro.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.smartsense.app.domain.model.MopekaSensorType
import com.smartsense.app.domain.model.SensorReading
import com.smartsense.app.data.ble.BleConstants
import com.smartsense.app.data.ble.SensorAdvertParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class ScannedSensor(
    val address: String,
    val name: String?,
    val reading: SensorReading?,
    val sensorType: MopekaSensorType,
    val syncPressed: Boolean,
    val rssi: Int,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context
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

    private var currentCallback: ScanCallback? = null

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    val isBluetoothSupported: Boolean
        get() = bluetoothAdapter != null

    /**
     * Start scanning for Mopeka BLE sensors.
     * Emits ScannedSensor objects as they are discovered.
     */
    @SuppressLint("MissingPermission")
    fun startScan(): Flow<ScannedSensor> = callbackFlow {
        val leScanner = scanner
        if (leScanner == null) {
            Log.w(TAG, "BLE scanner not available")
            close()
            return@callbackFlow
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        // Scan for all BLE devices — we filter Mopeka sensors by manufacturer data in parseScanResult
        val scanFilters = listOf<ScanFilter>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val sensor = parseScanResult(result)
                if (sensor != null) {
                    trySend(sensor)
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { result ->
                    val sensor = parseScanResult(result)
                    if (sensor != null) {
                        trySend(sensor)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed with error code: $errorCode")
                close(Exception("BLE scan failed: $errorCode"))
            }
        }

        currentCallback = callback

        try {
            leScanner.startScan(scanFilters, scanSettings, callback)
            Log.d(TAG, "BLE scan started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLE permissions", e)
            close(e)
            return@callbackFlow
        }

        awaitClose {
            stopScanInternal(leScanner, callback)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val callback = currentCallback ?: return
        val leScanner = scanner ?: return
        stopScanInternal(leScanner, callback)
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal(leScanner: BluetoothLeScanner, callback: ScanCallback) {
        try {
            leScanner.stopScan(callback)
            Log.d(TAG, "BLE scan stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping scan", e)
        }
        if (currentCallback === callback) {
            currentCallback = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun parseScanResult(result: ScanResult): ScannedSensor? {
        val record = result.scanRecord ?: return null
        // ---------------------------------------------------------------
        // Get manufacturer-specific data for Mopeka's manufacturer ID (0x000D)
        // ---------------------------------------------------------------
        val mfgData = record.getManufacturerSpecificData(BleConstants.MOPEKA_MANUFACTURER_ID)
            ?: return null // Not a Mopeka device
        // Try parsing as CC2540 (20/23 byte payload) or NRF52 (10 byte payload)
        // Pass BLE address for MAC validation (Mopeka duplicates last 3 MAC bytes in payload)
        val parsed = SensorAdvertParser.parse(mfgData, result.rssi, result.device.address)
        if (parsed == null) {
            return null
        }

        // Only show LPG sensors
        if (!parsed.sensorType.isLpg) {
            Log.d(TAG, "Skipping non-LPG sensor ${result.device.address}: ${parsed.sensorType.displayName}")
            return null
        }
        Log.d("-----",parsed.toString())

        // ---------------------------------------------------------------
        // Extract BLE advertised name
        // ---------------------------------------------------------------
        val bleDeviceName: String? = try {
            result.device?.name
        } catch (_: SecurityException) {
            null
        }
        val scanRecordName = record.deviceName
        val rawLocalName = parseLocalNameFromBytes(record.bytes)
        val deviceName = bleDeviceName ?: scanRecordName ?: rawLocalName

        Log.d(TAG, "MOPEKA_LPG ${result.device.address}: " +
                "type=${parsed.sensorType.displayName}, rssi=${result.rssi}")

        return ScannedSensor(
            address = result.device.address,
            name = deviceName,
            reading = parsed.reading,
            sensorType = parsed.sensorType,
            syncPressed = parsed.syncPressed,
            rssi = result.rssi
        )
    }

    /**
     * Parse the Complete Local Name (0x09) or Shortened Local Name (0x08) from
     * raw BLE advertisement bytes as a fallback when ScanRecord.deviceName is null.
     */
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

    private fun getReadableByteArray(data: ByteArray):String {
        return data.joinToString(
            prefix = "[",
            postfix = "]"
        ) { (it.toInt() and 0xFF).toString() }
    }
}
