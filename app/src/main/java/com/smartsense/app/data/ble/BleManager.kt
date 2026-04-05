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
                // Nordic's ScanResult is passed here
                parseScanResult(result)?.let { trySend(it) }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { result ->
                    parseScanResult(result)?.let { trySend(it) }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Nordic BLE scan failed: $errorCode")
            }
        }

        activeScanCallback = callback

        try {
            // Nordic scanner handles compatibility across Android versions internally
            scanner.startScan(buildScanFilters(), buildScanSettings(), callback)
            Log.d(TAG, "Nordic BLE scan started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan", e)
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
        listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID_CC2540)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID_NRF52)).build()
        )

    // --- Parsing ---

    private fun parseScanResult(result: ScanResult): ScannedSensor? {
        // Nordic's result.scanRecord is the same as native
        val record = result.scanRecord ?: return null

        if (!isValidRssi(result.rssi)) return null

        val hwType = detectHardwareType(record.serviceUuids) ?: return null

        // Use the manufacturer ID to get data
        val mfgData = when (hwType) {
            HwType.CC2540 -> record.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_CC2540)
            HwType.NRF52 -> record.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID_NRF52)
        } ?: return null

        val parsed = parseAdvertData(hwType, mfgData, result) ?: return null
        if (!parsed.sensorType.isLpg) return null

        return ScannedSensor(
            address = result.device.address,
            name = result.device.name ?: record.deviceName,
            parsed = parsed
        )
    }

    private fun isValidRssi(rssi: Int): Boolean = rssi >= BleConstants.DEFAULT_RSSI_THRESHOLD

    private fun detectHardwareType(serviceUuids: List<ParcelUuid>?): HwType? {
        return when {
            serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID_CC2540 } == true -> HwType.CC2540
            serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID_NRF52 } == true -> HwType.NRF52
            else -> null
        }
    }

    private fun parseAdvertData(hwType: HwType, data: ByteArray, result: ScanResult) =
        when (hwType) {
            HwType.CC2540 -> SensorAdvertParser.parseCC2540(data, result.rssi, result.device.address)
            HwType.NRF52 -> SensorAdvertParser.parseNRF52(data, result.rssi, result.device.address)
        }

    private enum class HwType { CC2540, NRF52 }
}
/**
 * Data model for a detected sensor
 */
data class ScannedSensor(
    val address: String,
    val name: String?,
    val parsed: ParsedSensor?=null
)