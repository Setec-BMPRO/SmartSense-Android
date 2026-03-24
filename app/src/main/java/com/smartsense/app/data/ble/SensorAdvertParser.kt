package com.smartsense.app.data.ble

import android.util.Log
import com.smartsense.app.domain.model.MopekaSensorType
import com.smartsense.app.domain.model.SensorReading
import timber.log.Timber

/**
 * Result of parsing a Mopeka advertisement, bundling reading + sensor type.
 */
data class ParsedSensor(
    val reading: SensorReading,
    val sensorType: MopekaSensorType,
    val syncPressed: Boolean = false
)

/**
 * Parses BLE advertisement manufacturer-specific data from Mopeka sensors.
 *
 * IMPORTANT: Android's ScanRecord.getManufacturerSpecificData() strips the 2-byte
 * manufacturer ID. So the data we receive starts AFTER the manufacturer ID (0x000D).
 *
 * **CC2540 format** (manufacturer payload = 21 or 23 bytes, total raw was 23 or 25):
 * - Byte 0: Reserved (0x00)
 * - Byte 1: Device/brand flags (masked 0xCF) — BMPRO accepts 0x46 or 0x48
 * - Byte 2: Battery + temperature combined byte
 * - Byte 3: Temperature lower bits / raw level bits
 * - Byte 4-onwards: Sensor readings (multiple 4-byte groups)
 * - Last 3 bytes: Duplicated MAC address bytes
 *
 * **NRF52 format** (manufacturer payload = 10 bytes, total raw was 12):
 * - Byte 0: Hardware/sensor type ID
 * - Byte 1: Battery (lower 7 bits)
 * - Byte 2: Temperature (lower 7 bits)
 * - Bytes 3-4: Raw distance (14-bit) + quality (upper 2 bits of byte 4)
 * - Bytes 5-7: MAC bytes
 * - Bytes 8-9: Accelerometer
 */
object SensorAdvertParser {

    private const val TAG = "SensorAdvertParser"

    // CC2540 payload sizes (after manufacturer ID stripped by Android)
    private const val CC2540_PAYLOAD_SHORT = 20  // was 22 raw (minus 2-byte mfg ID)
    private const val CC2540_PAYLOAD_LONG = 23   // was 25 raw

    // NRF52 payload size (after manufacturer ID stripped)
    private const val NRF52_PAYLOAD = 10         // was 12 raw

