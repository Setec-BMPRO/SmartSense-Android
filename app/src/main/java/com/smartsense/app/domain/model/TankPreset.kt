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
            TankPreset("30lb", "30 lb", 368f, TankType.PROPANE_VERTICAL),
            TankPreset("40lb", "40 lb", 457f, TankType.PROPANE_VERTICAL),
            TankPreset("100lb", "100 lb", 838f, TankType.PROPANE_VERTICAL),
            TankPreset("120gal", "120 Gallon", 1219f, TankType.PROPANE_HORIZONTAL),
            TankPreset("200gal", "200 Gallon", 610f, TankType.PROPANE_HORIZONTAL),
            TankPreset("500gal", "500 Gallon", 965f, TankType.PROPANE_HORIZONTAL),
            TankPreset("custom", "Custom", 0f, TankType.CUSTOM),
        )

        fun findById(id: String): TankPreset? = defaults.find { it.id == id }
    }
}

enum class TankType {
    PROPANE_VERTICAL,
    PROPANE_HORIZONTAL,
    CUSTOM
}
