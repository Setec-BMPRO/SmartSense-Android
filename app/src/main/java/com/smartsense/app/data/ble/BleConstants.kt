package com.smartsense.app.data.ble

import java.util.UUID

object BleConstants {
    private const val BASE_UUID = "0000%s-0000-1000-8000-00805f9b34fb"
    const val MOPEKA_MANUFACTURER_ID = 0x000D
    fun shortUuid(shortId: String): UUID = UUID.fromString(BASE_UUID.format(shortId))

    /** CC2540 manufacturer ID: 0x000D (Texas Instruments) */
    const val MANUFACTURER_ID_CC2540 = 0x000D

    /** NRF52 manufacturer ID: 0x0059 (Nordic Semiconductor) */
    const val MANUFACTURER_ID_NRF52 = 0x0059

    /** Setec manufacturer ID: 0x051F (Setec Pty Ltd) */
    const val MANUFACTURER_ID_SETEC = 0x051F

    /** Default RSSI threshold — reject signals weaker than this */
    const val DEFAULT_RSSI_THRESHOLD = -95

    /** Relaxed RSSI threshold for sync-pressed (pairing) devices */
    const val SYNC_RSSI_THRESHOLD = -100

    /** Service UUID indicating CC2540 hardware */
    val SERVICE_UUID_CC2540: UUID = shortUuid("ADA0")

    /** Service UUID indicating NRF52 hardware */
    val SERVICE_UUID_NRF52: UUID = shortUuid("FEE5")

    object SensorType {
        const val UNKNOWN = 0x00
        const val STANDARD_BOTTOM_UP = 0x03
        const val TOP_DOWN_AIR_ABOVE = 0x04
        const val BOTTOM_UP_WATER = 0x05
        const val LIPPERT_BOTTOM_UP = 0x06
        const val PLUS_BOTTOM_UP = 0x08
        const val PRO_UNIVERSAL = 0x0C
        const val GAS_SENSOR = 0x06
    }

    /** Setec 3rd-party sensor data type marker */
    const val SETEC_DATA_TYPE_3RD_PARTY = 0xFF

    /** Sigmawit company ID within the Setec protocol */
    const val SETEC_COMPANY_SIGMAWIT = 0x01

    /** Setec payload size after manufacturer ID is stripped (bytes 14-30 = 17 bytes) */
    const val SETEC_PAYLOAD_SIZE = 17

    /** Accepted CC2540 device bytes (masked 0xCF): BMPRO (0x46, 0x48) + standard Mopeka (0x02 Std, 0x03 XL) */
    val BMPRO_ACCEPTED_DEVICE_BYTES = setOf(0x02, 0x03, 0x46, 0x48)

    const val CC2540_DATA_SIZE_SHORT = 22
    const val CC2540_DATA_SIZE_LONG = 25

    const val NRF52_DATA_SIZE = 12

    const val SCAN_PERIOD_MS = 10_000L
    const val SCAN_INTERVAL_MS = 2_000L
    const val SENSOR_STALE_TIMEOUT_MS = 60_000L

    /**
     * Temperature compensation coefficients for propane depth (mm).
     * depth_mm = raw_us * (coeff[0] + coeff[1]*temp + coeff[2]*temp^2)
     */
    val PROPANE_COEFFICIENTS = doubleArrayOf(0.573045, -0.002822, -0.00000535)

    // --- BMPRO Hub Constants ---

    /** BMPRO Hub advertised service UUID */
    val HUB_SERVICE_UUID: UUID = shortUuid("FE0E")

    /** Hub sensor data characteristic (notifications) */
    val HUB_SENSOR_CHARACTERISTIC: UUID = UUID.fromString("5e132001-73a9-4a6a-ae5f-c7bff0bd075e")

    /** Hub authentication characteristic */
    val HUB_AUTH_CHARACTERISTIC: UUID = UUID.fromString("aaf94c01-906b-49e8-8b5b-b5f412986d26")

    /** Client Characteristic Configuration Descriptor for enabling notifications */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Hub device name prefix for discovery */
    const val HUB_DEVICE_NAME_PREFIX = "RVMN101C"

    /** Hub authentication AES key (128-bit, hex-encoded) */
    const val HUB_AUTH_KEY = "73251E071383F24F18103D347C83ADE3"

    /** Hub requested MTU */
    const val HUB_MTU = 182

    /** Propane sensor type ID in hub payload */
    const val HUB_SENSOR_TYPE_PROPANE = 3

    /** Propane sensor slot range in hub (17-19) */
    val HUB_PROPANE_SLOTS = 17..19
}
