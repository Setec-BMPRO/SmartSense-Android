package com.smartsense.app.data.ble

import java.util.UUID

object BleConstants {
    private const val BASE_UUID = "0000%s-0000-1000-8000-00805f9b34fb"

    fun shortUuid(shortId: String): UUID = UUID.fromString(BASE_UUID.format(shortId))

    /** CC2540 manufacturer ID: 0x000D (Texas Instruments) */
    const val MANUFACTURER_ID_CC2540 = 0x000D

    /** NRF52 manufacturer ID: 0x0059 (Nordic Semiconductor) */
    const val MANUFACTURER_ID_NRF52 = 0x0059

    /** Default RSSI threshold — reject signals weaker than this */
    const val DEFAULT_RSSI_THRESHOLD = -60

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
    }

    /** BMPRO brand filter: device byte (masked 0xCF) must be one of these */
    val BMPRO_ACCEPTED_DEVICE_BYTES = setOf(0x46, 0x48)

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
}
