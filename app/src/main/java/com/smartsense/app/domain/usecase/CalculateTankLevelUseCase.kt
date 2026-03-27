package com.smartsense.app.domain.usecase

import android.util.Log
import com.smartsense.app.domain.model.Tank
import com.smartsense.app.domain.model.TankLevel
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankPreset.TankType
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
        tankHeightMm: Float,
        tankType: TankType
    ): TankLevel {

        Log.d("TankCalc", "---- START ----")
        Log.d("TankCalc", "rawHeightMeters = $rawHeightMeters")
        Log.d("TankCalc", "tankHeightMm = $tankHeightMm")
        Log.d("TankCalc", "tankType = $tankType")

        val tankHeightMeters = tankHeightMm / 1000.0
        Log.d("TankCalc", "tankHeightMeters = $tankHeightMeters")

        if (tankHeightMeters <= 0) {
            Log.d("TankCalc", "Invalid tank height → return 0")
            return TankLevel(0f, 0f)
        }

        val effectiveHeight = tankHeightMeters * SCALE_FACTOR
        Log.d("TankCalc", "effectiveHeight = $effectiveHeight")

        if (rawHeightMeters < MIN_OFFSET_METERS) {
            Log.d("TankCalc", "Below MIN_OFFSET_METERS ($MIN_OFFSET_METERS) → return 0")
            return TankLevel(0f, 0f)
        }

        val percent = when (tankType) {

            TankType.PROPANE_VERTICAL, TankType.CUSTOM -> {
                Log.d("TankCalc", "Mode = VERTICAL/CUSTOM")

                if (MIN_OFFSET_METERS >= effectiveHeight) {
                    Log.d("TankCalc", "MIN_OFFSET >= effectiveHeight → 100%")
                    100.0
                } else {
                    val value = 100.0 * (rawHeightMeters - MIN_OFFSET_METERS) /
                            (effectiveHeight - MIN_OFFSET_METERS)

                    Log.d("TankCalc", "Vertical formula result = $value")
                    value
                }
            }

            TankType.PROPANE_HORIZONTAL -> {
                Log.d("TankCalc", "Mode = HORIZONTAL")

                val diameter = effectiveHeight
                Log.d("TankCalc", "diameter = $diameter")

                when {
                    rawHeightMeters >= diameter -> {
                        Log.d("TankCalc", "rawHeight >= diameter → 100%")
                        100.0
                    }

                    rawHeightMeters <= 0 -> {
                        Log.d("TankCalc", "rawHeight <= 0 → 0%")
                        0.0
                    }

                    else -> {
                        val norm = rawHeightMeters / diameter
                        Log.d("TankCalc", "normalized height = $norm")

                        val p = -1.16533 * norm.pow(3) +
                                1.7615 * norm.pow(2) +
                                0.40923 * norm

                        Log.d("TankCalc", "polynomial p = $p")

                        val result = 100.0 * p
                        Log.d("TankCalc", "Horizontal result = $result")

                        result
                    }
                }
            }
        }

        Log.d("TankCalc", "percent (raw) = $percent")

        val clampedPercent = max(0f, min(100f, percent.toFloat()))
        Log.d("TankCalc", "clampedPercent = $clampedPercent")

        val heightMm = (rawHeightMeters * 1000.0).toFloat()
        Log.d("TankCalc", "heightMm = $heightMm")

        Log.d("TankCalc", "---- END ----")

        return TankLevel(clampedPercent, heightMm)
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
