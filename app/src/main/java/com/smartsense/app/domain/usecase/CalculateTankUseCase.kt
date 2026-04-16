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

        // Per SRS 9.1.3.2: LPG level (%) = (LPG liquid level / Tank height) * 100%
        // For horizontal tanks, we use the polynomial to convert height ratio to volume ratio.
        val percent = when (tankType) {
            TankType.PROPANE_HORIZONTAL -> {
                val norm = (rawHeightMeters / tankHeightMeters).coerceIn(0.0, 1.0)
                100.0 * (-1.16533 * norm.pow(3) + 1.7615 * norm.pow(2) + 0.40923 * norm)
            }
            else -> {
                (rawHeightMeters / tankHeightMeters) * 100.0
            }
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