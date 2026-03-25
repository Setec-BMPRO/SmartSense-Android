package com.smartsense.app.domain.usecase

import com.smartsense.app.domain.model.Tank
import com.smartsense.app.domain.model.TankOrientation
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class CalculateTankLevelUseCase @Inject constructor() {

    companion object {
        private const val MIN_OFFSET_METERS = 0.0381  // Sensor dead zone at tank bottom
        private const val SCALE_FACTOR = 0.78          // Tanks filled to ~80% capacity
        const val MAX_PERCENTAGE = 100F
    }

    /**
     * Calculates tank fill level as a percentage from measured fluid height.
     *
     * From decompiled source:
     * - Vertical: percent = 100 * (height - MIN_OFFSET) / (effectiveHeight - MIN_OFFSET)
     * - Horizontal: polynomial approximation for cylindrical cross-section
     * - effectiveHeight = tankHeight * SCALE_FACTOR
     */
    fun calculate(
        rawHeightMeters: Double,
        tank: Tank,
        propaneRatio: Double = 1.0,
        cAdjustment: Double = 1.0
    ): Float {
        val tankHeight = tank.effectiveHeightMeters
        if (tankHeight <= 0) return 0f

        val adjustedHeight = rawHeightMeters * cAdjustment * propaneRatio
        val effectiveHeight = tankHeight * SCALE_FACTOR

        // Below sensor dead zone = empty
        if (adjustedHeight < MIN_OFFSET_METERS) return 0f

        val percent = when (tank.effectiveOrientation) {
            TankOrientation.VERTICAL -> {
                // Linear: percent = (height - minOffset) / (effectiveHeight - minOffset) * 100
                if (MIN_OFFSET_METERS >= effectiveHeight) 100.0
                else 100.0 * (adjustedHeight - MIN_OFFSET_METERS) / (effectiveHeight - MIN_OFFSET_METERS)
            }
            TankOrientation.HORIZONTAL -> {
                // Polynomial approximation for cylindrical cross-section
                val diameter = effectiveHeight
                if (adjustedHeight >= diameter) 100.0
                else if (adjustedHeight <= 0) 0.0
                else {
                    val norm = adjustedHeight / diameter
                    val p = -1.16533 * norm.pow(3) + 1.7615 * norm.pow(2) + 0.40923 * norm
                    100.0 * p
                }
            }
        }

        return max(0f, min(100f, percent.toFloat()))
    }

     fun calculateRoundedGasTankLevel(rawTankLevel: Int): Int =
         when {
             rawTankLevel < 0 -> {
                 0
             }
             rawTankLevel > MAX_PERCENTAGE -> {
                 MAX_PERCENTAGE.toInt()
             }
             else -> {
                 ((rawTankLevel + 5) / 10) * 10
             }
         }

}
