package com.smartsense.app.data.ble

import android.util.Log

/**
 * Result of parsing a Mopeka BLE advertisement.
 */
data class ParsedSensor(
    val rawHeightMeters: Double,
    val batteryVoltage: Float,
    val rssi: Int,
    val quality: Int,
    val temperatureCelsius: Float,
    val sensorType: MopekaSensorType,
    val syncPressed: Boolean = false
)

/**
 * Parses BLE advertisement manufacturer-specific data from Mopeka sensors.
 *
 * Supports two hardware formats:
 * - CC2540 (20/23 byte payload after manufacturer ID stripped)
 * - NRF52 (10 byte payload after manufacturer ID stripped)
 */
object SensorAdvertParser {

    private const val TAG = "SensorAdvertParser"

    private const val CC2540_PAYLOAD_SHORT = 20
    private const val CC2540_PAYLOAD_LONG = 23
    private const val NRF52_PAYLOAD = 10

    fun parse(data: ByteArray, rssi: Int, bleAddress: String): ParsedSensor? {
        return parseCC2540(data, rssi, bleAddress) ?: parseNRF52(data, rssi, bleAddress)
    }

    fun parseCC2540(data: ByteArray, rssi: Int, bleAddress: String): ParsedSensor? {
        if (data.size != CC2540_PAYLOAD_SHORT && data.size != CC2540_PAYLOAD_LONG) return null

        return try {
            val byte0 = data[0].toInt() and 0xFF
            if (byte0 == 0xBB || byte0 == 0xAA) return null

            val byte1 = data[1].toInt() and 0xFF
            val maskedByte1 = byte1 and 0xCF
            if (maskedByte1 !in BleConstants.BMPRO_ACCEPTED_DEVICE_BYTES) return null

            // MAC validation — mandatory: last 3 bytes of payload must match last 3 of BLE address
            val macBytes = parseMacBytes(bleAddress) ?: return null
            val payloadMac = byteArrayOf(
                data[data.size - 3],
                data[data.size - 2],
                data[data.size - 1]
            )
            val deviceMac = byteArrayOf(macBytes[3], macBytes[4], macBytes[5])
            if (!payloadMac.contentEquals(deviceMac)) return null

            val quality = (byte1 shr 4) and 0x03

            val batteryRaw = data[2].toInt() and 0xFF
            val batteryVoltage = (batteryRaw / 256.0f) * 2.0f + 1.5f

            val byte3raw = data[3].toInt() and 0xFF
            val syncPressed = (byte3raw and 0x80) != 0
            val tempRaw = byte3raw and 0x3F
            val temperatureCelsius = if (tempRaw == 0) -40.0f
            else (1.776964f * (tempRaw - 25))

            val heightMeters = extractHeightFromSamples(data, 4, temperatureCelsius)

            Log.d(TAG, "CC2540 VALID $bleAddress: battery=${"%.2f".format(batteryVoltage)}V, " +
                    "temp=${"%.1f".format(temperatureCelsius)}°C, quality=$quality, " +
                    "height=${"%.4f".format(heightMeters)}m")

            ParsedSensor(
                rawHeightMeters = heightMeters,
                batteryVoltage = batteryVoltage,
                rssi = rssi,
                quality = quality,
                temperatureCelsius = temperatureCelsius,
                sensorType = MopekaSensorType.fromCC2540DeviceByte(byte1),
                syncPressed = syncPressed
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing CC2540 data", e)
            null
        }
    }

    fun parseNRF52(data: ByteArray, rssi: Int, bleAddress: String): ParsedSensor? {
        if (data.size != NRF52_PAYLOAD) return null

        return try {
            // MAC validation — mandatory: bytes 5-7 must match last 3 of BLE address
            val macBytes = parseMacBytes(bleAddress) ?: return null
            val payloadMac = byteArrayOf(data[5], data[6], data[7])
            val deviceMac = byteArrayOf(macBytes[3], macBytes[4], macBytes[5])
            if (!payloadMac.contentEquals(deviceMac)) return null

            val byte0raw = data[0].toInt() and 0xFF
            val sensorType = byte0raw and 0x7F
            val extendedRange = (byte0raw and 0x80) != 0

            val validTypes = setOf(
                BleConstants.SensorType.STANDARD_BOTTOM_UP,
                BleConstants.SensorType.TOP_DOWN_AIR_ABOVE,
                BleConstants.SensorType.BOTTOM_UP_WATER,
                BleConstants.SensorType.LIPPERT_BOTTOM_UP,
                BleConstants.SensorType.PLUS_BOTTOM_UP,
                BleConstants.SensorType.PRO_UNIVERSAL
            )
            if (sensorType !in validTypes) return null

            val batteryRaw = data[1].toInt() and 0x7F
            val batteryVoltage = batteryRaw / 32.0f

            val byte2raw = data[2].toInt() and 0xFF
            val syncPressed = (byte2raw and 0x80) != 0
            val tempRaw = byte2raw and 0x7F
            val temperatureCelsius = (tempRaw - 40).toFloat()

            val byte3 = data[3].toInt() and 0xFF
            val byte4 = data[4].toInt() and 0xFF
            var rawLevel = byte3 or ((byte4 and 0x3F) shl 8)

            // Extended range: bit 7 of hw byte scales up the raw level
            if (extendedRange) {
                rawLevel = 16384 + (rawLevel shl 2)
            }

            val quality = (byte4 shr 6) and 0x03

            // Match decompiled: level = 1e-6 * rawLevel (time-of-flight in µs)
            // Then apply temperature-compensated propane speed of sound / 2
            val levelSeconds = rawLevel * 1e-6
            val coeffs = BleConstants.PROPANE_COEFFICIENTS
            val speedOfSound = (coeffs[0] + coeffs[1] * temperatureCelsius + coeffs[2] * temperatureCelsius * temperatureCelsius) * 2000.0
            val heightMeters = levelSeconds * speedOfSound / 2.0

            val mopekaSensorType = MopekaSensorType.fromNrf52TypeByte(sensorType)

            Log.d(TAG, "NRF52 OK $bleAddress: type=${mopekaSensorType.displayName}, " +
                    "battery=${"%.2f".format(batteryVoltage)}V, temp=${temperatureCelsius}°C, " +
                    "rawLevel=$rawLevel, quality=$quality, height=${"%.4f".format(heightMeters)}m")

            ParsedSensor(
                rawHeightMeters = heightMeters,
                batteryVoltage = batteryVoltage,
                rssi = rssi,
                quality = quality,
                temperatureCelsius = temperatureCelsius,
                sensorType = mopekaSensorType,
                syncPressed = syncPressed
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing NRF52 data", e)
            null
        }
    }

    /**
     * Extract fluid height from CC2540 advertisement data.
     *
     * Matches decompiled logic: data[1] (hwVersion byte) determines encoding.
     * - hwVersion 0 or 1: simple 2-byte pairs (amplitude, intensity)
     * - Other: 10-bit packed variable-length encoding
     *
     * The adv[] entries contain {amplitude, intensity} where intensity = 2 * distance_index.
     * The highest-amplitude entry's distance is used as the raw level.
     */
    private fun extractHeightFromSamples(
        data: ByteArray,
        startIdx: Int,
        temperatureCelsius: Float
    ): Double {
        val endIdx = data.size - 3 // Last 3 bytes are MAC
        if (startIdx >= endIdx) return 0.0

        val hwVersion = data[1].toInt() and 0xFF
        val isXl = (hwVersion and 0x01) == 1

        data class AdvEntry(val amplitude: Int, val distance: Int)
        val adv = mutableListOf<AdvEntry>()

        if (hwVersion == 0 || hwVersion == 1) {
            // Simple encoding: 8 entries of 2-byte pairs
            for (n in 0 until 8) {
                val idx = startIdx + 2 * n
                if (idx + 1 >= endIdx) break
                val a = data[idx].toInt() and 0xFF
                var i = data[idx + 1].toInt() and 0xFF
                if (isXl) i *= 2
                if (a > 0) adv.add(AdvEntry(a, i * 2))
            }
        } else {
            // 10-bit packed variable-length encoding
            var entryCount = 0
            var cumDist = 0
            for (o in 0 until 12) {
                val bitOffset = 10 * o
                val byteOffset = bitOffset / 8
                val bitShift = bitOffset % 8
                val absIdx = startIdx + byteOffset
                if (absIdx + 1 >= endIdx) break

                val lo = data[absIdx].toInt() and 0xFF
                val hi = data[absIdx + 1].toInt() and 0xFF
                var s = lo + 256 * hi
                s = s shr bitShift

                val u = 1 + (s and 0x1F) // 5-bit delta distance
                s = s shr 5
                val amplitude = s and 0x1F // 5-bit amplitude

                cumDist += u
                if (cumDist > 255) break

                if (amplitude > 0) {
                    val adjAmplitude = (amplitude - 1) * 4 + 6
                    adv.add(AdvEntry(adjAmplitude, cumDist * 2))
                    entryCount++
                }
            }
        }

        if (adv.isEmpty()) return 0.0

        // Use the entry with the highest amplitude as the best reading
        val best = adv.maxByOrNull { it.amplitude } ?: return 0.0
        val rawLevel = best.distance

        // Convert using propane speed of sound (same as NRF52)
        val levelSeconds = rawLevel * 1e-6
        val coeffs = BleConstants.PROPANE_COEFFICIENTS
        val temp = temperatureCelsius.toDouble()
        val speedOfSound = (coeffs[0] + coeffs[1] * temp + coeffs[2] * temp * temp) * 2000.0
        return levelSeconds * speedOfSound / 2.0
    }

    private fun parseMacBytes(address: String): ByteArray? {
        return try {
            val parts = address.split(":")
            if (parts.size != 6) return null
            ByteArray(6) { i -> parts[i].toInt(16).toByte() }
        } catch (_: Exception) {
            null
        }
    }
}
