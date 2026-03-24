package com.smartsense.app.domain.model

/**
 * Mopeka sensor hardware families and types.
 *
 * From decompiled source:
 * - CC2540 byte[3] bit 0: 0=gen2 ("Std"), 1=xl ("XL")
 * - NRF52 byte[2] & 0x7F | 0x100: determines Pro vs Pro+
 *
 * Display mapping (Tank Info screen):
 * - hwFamily "gen2" → "Std"
 * - hwFamily "xl"   → "XL"
 * - hwFamily "pro"  → "Pro"
 * - hwFamily "pro+" → "Pro+"
 */
enum class MopekaSensorType(val displayName: String, val isLpg: Boolean) {
    // CC2540 types (determined by byte[3] bit 0)
    CC2540_STD("Std", isLpg = true),    // gen2: bit 0 = 0
    CC2540_XL("XL", isLpg = true),      // xl: bit 0 = 1

    // NRF52 Pro types
    PRO("Pro", isLpg = true),
    PRO_PLUS("Pro+", isLpg = true),

    // NRF52 non-LPG types
    TOP_DOWN_AIR("Pro", isLpg = false),
    BOTTOM_UP_WATER("Pro", isLpg = false),

    UNKNOWN("Unknown", isLpg = false);

    companion object {
        /**
         * Determine CC2540 sensor type from device flags byte (payload[1]).
         * Bit 0: 0 = gen2 ("Std"), 1 = xl ("XL")
         */
        fun fromCC2540DeviceByte(deviceByte: Int): MopekaSensorType {
            return if ((deviceByte and 0x01) == 1) CC2540_XL else CC2540_STD
        }

        /**
         * Map NRF52 sensor type byte to enum.
         * hwVersionNumber = (byte[2] & 0x7F) | 0x100
         * Pro+ versions: 264, 265, 266, 267
         */
        fun fromNrf52TypeByte(typeByte: Int): MopekaSensorType {
            val hwVersion = typeByte or 0x100
            return when {
                hwVersion in setOf(264, 265, 266, 267) -> PRO_PLUS
                typeByte == 0x04 -> TOP_DOWN_AIR
                typeByte == 0x05 -> BOTTOM_UP_WATER
                else -> PRO  // 0x03, 0x06, 0x0C etc.
            }
        }

        /**
         * Check if NRF52 type is LPG-related.
         */
        fun isNrf52Lpg(typeByte: Int): Boolean {
            return typeByte != 0x04 && typeByte != 0x05  // Not air/water
        }
    }
}
