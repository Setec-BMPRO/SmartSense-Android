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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
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
                results.forEach { result ->
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

        awaitClose {
            stopScan()
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

    private fun buildScanFilters(): List<ScanFilter> =
        // Empty filter list = accept all advertisements; filtering is done in parseScanResult.
        // Mixing service UUID and manufacturer data filters caused the manufacturer data
        // filter to be silently dropped on some Android versions (notably Android 11).
        emptyList()

    // --- Parsing ---

    private fun parseScanResult(result: ScanResult): ScannedSensor? {
        // Nordic's result.scanRecord is the same as native
        val record = result.scanRecord ?: return null

        val hwType = detectHardwareType(record) ?: return null

        // Use the manufacturer ID to get data
        val mfgData = when (hwType) {
            HwType.CC2540 -> record.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_CC2540)
            HwType.NRF52 -> record.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_NRF52)
            HwType.SETEC -> record.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_SETEC)
        } ?: return null

        val parsed = parseAdvertData(hwType, mfgData, result) ?: return null
        if (!parsed.sensorType.isLpg) return null

        return ScannedSensor(
            address = result.device.address,
            name = result.device.name ?: record.deviceName,
            rssi = result.rssi,
            parsed = parsed
        )
    }

    private fun detectHardwareType(record: no.nordicsemi.android.support.v18.scanner.ScanRecord): HwType? {
        val serviceUuids = record.serviceUuids
        return when {
            serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID_CC2540 } == true -> HwType.CC2540
            serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID_NRF52 } == true -> HwType.NRF52
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