    /**
     * Try to parse CC2540-format manufacturer payload.
     *
     * From the decompiled source (dist.bundle.js, getTankCheckSensorMac_cc2540):
     * Raw bytes included 2-byte mfg ID at [0..1]. Android strips those, so:
     * - payload[0] = raw[2] — must NOT be 0xBB or 0xAA
     * - payload[1] = raw[3] — brand filter: (val & 0xCF) must be 0x46 or 0x48 for BMPRO
     * - payload[2] = raw[4] — battery (lower 7 bits / 32 = volts)
     * - payload[3] = raw[5] — temperature (lower 7 bits, offset -40°C)
     * - payload[4..5] = raw[6..7] — raw tank level (14-bit) + quality (bits 14-15)
     * - Last 3 bytes: Duplicated MAC
     */
    fun parseCC2540(data: ByteArray, rssi: Int, bleAddress: String): ParsedSensor? {
        if (data.size != CC2540_PAYLOAD_SHORT && data.size != CC2540_PAYLOAD_LONG) return null
        Timber.i("-----parseCC2540-data:${getReadableByteArray(data)}")
        return try {
            val byte0 = data[0].toInt() and 0xFF
            // Reject gateway/diagnostic packets
            if (byte0 == 0xBB || byte0 == 0xAA) return null

            val byte1 = data[1].toInt() and 0xFF
            // BMPRO brand filter: (byte1 & 0xCF) must be 0x46 or 0x48
            val maskedByte1 = byte1 and 0xCF
            if (maskedByte1 !in BleConstants.BMPRO_ACCEPTED_DEVICE_BYTES) {
                return null
            }

            // -----------------------------------------------------------
            // MAC validation: Mopeka CC2540 sensors duplicate the last 3
            // bytes of their MAC address at the end of the advertisement.
            // This is THE key filter to reject non-Mopeka TI devices.
            // -----------------------------------------------------------
            val macBytes = parseMacBytes(bleAddress)
            if (macBytes != null && data.size >= 3) {
                val payloadMac = byteArrayOf(
                    data[data.size - 3],
                    data[data.size - 2],
                    data[data.size - 1]
                )
                val deviceMac = byteArrayOf(macBytes[3], macBytes[4], macBytes[5])
                if (!payloadMac.contentEquals(deviceMac)) {
                    Log.d(TAG, "CC2540: MAC mismatch for $bleAddress — " +
                            "payload=[%02X:%02X:%02X] vs device=[%02X:%02X:%02X]".format(
                                payloadMac[0], payloadMac[1], payloadMac[2],
                                deviceMac[0], deviceMac[1], deviceMac[2]))
                    return null
                }
            }

            // Quality: bits 4-5 of byte 1 (hardware/device flags byte)
            val quality = (byte1 shr 4) and 0x03

            // Battery voltage: byte 2 — formula: (raw / 256) * 2 + 1.5
            val batteryRaw = data[2].toInt() and 0xFF
            val batteryVoltage = (batteryRaw / 256.0f) * 2.0f + 1.5f

            // Byte 3: Temperature (bits 0-5), slowUpdateRate (bit 6), syncPressed (bit 7)
            val byte3raw = data[3].toInt() and 0xFF
            val syncPressed = (byte3raw and 0x80) != 0
            val tempRaw = byte3raw and 0x3F  // 6-bit temperature
            val temperatureCelsius = if (tempRaw == 0) -40.0f
                else (1.776964f * (tempRaw - 25))

            // Byte 7: Raw tank level
            val tankLevelPercentage = data[7]

            // Bytes 4+ contain multiple measurement samples (up to 8 for Gen2)
            // Each sample encodes distance/time. For now extract first sample
            // and use speed-of-sound based conversion.
            // The raw level is in the measurement data, encoded per sample.
            // For simplicity, use the average of available samples.
            val heightMeters = extractHeightFromSamples(data, 4, temperatureCelsius)

            Log.d(TAG, "CC2540 VALID $bleAddress: battery=${"%.2f".format(batteryVoltage)}V, " +
                    "temp=${"%.1f".format(temperatureCelsius)}°C, quality=$quality, " +
                    "height=${"%.4f".format(heightMeters)}m, syncPressed=$syncPressed" +
                    ",tankLevelPercentage=$tankLevelPercentage")

            ParsedSensor(
                reading = SensorReading(
                    levelPercent = 0f,
                    rawHeightMeters = heightMeters,
                    batteryVoltage = batteryVoltage,
                    rssi = rssi,
                    quality = quality,
                    temperatureCelsius = temperatureCelsius,
                    firmwareVersion = "",
                    tankLevelPercentage = tankLevelPercentage.toInt() and 0xFF

                ),
                sensorType = MopekaSensorType.fromCC2540DeviceByte(byte1),
                syncPressed = syncPressed
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing CC2540 data", e)
            null
        }
    }

    /**
     * Try to parse NRF52-format manufacturer payload (10 bytes after mfg ID stripped).
     */
    fun parseNRF52(data: ByteArray, rssi: Int, bleAddress: String): ParsedSensor? {
        if (data.size != NRF52_PAYLOAD) return null

        return try {
            // MAC validation: NRF52 duplicates last 3 MAC bytes at positions 7-9
            val macBytes = parseMacBytes(bleAddress)
            if (macBytes != null && data.size >= 8) {
                val payloadMac = byteArrayOf(data[5], data[6], data[7])
                val deviceMac = byteArrayOf(macBytes[3], macBytes[4], macBytes[5])
                if (!payloadMac.contentEquals(deviceMac)) {
                    return null
                }
            }

            val sensorType = data[0].toInt() and 0x7F

            // Validate sensor type
            val validTypes = setOf(
                BleConstants.SensorType.STANDARD_BOTTOM_UP,
                BleConstants.SensorType.TOP_DOWN_AIR_ABOVE,
                BleConstants.SensorType.BOTTOM_UP_WATER,
                BleConstants.SensorType.LIPPERT_BOTTOM_UP,
                BleConstants.SensorType.PLUS_BOTTOM_UP,
                BleConstants.SensorType.PRO_UNIVERSAL
            )
            if (sensorType !in validTypes) {
                Log.d(TAG, "NRF52: Unknown sensor type 0x%02X".format(sensorType))
                return null
            }

            // Battery voltage: byte 1 lower 7 bits, divide by 32 for volts
            val batteryRaw = data[1].toInt() and 0x7F
            val batteryVoltage = batteryRaw / 32.0f

            // Temperature: byte 2 lower 7 bits, offset -40
            // Bit 7 of byte 2 = syncPressed (physical button on sensor)
            val byte2raw = data[2].toInt() and 0xFF
            val syncPressed = (byte2raw and 0x80) != 0
            val tempRaw = byte2raw and 0x7F
            val temperatureCelsius = (tempRaw - 40).toFloat()

            // Raw distance: 14-bit value from bytes 3-4
            val byte3 = data[3].toInt() and 0xFF
            val byte4 = data[4].toInt() and 0xFF
            val rawLevel = byte3 or ((byte4 and 0x3F) shl 8)

            // Quality: upper 2 bits of byte 4
            val quality = (byte4 shr 6) and 0x03

            // Convert raw level to depth in mm using temperature coefficients
            val coeffs = BleConstants.PROPANE_COEFFICIENTS
            val depthMm = rawLevel * (coeffs[0] + coeffs[1] * temperatureCelsius + coeffs[2] * temperatureCelsius * temperatureCelsius)
            val heightMeters = depthMm / 1000.0

            val mopekaSensorType = MopekaSensorType.fromNrf52TypeByte(sensorType)

            Log.d(TAG, "NRF52 OK: type=0x%02X (${mopekaSensorType.displayName}), ".format(sensorType) +
                    "battery=${"%.2f".format(batteryVoltage)}V, temp=${temperatureCelsius}°C, " +
                    "rawLevel=$rawLevel, quality=$quality, depthMm=${"%.1f".format(depthMm)}, " +
                    "syncPressed=$syncPressed")

            ParsedSensor(
                reading = SensorReading(
                    levelPercent = 0f,
                    rawHeightMeters = heightMeters,
                    batteryVoltage = batteryVoltage,
                    rssi = rssi,
                    quality = quality,
                    temperatureCelsius = temperatureCelsius,
                    firmwareVersion = ""
                ),
                sensorType = mopekaSensorType,
                syncPressed = syncPressed
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing NRF52 data", e)
            null
        }
    }

    /**
     * Try to parse manufacturer data from either hardware format.
     * Tries CC2540 first (21/23 byte payload), then NRF52 (10 byte payload).
     */
    fun parse(data: ByteArray, rssi: Int, bleAddress: String): ParsedSensor? {
        return parseCC2540(data, rssi, bleAddress) ?: parseNRF52(data, rssi, bleAddress)
    }

    /**
     * Extract fluid height from CC2540 Gen2 measurement samples.
     * Samples are packed in bytes starting at [startIdx] up to end-3 (MAC bytes).
     * Uses speed-of-sound temperature compensation.
     *
     * Each Gen2 sample is approx 2 bytes encoding time-of-flight in microseconds.
     * Height = (time_us * speed_of_sound) / 2 (divide by 2 for round trip)
     */
    private fun extractHeightFromSamples(
        data: ByteArray,
        startIdx: Int,
        temperatureCelsius: Float
    ): Double {
        // Speed of sound in propane gas, temperature-compensated
        val temp = temperatureCelsius.toDouble()
        val speedOfSound = 331.411 + 0.607 * temp - 0.00058865 * temp * temp // m/s

        val endIdx = data.size - 3 // Last 3 bytes are MAC
        if (startIdx >= endIdx) return 0.0

        // Extract raw 16-bit samples (little-endian pairs)
        val samples = mutableListOf<Double>()
        var i = startIdx
        while (i + 1 < endIdx) {
            val lo = data[i].toInt() and 0xFF
            val hi = data[i + 1].toInt() and 0xFF
            val rawTimeUs = lo or (hi shl 8)
            if (rawTimeUs > 0) {
                // Convert time-of-flight (microseconds) to distance (meters)
                // Divide by 2 for round trip, multiply by 1e-6 to convert us to seconds
                val distanceMeters = (rawTimeUs * 1e-6 * speedOfSound) / 2.0
                samples.add(distanceMeters)
            }
            i += 2
        }

        // Return median sample height (most robust against outliers)
        if (samples.isEmpty()) return 0.0
        val sorted = samples.sorted()
        return sorted[sorted.size / 2]
    }

    /**
     * Parse a BLE MAC address string "AA:BB:CC:DD:EE:FF" into 6 bytes.
     */
    private fun parseMacBytes(address: String): ByteArray? {
        return try {
            val parts = address.split(":")
            if (parts.size != 6) return null
            ByteArray(6) { i -> parts[i].toInt(16).toByte() }
        } catch (_: Exception) {
            null
        }
    }

    private fun getReadableByteArray(data: ByteArray):String {
        return data.joinToString(
            prefix = "[",
            postfix = "]"
        ) { (it.toInt() and 0xFF).toString() }
    }
}
