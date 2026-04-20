package com.smartsense.app.data.ble

import android.util.Log
import com.smartsense.app.domain.model.MopekaSensorType
import com.smartsense.app.domain.model.SensorReading

/**
 * Result of parsing a Mopeka advertisement, bundling reading + sensor type.
 */
data class ParsedSensor(
    val reading: SensorReading,
    val sensorType: MopekaSensorType,
    val syncPressed: Boolean = false,
    val rawData: ByteArray? = null
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

    private const val CC2540_PAYLOAD_SHORT = 20
    private const val CC2540_PAYLOAD_LONG = 23
    private const val NRF52_PAYLOAD = 10

    /** CC2540 firmware samples at 12µs intervals; distance = index * 2, so each unit = 6µs */
    private const val CC2540_TIME_BASE_US = 6

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
            val temperatureCelsius = if (tempRaw == 0) 25.0f
            else (1.776964f * (tempRaw - 25))

            val heightMeters = extractHeightFromSamples(data, 4, temperatureCelsius)


            Log.d(TAG, "CC2540 VALID $bleAddress: battery=${"%.2f".format(batteryVoltage)}V, " +
                    "temp=${"%.1f".format(temperatureCelsius)}°C, quality=$quality, " +
                    "height=${"%.4f".format(heightMeters)}m, sync=$syncPressed")

            ParsedSensor(
                reading = SensorReading(
                    rawHeightMeters = heightMeters,
                    batteryVoltage = batteryVoltage,
                    rssi = rssi,
                    quality = quality,
                    temperatureCelsius = temperatureCelsius,
                    firmwareVersion = "",
                    deviceMAC = macBytes.let {
                        byteArrayOf(it[3], it[4], it[5])
                            .joinToString(":") { byte -> "%02X".format(byte) }
                    }

                ),
                sensorType = MopekaSensorType.fromCC2540DeviceByte(byte1),
                syncPressed = syncPressed,
                rawData = data
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
            val temperatureCelsius = if (tempRaw == 0) 25.0f else (tempRaw - 40).toFloat()

            val byte3 = data[3].toInt() and 0xFF
            val byte4 = data[4].toInt() and 0xFF
            val rawLevel = byte3 or ((byte4 and 0x3F) shl 8)

            val quality = (byte4 shr 6) and 0x03

            // Convert raw level to depth in mm per SRS 9.1.3.2 (M1001 sensor)
            val t = temperatureCelsius.toDouble()
            val speed = 0.0004 * t * t * t - 0.0224 * t * t - 6.1989 * t + 940.04
            // Distance travelled (mm) = (rawLevel units * 20us / 1000) * (speed * 0.7174)
            // LPG liquid level (mm) = Distance travelled (mm) / 2
            val depthMm = (rawLevel * 20.0 / 1000.0) * (speed * 0.7174) / 2.0
            val heightMeters = depthMm / 1000.0

            val mopekaSensorType = MopekaSensorType.fromNrf52TypeByte(sensorType)

            Log.d(TAG, "NRF52 OK $bleAddress: type=${mopekaSensorType.displayName}, " +
                    "battery=${"%.2f".format(batteryVoltage)}V, temp=${temperatureCelsius}°C, " +
                    "rawLevel=$rawLevel, quality=$quality, height=${"%.4f".format(heightMeters)}m, sync=$syncPressed")

            ParsedSensor(
                reading = SensorReading(

                    rawHeightMeters = heightMeters,
                    batteryVoltage = batteryVoltage,
                    rssi = rssi,
                    quality = quality,
                    temperatureCelsius = temperatureCelsius,
                    firmwareVersion = ""
                ),
                sensorType = mopekaSensorType,
                syncPressed = syncPressed,
                rawData = data
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

        Log.d(TAG, "CC2540 samples: hwVer=$hwVersion, entries=${adv.size}, " +
                "best=(amp=${best.amplitude}, dist=$rawLevel), " +
                "all=${adv.joinToString { "(a=${it.amplitude},d=${it.distance})" }}")

        // CC2540 distance units represent 6µs of round-trip time-of-flight each
        // (firmware timer resolution = 12µs per sample index, distance = index * 2)
        val timeUs = rawLevel.toDouble() * CC2540_TIME_BASE_US
        val t = temperatureCelsius.toDouble()
        // Speed formula per point 1
        val speed = 0.0004 * t * t * t - 0.0224 * t * t - 6.1989 * t + 940.04
        // Apply factor 0.7174 from SRS 9.1.3.2 (u71.74)
        val depthMeters = (timeUs / 1_000_000.0) * (speed * 0.7174) / 2.0
        return depthMeters
    }

    /**
     * Parse Setec (0x051F) next-gen BLE advertisement.
     *
     * Payload layout (after Android strips the 2-byte manufacturer ID):
     *  Byte  0:  0xFF — Setec data type (3rd party sensor)
     *  Byte  1:  Bits[0-6] = 3rd-party company ID, Bit 7 = sync/pairing flag
     *  Byte  2:  Protocol version (major 7-4, minor 3-0)
     *  Byte  3:  Software version (major 7-4, minor 3-0)
     *  Bytes 4-9:  Repeated sensor MAC address (6 bytes)
     *  Byte 10:  Battery voltage raw (V = raw * 0.01 + 1.22)
     *  Byte 11:  Reserved (0x00)
     *  Byte 12:  Sensor type (6 = gas sensor)
     *  Bytes 13-14: Gas tank height in mm (big-endian)
     *  Byte 15:  Data serial number
     *  Byte 16:  Reporting interval (seconds)
     */
    fun parseSetec(data: ByteArray, rssi: Int, bleAddress: String): ParsedSensor? {
        if (data.size < BleConstants.SETEC_PAYLOAD_SIZE) return null

        return try {
            val dataType = data[0].toInt() and 0xFF
            if (dataType != BleConstants.SETEC_DATA_TYPE_3RD_PARTY) return null

            val byte1 = data[1].toInt() and 0xFF
            val companyId = byte1 and 0x7F
            val syncPressed = (byte1 and 0x80) != 0

            // MAC validation — bytes 4-9 must match BLE address
            val macBytes = parseMacBytes(bleAddress) ?: return null
            val payloadMac = ByteArray(6) { data[4 + it] }
            if (!payloadMac.contentEquals(macBytes)) return null

            val protocolVersion = data[2].toInt() and 0xFF
            val softwareVersion = data[3].toInt() and 0xFF
            val protoMajor = (protocolVersion shr 4) and 0x0F
            val protoMinor = protocolVersion and 0x0F
            val swMajor = (softwareVersion shr 4) and 0x0F
            val swMinor = softwareVersion and 0x0F

            val batteryRaw = data[10].toInt() and 0xFF
            val batteryVoltage = batteryRaw * 0.01f + 1.22f

            val sensorType = data[12].toInt() and 0xFF

            // Tank height reported directly in mm (bytes 13-14, big-endian)
            val heightMm = ((data[13].toInt() and 0xFF) shl 8) or (data[14].toInt() and 0xFF)
            val heightMeters = heightMm / 1000.0

            val mopekaSensorType = when (sensorType) {
                BleConstants.SensorType.GAS_SENSOR -> MopekaSensorType.SETEC_GAS
                else -> MopekaSensorType.UNKNOWN
            }

            Log.d(TAG, "SETEC OK $bleAddress: company=$companyId, type=${mopekaSensorType.displayName}, " +
                    "battery=${"%.2f".format(batteryVoltage)}V, " +
                    "height=${heightMm}mm, sync=$syncPressed, " +
                    "proto=$protoMajor.$protoMinor, sw=$swMajor.$swMinor")

            ParsedSensor(
                reading = SensorReading(
                    rawHeightMeters = heightMeters,
                    batteryVoltage = batteryVoltage,
                    rssi = rssi,
                    quality = 3, // No quality field in Setec protocol; default to good
                    temperatureCelsius = 0f, // No temperature field in Setec protocol
                    firmwareVersion = "$swMajor.$swMinor",
                    deviceMAC = macBytes.joinToString(":") { byte -> "%02X".format(byte) }
                ),
                sensorType = mopekaSensorType,
                syncPressed = syncPressed,
                rawData = data
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Setec data", e)
            null
        }
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
