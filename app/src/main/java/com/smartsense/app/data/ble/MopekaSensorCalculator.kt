package com.smartsense.app.data.ble

object MopekaSensorCalculator {

    private const val TANK_MIN_OFFSET_UM: Long = 38100L // 38.1mm baseline offset
    private const val TOF_STEP: Long = 20L
    private const val M1001_TANK_LEVEL_PRE_SEED: Int = 0x35

    /* Speed of sound in propane relative to arbitrary Mopeka temp indexes */
    private val PROPANE_SPEED_TABLE = intArrayOf(
        777, 777, 777, 1124, 1119, 1114, 1108, 1102, 1096, 1089, 1082,
        1074, 1066, 1058, 1050, 1041, 1032, 1022, 1013, 1003, 993,
        983, 972, 962, 951, 940, 929, 918, 906, 895, 883, 872,
        860, 849, 837, 825, 813, 802, 790, 778, 766, 755, 743,
        732, 721, 709, 698, 687, 677, 666, 656, 645, 635, 625,
        616, 607, 597, 589, 580, 572, 564, 557, 549, 542
    )

    /**
     * Complete Wrapper: Give it the 23-byte array (Bluetooth Manufacturer Data Block
     * without the header) and the user-configured tank height, and it gives you the final %.
     */
    fun calculatePercentageFromPayload(manufacturerData: ByteArray, tankHeightMm: Int): Int {
        if (manufacturerData.size < 23) {
            return 0 // Safety check against malformed BLE payload
        }

        // Byte 3 represents "Misc", which contains the temp index on its lower 6 bits
        val miscByte = manufacturerData[3].toInt() and 0xFF
        val mopekaTemperature = miscByte and 0x3F

        // Bytes 4 through 22 (19 bytes total) contain the obfuscated level data
        val obfuscationPayload = manufacturerData.copyOfRange(4, 23)
        val tankLevelRaw = deobfuscateTankLevel(obfuscationPayload)

        return calculatePercent(tankLevelRaw, tankHeightMm, mopekaTemperature)
    }

    /**
     * Unmasks the hardware time-of-flight distance using the Mopeka XOR sequence constraint.
     * @param Payload requires the exact 19-bytes designated for XOR (bytes 8-26 of full AD packet).
     */
    fun deobfuscateTankLevel(obfuscationPayload: ByteArray): Int {
        var tankLevel = M1001_TANK_LEVEL_PRE_SEED

        for (byte in obfuscationPayload) {
            // Apply bitwise XOR with unsigned conversion
            tankLevel = tankLevel xor (byte.toInt() and 0xFF)
        }

        return tankLevel
    }

    /**
     * Safely retrieves environmental speed of sound in limits.
     */
    fun getSpeedOfSound(temperatureIndex: Int): Long {
        return if (temperatureIndex >= PROPANE_SPEED_TABLE.size) {
            PROPANE_SPEED_TABLE.last().toLong()
        } else {
            PROPANE_SPEED_TABLE[temperatureIndex].toLong()
        }
    }

    /**
     * Takes the exact integer logic from the C firmware and maps distances to percentage.
     */
    fun calculatePercent(tankLevelRaw: Int, tankHeightMm: Int, mopekaTemperature: Int): Int {
        var tankHeightUm = tankHeightMm.toLong() * 1000L
        if (tankHeightUm <= TANK_MIN_OFFSET_UM) {
            return 100
        }

        val speedOfSound = getSpeedOfSound(mopekaTemperature)

        // Calculate raw geometric Time of Flight hardware distance
        var distanceToBoundaryUm = (speedOfSound * tankLevelRaw.toLong() * TOF_STEP) / 2L

        // Sub-zero / Dead-zone detection
        if (distanceToBoundaryUm <= TANK_MIN_OFFSET_UM) {
            return 0
        }

        // Subtract the hardware housing's minimum offset
        distanceToBoundaryUm -= TANK_MIN_OFFSET_UM
        tankHeightUm -= TANK_MIN_OFFSET_UM

        // Emulate C's round-division logic rather than allowing Kotlin to automatically truncate early
        val roundingCorrection = tankHeightUm / 2L
        var percent = ((100L * distanceToBoundaryUm) + roundingCorrection) / tankHeightUm

        if (percent > 100L) {
            percent = 100L
        }

        return percent.toInt()
    }
}
