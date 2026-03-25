package com.smartsense.app.data.ble

/**
 * Mopeka sensor hardware families and types.
 */
enum class MopekaSensorType(val displayName: String, val isLpg: Boolean) {
    CC2540_STD("Std", isLpg = true),
    CC2540_XL("XL", isLpg = true),
    PRO("Pro", isLpg = true),
    PRO_PLUS("Pro+", isLpg = true),
    TOP_DOWN_AIR("Pro", isLpg = false),
    BOTTOM_UP_WATER("Pro", isLpg = false),
    UNKNOWN("Unknown", isLpg = false);

    companion object {
        fun fromCC2540DeviceByte(deviceByte: Int): MopekaSensorType {
            return if ((deviceByte and 0x01) == 1) CC2540_XL else CC2540_STD
        }

        fun fromNrf52TypeByte(typeByte: Int): MopekaSensorType {
            val hwVersion = typeByte or 0x100
            return when {
                hwVersion in setOf(264, 265, 266, 267) -> PRO_PLUS
                typeByte == 0x04 -> TOP_DOWN_AIR
                typeByte == 0x05 -> BOTTOM_UP_WATER
                else -> PRO
            }
        }
    }
}
