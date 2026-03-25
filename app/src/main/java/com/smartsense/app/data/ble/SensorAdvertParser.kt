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

            val sensorType = data[0].toInt() and 0x7F

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
            val rawLevel = byte3 or ((byte4 and 0x3F) shl 8)

            val quality = (byte4 shr 6) and 0x03

            val coeffs = BleConstants.PROPANE_COEFFICIENTS
            val depthMm = rawLevel * (coeffs[0] + coeffs[1] * temperatureCelsius + coeffs[2] * temperatureCelsius * temperatureCelsius)
            val heightMeters = depthMm / 1000.0

            val mopekaSensorType = MopekaSensorType.fromNrf52TypeByte(sensorType)

            Log.d(TAG, "NRF52 OK $bleAddress: type=${mopekaSensorType.displayName}, " +
                    "battery=${"%.2f".format(batteryVoltage)}V, temp=${temperatureCelsius}°C, " +
                    "rawLevel=$rawLevel, quality=$quality, depthMm=${"%.1f".format(depthMm)}")

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

    private fun extractHeightFromSamples(
        data: ByteArray,
        startIdx: Int,
        temperatureCelsius: Float
    ): Double {
        val temp = temperatureCelsius.toDouble()
        val speedOfSound = 331.411 + 0.607 * temp - 0.00058865 * temp * temp

        val endIdx = data.size - 3
        if (startIdx >= endIdx) return 0.0

        val samples = mutableListOf<Double>()
        var i = startIdx
        while (i + 1 < endIdx) {
            val lo = data[i].toInt() and 0xFF
            val hi = data[i + 1].toInt() and 0xFF
            val rawTimeUs = lo or (hi shl 8)
            if (rawTimeUs > 0) {
                val distanceMeters = (rawTimeUs * 1e-6 * speedOfSound) / 2.0
                samples.add(distanceMeters)
            }
            i += 2
        }

        if (samples.isEmpty()) return 0.0
        val sorted = samples.sorted()
        return sorted[sorted.size / 2]
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
