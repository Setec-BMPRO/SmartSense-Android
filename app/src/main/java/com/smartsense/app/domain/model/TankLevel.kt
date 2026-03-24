package com.smartsense.app.domain.model

data class TankLevel(
    val percentage: Float,
    val heightMm: Float=0F
) {
    val status: LevelStatus
        get() = when {
            percentage > 25f -> LevelStatus.GREEN
            percentage > 10f -> LevelStatus.YELLOW
            else -> LevelStatus.RED
        }
}

enum class LevelStatus {
    GREEN,
    YELLOW,
    RED
}
