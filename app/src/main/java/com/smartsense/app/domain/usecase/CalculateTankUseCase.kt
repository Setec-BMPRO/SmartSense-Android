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
        Timber.tag(TAG).d("tankHeightMeters = $tankHeightMeters")

        if (tankHeightMeters <= 0) {
            Timber.tag(TAG).d("Invalid tank height → return 0")
            return TankLevel(0f, 0f)
        }

        val effectiveHeight = tankHeightMeters * SCALE_FACTOR
        Timber.tag(TAG).d("effectiveHeight = $effectiveHeight")

        if (rawHeightMeters < MIN_OFFSET_METERS) {
            Timber.tag(TAG).d("Below MIN_OFFSET_METERS ($MIN_OFFSET_METERS) → return 0")
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

        Timber.tag(TAG).d("percent (raw) = $percent")

        val clampedPercent = percent.toFloat().coerceIn(0f, 100f)
        Timber.tag(TAG).d("clampedPercent = $clampedPercent")

        val heightMm = (rawHeightMeters * 1000.0).toFloat()
        Timber.tag(TAG).d("heightMm = $heightMm")
        Timber.tag(TAG).d("---- END ----")

        return TankLevel(clampedPercent, heightMm)
    }

    // --------------------------------------
    // 📐 CALCULATIONS
    // --------------------------------------

    private fun calculateVerticalPercent(
        rawHeightMeters: Double,
        effectiveHeight: Double
    ): Double {
        Timber.tag(TAG).d("Mode = VERTICAL/CUSTOM")

        return if (MIN_OFFSET_METERS >= effectiveHeight) {
            Timber.tag(TAG).d("MIN_OFFSET >= effectiveHeight → 100%")
            100.0
        } else {
            val value = 100.0 * (rawHeightMeters - MIN_OFFSET_METERS) /
                    (effectiveHeight - MIN_OFFSET_METERS)

            Timber.tag(TAG).d("Vertical formula result = $value")
            value
        }
    }

    private fun calculateHorizontalPercent(
        rawHeightMeters: Double,
        effectiveHeight: Double
    ): Double {
        Timber.tag(TAG).d("Mode = HORIZONTAL")
        Timber.tag(TAG).d("diameter = $effectiveHeight")

        return when {
            rawHeightMeters >= effectiveHeight -> {
                Timber.tag(TAG).d("rawHeight >= diameter → 100%")
                100.0
            }

            rawHeightMeters <= 0 -> {
                Timber.tag(TAG).d("rawHeight <= 0 → 0%")
                0.0
            }

            else -> {
                val norm = rawHeightMeters / effectiveHeight
                Timber.tag(TAG).d("normalized height = $norm")

                val p = -1.16533 * norm.pow(3) +
                        1.7615 * norm.pow(2) +
                        0.40923 * norm

                Timber.tag(TAG).d("polynomial p = $p")

                val result = 100.0 * p
                Timber.tag(TAG).d("Horizontal result = $result")

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