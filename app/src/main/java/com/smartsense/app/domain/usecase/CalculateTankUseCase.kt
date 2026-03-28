package com.smartsense.app.domain.usecase

import android.util.Log
import com.smartsense.app.domain.model.Tank
import com.smartsense.app.domain.model.TankLevel
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankPreset.TankType
import javax.inject.Inject
import kotlin.math.pow
class CalculateTankUseCase @Inject constructor() {

    companion object {
        private const val MIN_OFFSET_METERS = 0.0381
        private const val SCALE_FACTOR = 0.78
    }

    fun calculateTankLevel(
        rawHeightMeters: Double,
        tankHeightMm: Float,
        tankType: TankType
    ): TankLevel {

        logStart(rawHeightMeters, tankHeightMm, tankType)

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
            TankType.PROPANE_VERTICAL,
            TankType.CUSTOM -> calculateVerticalPercent(
                rawHeightMeters,
                effectiveHeight
            )

            TankType.PROPANE_HORIZONTAL -> calculateHorizontalPercent(
                rawHeightMeters,
                effectiveHeight
            )
        }

        Log.d("TankCalc", "percent (raw) = $percent")

        val clampedPercent = percent.toFloat().coerceIn(0f, 100f)
        Log.d("TankCalc", "clampedPercent = $clampedPercent")

        val heightMm = (rawHeightMeters * 1000.0).toFloat()
        Log.d("TankCalc", "heightMm = $heightMm")

        Log.d("TankCalc", "---- END ----")

        return TankLevel(clampedPercent, heightMm)
    }

    // --------------------------------------
    // 📐 CALCULATIONS
    // --------------------------------------

    private fun calculateVerticalPercent(
        rawHeightMeters: Double,
        effectiveHeight: Double
    ): Double {

        Log.d("TankCalc", "Mode = VERTICAL/CUSTOM")

        return if (MIN_OFFSET_METERS >= effectiveHeight) {
            Log.d("TankCalc", "MIN_OFFSET >= effectiveHeight → 100%")
            100.0
        } else {
            val value = 100.0 * (rawHeightMeters - MIN_OFFSET_METERS) /
                    (effectiveHeight - MIN_OFFSET_METERS)

            Log.d("TankCalc", "Vertical formula result = $value")
            value
        }
    }

    private fun calculateHorizontalPercent(
        rawHeightMeters: Double,
        effectiveHeight: Double
    ): Double {

        Log.d("TankCalc", "Mode = HORIZONTAL")

        Log.d("TankCalc", "diameter = $effectiveHeight")

        return when {
            rawHeightMeters >= effectiveHeight -> {
                Log.d("TankCalc", "rawHeight >= diameter → 100%")
                100.0
            }

            rawHeightMeters <= 0 -> {
                Log.d("TankCalc", "rawHeight <= 0 → 0%")
                0.0
            }

            else -> {
                val norm = rawHeightMeters / effectiveHeight
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

    // --------------------------------------
    // 🧾 HELPERS
    // --------------------------------------

    private fun logStart(
        rawHeightMeters: Double,
        tankHeightMm: Float,
        tankType: TankType
    ) {
        Log.d("TankCalc", "---- START ----")
        Log.d("TankCalc", "rawHeightMeters = $rawHeightMeters")
        Log.d("TankCalc", "tankHeightMm = $tankHeightMm")
        Log.d("TankCalc", "tankType = $tankType")
    }

    // --------------------------------------
    // 🔄 MAPPERS
    // --------------------------------------

    fun calculateTankHeightMm(tank: Tank?) =
        when (val type = tank?.type) {
            com.smartsense.app.domain.model.TankType.ARBITRARY ->
                tank.customHeightMeters.toFloat()

            else ->
                type?.heightMeters?.toFloat()
        } ?: com.smartsense.app.domain.model.TankType.KG_3_7.heightMeters.toFloat()

    fun calculateTankType(tank: Tank?) =
        when (
            tank?.let {
                if (it.type == com.smartsense.app.domain.model.TankType.ARBITRARY)
                    it.orientation
                else
                    it.type.orientation
            } ?: com.smartsense.app.domain.model.TankType.KG_3_7.orientation
        ) {
            TankOrientation.VERTICAL -> TankType.PROPANE_VERTICAL
            else -> TankType.PROPANE_HORIZONTAL
        }
}