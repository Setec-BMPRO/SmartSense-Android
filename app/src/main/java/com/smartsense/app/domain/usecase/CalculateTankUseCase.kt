package com.smartsense.app.domain.usecase

import com.smartsense.app.data.ble.ScannedSensor
import com.smartsense.app.domain.model.MopekaSensorType
import com.smartsense.app.domain.model.Tank
import com.smartsense.app.domain.model.TankLevel
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankPreset.TankType
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.pow
import kotlin.run

class CalculateTankUseCase @Inject constructor() {

    companion object {
        private const val TAG = "CalculateTankUseCase"
    }

    fun calculateTankLevel(
        rawHeightMeters: Double,
        tankHeightMm: Float,
        tankType: TankType
    ): TankLevel {

        logStart(rawHeightMeters, tankHeightMm, tankType)

        val tankHeightMeters = tankHeightMm / 1000.0
        Timber.tag(TAG).d("tankHeightMeters = $tankHeightMeters")

        if (tankHeightMeters <= 0) {
            Timber.tag(TAG).d("Invalid tank height → return 0")
            return TankLevel(0f, 0f)
        }

        val heightMm = (rawHeightMeters * 1000.0).toFloat()

        if (rawHeightMeters <= 0) {
            Timber.tag(TAG).d("rawHeight <= 0 → 0%")
            return TankLevel(0f, heightMm)
        }

        // SRS 9.1.3.2: level% = level_mm / tank_height_mm * 100
        val percent = when (tankType) {
            TankType.PROPANE_VERTICAL,
            TankType.CUSTOM -> calculateVerticalPercent(
                rawHeightMeters,
                tankHeightMeters
            )

            TankType.PROPANE_HORIZONTAL -> calculateHorizontalPercent(
                rawHeightMeters,
                tankHeightMeters
            )
        }

        Timber.tag(TAG).d("percent (raw) = $percent")

        val clampedPercent = percent.toFloat().coerceIn(0f, 100f)
        Timber.tag(TAG).d("clampedPercent = $clampedPercent, heightMm = $heightMm")
        Timber.tag(TAG).d("---- END ----")

        return TankLevel(clampedPercent, heightMm)
    }

    // --------------------------------------
    // 📐 CALCULATIONS
    // --------------------------------------

    private fun calculateVerticalPercent(
        rawHeightMeters: Double,
        tankHeightMeters: Double
    ): Double {
        Timber.tag(TAG).d("Mode = VERTICAL/CUSTOM")
        // SRS 9.1.3.2: level% = level_mm / tank_height_mm * 100
        val value = 100.0 * rawHeightMeters / tankHeightMeters
        Timber.tag(TAG).d("Vertical formula result = $value")
        return value
    }

    private fun calculateHorizontalPercent(
        rawHeightMeters: Double,
        tankHeightMeters: Double
    ): Double {
        Timber.tag(TAG).d("Mode = HORIZONTAL, diameter = $tankHeightMeters")

        return when {
            rawHeightMeters >= tankHeightMeters -> 100.0
            rawHeightMeters <= 0 -> 0.0
            else -> {
                val norm = rawHeightMeters / tankHeightMeters
                val p = -1.16533 * norm.pow(3) +
                        1.7615 * norm.pow(2) +
                        0.40923 * norm
                Timber.tag(TAG).d("Horizontal: norm=$norm, p=$p")
                100.0 * p
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
        Timber.tag(TAG).d("---- START ----")
        Timber.tag(TAG).d("rawHeightMeters = $rawHeightMeters")
        Timber.tag(TAG).d("tankHeightMm = $tankHeightMm")
        Timber.tag(TAG).d("tankType = $tankType")
    }

    // --------------------------------------
    // 🔄 MAPPERS
    // --------------------------------------

    fun calculateTankHeightMm(tank: Tank?): Float {
        val heightMm = when (val type = tank?.type) {
            com.smartsense.app.domain.model.TankType.ARBITRARY -> {
                // Convert custom height (m) to mm
                tank.customHeightMeters.toFloat() * 1000f
            }
            else -> {
                // Convert predefined type height (m) to mm
                (type?.heightMeters?.toFloat() ?: com.smartsense.app.domain.model.TankType.default().heightMeters.toFloat()) * 1000f
            }
        }
        Timber.d("Final calculated height: ${heightMm}mm")
        return heightMm
    }

    fun calculateTankType(tank: Tank?) =
        when (
            tank?.let {
                if (it.type == com.smartsense.app.domain.model.TankType.ARBITRARY)
                    it.orientation
                else
                    it.type.orientation
            } ?: com.smartsense.app.domain.model.TankType.default().orientation
        ) {
            TankOrientation.VERTICAL -> TankType.PROPANE_VERTICAL
            else -> TankType.PROPANE_HORIZONTAL
        }

     fun calculateName(sensorType:MopekaSensorType?=null,tankName: String?=null): String =
         tankName?.takeIf { it.isNotBlank() } ?: run {
            when {
                sensorType == MopekaSensorType.SETEC_GAS -> "Setec LPG Device"
                (sensorType ==null) or (sensorType?.isLpg!=false) -> "New LPG Device"
                sensorType == MopekaSensorType.BOTTOM_UP_WATER -> "New water sensor"
                else -> "New ${sensorType!!.displayName} Device"
            }
        }
}