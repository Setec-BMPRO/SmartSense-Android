package com.smartsense.app.domain.usecase

import com.smartsense.app.domain.model.TankLevel
import com.smartsense.app.domain.model.TankType
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class CalculateTankLevelUseCase @Inject constructor() {

    companion object {
        private const val MIN_OFFSET_METERS = 0.0381  // Sensor dead zone at tank bottom
        private const val SCALE_FACTOR = 0.78          // Tanks filled to ~80% capacity
    }

    /**
     * Calculates tank fill level from measured fluid height.
     *
     * @param rawHeightMeters raw height measurement from BLE sensor (meters)
     * @param tankHeightMm tank height from preset (millimeters)
     * @param tankType determines vertical (linear) or horizontal (polynomial) calculation
     * @return TankLevel with percentage and height in mm
     */
    fun calculate(
        rawHeightMeters: Double,
        tankHeightMm: Float,
        tankType: TankType
    ): TankLevel {
        val tankHeightMeters = tankHeightMm / 1000.0
        if (tankHeightMeters <= 0) return TankLevel(0f, 0f)

        val effectiveHeight = tankHeightMeters * SCALE_FACTOR

        if (rawHeightMeters < MIN_OFFSET_METERS) return TankLevel(0f, 0f)

        val percent = when (tankType) {
            TankType.PROPANE_VERTICAL, TankType.CUSTOM -> {
                if (MIN_OFFSET_METERS >= effectiveHeight) 100.0
                else 100.0 * (rawHeightMeters - MIN_OFFSET_METERS) / (effectiveHeight - MIN_OFFSET_METERS)
            }
            TankType.PROPANE_HORIZONTAL -> {
                val diameter = effectiveHeight
                if (rawHeightMeters >= diameter) 100.0
                else if (rawHeightMeters <= 0) 0.0
                else {
                    val norm = rawHeightMeters / diameter
                    val p = -1.16533 * norm.pow(3) + 1.7615 * norm.pow(2) + 0.40923 * norm
                    100.0 * p
                }
            }
        }

        val clampedPercent = max(0f, min(100f, percent.toFloat()))
        val heightMm = (rawHeightMeters * 1000.0).toFloat()

        return TankLevel(clampedPercent, heightMm)
    }
}
