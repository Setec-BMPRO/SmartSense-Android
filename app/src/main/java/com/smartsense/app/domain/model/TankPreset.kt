package com.smartsense.app.domain.model

data class TankPreset(
    val id: String,
    val name: String,
    val heightMm: Float,
    val type: TankType
) {
    companion object {
        val defaults = listOf(
            TankPreset("20lb", "20 lb (BBQ)", 254f, TankType.PROPANE_VERTICAL),
            TankPreset("30lb", "30 lb", 381f, TankType.PROPANE_VERTICAL),
            TankPreset("40lb", "40 lb", 508f, TankType.PROPANE_VERTICAL),
            TankPreset("100lb", "100 lb", 812.8f, TankType.PROPANE_VERTICAL),
            TankPreset("120galv", "120 gal, Vertical", 975.4f, TankType.PROPANE_VERTICAL),
            TankPreset("120galh", "120 gal, Horizontal", 609.6f, TankType.PROPANE_HORIZONTAL),
            TankPreset("150gal", "150 gal, Horizontal", 609.6f, TankType.PROPANE_HORIZONTAL),
            TankPreset("250gal", "250 gal, Horizontal", 762f, TankType.PROPANE_HORIZONTAL),
            TankPreset("500gal", "500 gal, Horizontal", 939.8f, TankType.PROPANE_HORIZONTAL),
            TankPreset("1000gal", "1000 gal, Horizontal", 1041.4f, TankType.PROPANE_HORIZONTAL),
            TankPreset("custom", "Custom", 0f, TankType.CUSTOM),
        )

        fun findById(id: String): TankPreset? = defaults.find { it.id == id }
    }
    enum class TankType(val drawableRes: Int) {
        PROPANE_VERTICAL(com.smartsense.app.R.drawable.ic_tank_vertical),
        PROPANE_HORIZONTAL(com.smartsense.app.R.drawable.ic_tank_horizontal),
        CUSTOM(com.smartsense.app.R.drawable.ic_tank_custom)
    }

}

