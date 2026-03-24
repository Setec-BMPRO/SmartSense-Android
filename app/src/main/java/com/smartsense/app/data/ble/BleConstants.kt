package com.smartsense.app.data.ble

import java.util.UUID

object BleConstants {
    // Base UUID for BLE short UUIDs
    private const val BASE_UUID = "0000%s-0000-1000-8000-00805f9b34fb"

    fun shortUuid(shortId: String): UUID = UUID.fromString(BASE_UUID.format(shortId))

    // ---------------------------------------------------------------------------
    // Mopeka Manufacturer IDs (from decompiled original app)
    // ---------------------------------------------------------------------------

    /**
     * Primary Mopeka manufacturer ID used in BLE advertisement manufacturer-specific data.
     * This is 0x000D (Texas Instruments), NOT 0x0059 (Nordic).
     * Byte 0 of manufacturer data = 0x0D, Byte 1 = 0x00.
     */
    const val MOPEKA_MANUFACTURER_ID = 0x000D

    /** Gateway/bridge device manufacturer byte */
    const val GATEWAY_MANUFACTURER_BYTE: Byte = 0x44 // 68
    const val GATEWAY_IDENTIFIER_BYTE: Byte = 0x2F   // 47

    // ---------------------------------------------------------------------------
    // Hardware types determined by BLE service UUIDs in advertisement
    // ---------------------------------------------------------------------------

    /** Service UUID indicating CC2540 hardware */
    val SERVICE_UUID_CC2540: UUID = shortUuid("ADA0")

    /** Service UUID indicating NRF52 hardware */
    val SERVICE_UUID_NRF52: UUID = shortUuid("FEE5")

    // ---------------------------------------------------------------------------
    // Sensor type constants (from byte 2/3 of manufacturer data)
    // ---------------------------------------------------------------------------

    object SensorType {
        const val UNKNOWN = 0x00
        const val STANDARD_BOTTOM_UP = 0x03    // Standard propane (Pro Check)
        const val TOP_DOWN_AIR_ABOVE = 0x04    // Top-down air space
        const val BOTTOM_UP_WATER = 0x05       // Bottom-up water
        const val LIPPERT_BOTTOM_UP = 0x06     // Lippert propane
        const val PLUS_BOTTOM_UP = 0x08        // Pro Plus BLE LPG
        const val PRO_UNIVERSAL = 0x0C         // Pro Universal
    }

    // ---------------------------------------------------------------------------
    // BMPRO app brand — device byte 3 (masked 0xCF) filter values
    // For BMPRO brand: accept 0x46 or 0x48
    // ---------------------------------------------------------------------------

    val BMPRO_ACCEPTED_DEVICE_BYTES = setOf(0x46, 0x48)

    // ---------------------------------------------------------------------------
    // CC2540 advertisement data sizes
    // ---------------------------------------------------------------------------

    const val CC2540_DATA_SIZE_SHORT = 22
    const val CC2540_DATA_SIZE_LONG = 25

    /** NRF52 manufacturer data is exactly 12 bytes */
    const val NRF52_DATA_SIZE = 12

    // ---------------------------------------------------------------------------
    // Scan settings
    // ---------------------------------------------------------------------------

    const val SCAN_PERIOD_MS = 10_000L
    const val SCAN_INTERVAL_MS = 2_000L
    const val SENSOR_STALE_TIMEOUT_MS = 60_000L

    // ---------------------------------------------------------------------------
    // Temperature compensation coefficients for propane depth (mm)
    // depth_mm = raw_us * (coeff[0] + coeff[1]*temp + coeff[2]*temp^2)
    // ---------------------------------------------------------------------------

    val PROPANE_COEFFICIENTS = doubleArrayOf(0.573045, -0.002822, -0.00000535)
}
