package com.smartsense.app.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Data from a propane sensor relayed through the BMPRO hub.
 */
data class HubPropaneSensorData(
    val slotIndex: Int,
    val locationId: Int,
    val tankLevelPercent: Int,
    val tankHeightMm: Int,
    val batteryVoltage: Float,
    val secondsSinceUpdate: Int
)

enum class HubConnectionState {
    DISCONNECTED, SCANNING, CONNECTING, AUTHENTICATING, CONNECTED
}

/**
 * Manages the BLE GATT connection to a BMPRO hub (RVMN101C).
 *
 * The hub aggregates Mopeka sensor readings and relays them via
 * GATT notifications with pre-calculated tank level percentages.
 */
@Singleton
class BmproHubManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BmproHubManager"
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val scanner = BluetoothLeScannerCompat.getScanner()

    private var gatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private var savedHubAddress: String? = null

    // Device authentication identifier (random, persisted per session)
    private val deviceIdentifier: Int = Random.nextInt()

    private val _connectionState = MutableStateFlow(HubConnectionState.DISCONNECTED)
    val connectionState: StateFlow<HubConnectionState> = _connectionState.asStateFlow()

    private val _sensorData = MutableSharedFlow<HubPropaneSensorData>(extraBufferCapacity = 10)
    val sensorData: SharedFlow<HubPropaneSensorData> = _sensorData.asSharedFlow()

    // --- Public API ---

    @SuppressLint("MissingPermission")
    fun startHubDiscovery() {
        if (_connectionState.value != HubConnectionState.DISCONNECTED) {
            Timber.tag(TAG).d("Already connected or connecting, skipping discovery")
            return
        }

        // If we have a saved address, try direct connect first
        savedHubAddress?.let { address ->
            bluetoothAdapter?.getRemoteDevice(address)?.let { device ->
                Timber.tag(TAG).i("Reconnecting to saved hub: $address")
                connectToDevice(device)
                return
            }
        }

        _connectionState.value = HubConnectionState.SCANNING
        Timber.tag(TAG).i("Scanning for BMPRO hub...")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: result.scanRecord?.deviceName ?: return
                if (name.contains(BleConstants.HUB_DEVICE_NAME_PREFIX, ignoreCase = true)) {
                    Timber.tag(TAG).i("Found BMPRO hub: $name (${result.device.address})")
                    stopHubScan()
                    connectToDevice(result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.tag(TAG).e("Hub scan failed: $errorCode")
                _connectionState.value = HubConnectionState.DISCONNECTED
            }
        }

        scanCallback = callback

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanner.startScan(emptyList(), settings, callback)
    }

    fun disconnect() {
        stopHubScan()
        gatt?.let {
            @SuppressLint("MissingPermission")
            it.close()
        }
        gatt = null
        _connectionState.value = HubConnectionState.DISCONNECTED
        Timber.tag(TAG).i("Disconnected from hub")
    }

    // --- Internal ---

    private fun stopHubScan() {
        scanCallback?.let {
            try { scanner.stopScan(it) } catch (_: Exception) {}
        }
        scanCallback = null
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        _connectionState.value = HubConnectionState.CONNECTING
        savedHubAddress = device.address
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.tag(TAG).i("Connected to hub GATT, requesting MTU")
                    gatt.requestMtu(BleConstants.HUB_MTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.tag(TAG).w("Disconnected from hub (status=$status)")
                    _connectionState.value = HubConnectionState.DISCONNECTED
                    this@BmproHubManager.gatt = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Timber.tag(TAG).d("MTU changed to $mtu (status=$status)")
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.tag(TAG).e("Service discovery failed: $status")
                gatt.close()
                return
            }
            Timber.tag(TAG).i("Services discovered, starting authentication")
            _connectionState.value = HubConnectionState.AUTHENTICATING
            authenticate(gatt)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == BleConstants.HUB_AUTH_CHARACTERISTIC && status == BluetoothGatt.GATT_SUCCESS) {
                val token = characteristic.value
                Timber.tag(TAG).d("Auth token received (${token?.size} bytes)")
                sendAuthResponse(gatt, token)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == BleConstants.HUB_AUTH_CHARACTERISTIC) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Timber.tag(TAG).i("Authentication successful, setting up notifications")
                    _connectionState.value = HubConnectionState.CONNECTED
                    enableSensorNotifications(gatt)
                } else {
                    Timber.tag(TAG).e("Authentication write failed: $status")
                    gatt.close()
                }
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == BleConstants.HUB_SENSOR_CHARACTERISTIC) {
                val data = characteristic.value ?: return
                parseSensorNotification(data)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.tag(TAG).d("Notification descriptor written for ${descriptor.characteristic.uuid}")
            }
        }
    }

    // --- Authentication ---

    @SuppressLint("MissingPermission")
    private fun authenticate(gatt: BluetoothGatt) {
        val service = gatt.services.flatMap { it.characteristics }
            .find { it.uuid == BleConstants.HUB_AUTH_CHARACTERISTIC }

        if (service == null) {
            Timber.tag(TAG).e("Auth characteristic not found")
            gatt.close()
            return
        }

        gatt.readCharacteristic(service)
    }

    @SuppressLint("MissingPermission")
    private fun sendAuthResponse(gatt: BluetoothGatt, token: ByteArray?) {
        if (token == null || token.isEmpty()) {
            Timber.tag(TAG).e("Empty auth token")
            return
        }

        val characteristic = gatt.services.flatMap { it.characteristics }
            .find { it.uuid == BleConstants.HUB_AUTH_CHARACTERISTIC } ?: return

        val encrypted = encryptAuthToken(token)
        // Append device identifier (4 bytes, little-endian)
        val idBytes = ByteArray(4)
        idBytes[0] = (deviceIdentifier and 0xFF).toByte()
        idBytes[1] = ((deviceIdentifier shr 8) and 0xFF).toByte()
        idBytes[2] = ((deviceIdentifier shr 16) and 0xFF).toByte()
        idBytes[3] = ((deviceIdentifier shr 24) and 0xFF).toByte()

        val response = encrypted + idBytes
        characteristic.value = response
        gatt.writeCharacteristic(characteristic)
        Timber.tag(TAG).d("Auth response sent (${response.size} bytes)")
    }

    private fun encryptAuthToken(token: ByteArray): ByteArray {
        // Pad token to 16 bytes with zeros
        val padded = ByteArray(16)
        token.copyInto(padded, 0, 0, minOf(token.size, 16))

        val keyBytes = BleConstants.HUB_AUTH_KEY.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
        return cipher.doFinal(padded)
    }

    // --- Notifications ---

    @SuppressLint("MissingPermission")
    private fun enableSensorNotifications(gatt: BluetoothGatt) {
        val characteristic = gatt.services.flatMap { it.characteristics }
            .find { it.uuid == BleConstants.HUB_SENSOR_CHARACTERISTIC }

        if (characteristic == null) {
            Timber.tag(TAG).w("Sensor characteristic not found")
            return
        }

        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(BleConstants.CCCD_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
            Timber.tag(TAG).i("Sensor notifications enabled")
        }
    }

    // --- Parsing ---

    private fun parseSensorNotification(data: ByteArray) {
        if (data.size < 20) return

        val sensorType = data[1].toInt() and 0xFF
        if (sensorType != BleConstants.HUB_SENSOR_TYPE_PROPANE) return

        val slotIndex = data[0].toInt() and 0xFF
        val locationId = data[2].toInt() and 0xFF

        val secondsSinceUpdate = (data[9].toInt() and 0xFF) or
                ((data[10].toInt() and 0xFF) shl 8)

        // Invalid data marker
        if (secondsSinceUpdate == 65535) return

        val rawVoltage = data[14].toInt() and 0xFF
        val batteryVoltage = rawVoltage * 0.01f + 1.22f

        val tankHeight = (data[17].toInt() and 0xFF) or
                ((data[18].toInt() and 0xFF) shl 8)

        val tankLevel = data[19].toInt() and 0xFF

        val sensorData = HubPropaneSensorData(
            slotIndex = slotIndex,
            locationId = locationId,
            tankLevelPercent = tankLevel,
            tankHeightMm = tankHeight,
            batteryVoltage = batteryVoltage,
            secondsSinceUpdate = secondsSinceUpdate
        )

        Timber.tag(TAG).d("Hub propane: slot=$slotIndex loc=$locationId " +
                "level=$tankLevel% height=${tankHeight}mm battery=${"%.2f".format(batteryVoltage)}V " +
                "age=${secondsSinceUpdate}s")

        _sensorData.tryEmit(sensorData)
    }
}
