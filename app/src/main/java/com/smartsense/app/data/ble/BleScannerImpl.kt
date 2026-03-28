package com.smartsense.app.data.ble

import android.util.Log
import com.smartsense.app.domain.model.MopekaSensorType
import com.smartsense.app.domain.model.ReadQuality
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.TankPreset
import com.smartsense.app.domain.usecase.CalculateTankUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleScannerImpl @Inject constructor(
    private val bleManager: BleManager,
    private val calculateTankLevel: CalculateTankUseCase
) : BleScanner {

    companion object {
        private const val TAG = "BleScannerImpl"
    }

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** Sensors that have been paired via sync button press */
    private val pairedSensors = MutableStateFlow<Map<String, Sensor>>(emptyMap())

    /** Addresses of sensors paired via sync button */
    private val pairedAddresses = mutableSetOf<String>()

    private val presetOverrides = mutableMapOf<String, TankPreset>()

    override fun getInitialSensors(): List<Sensor> = emptyList()

    override fun startScan(): Flow<List<Sensor>> {
        return bleManager.startScan()
            .onStart { _isScanning.value = true }
            .onEach { scanned -> handleScannedSensor(scanned) }
            .map { pairedSensors.value.values.toList().sortedByDescending { it.lastUpdated } }
            .onCompletion { _isScanning.value = false }
    }

    override fun stopScan() {
        bleManager.stopScan()
        _isScanning.value = false
    }

    fun setPresetOverride(address: String, preset: TankPreset) {
        presetOverrides[address] = preset
    }

    override fun removePairedSensor(address: String) {
        pairedAddresses.remove(address)
        val current = pairedSensors.value.toMutableMap()
        current.remove(address)
        pairedSensors.value = current
        presetOverrides.remove(address)
    }

    private fun handleScannedSensor(scanned: ScannedSensor) {
        val address = scanned.address

        // Auto-pair when sync button is pressed on the physical sensor
        if (scanned.parsed.syncPressed && address !in pairedAddresses) {
            pairedAddresses.add(address)
            Log.d(TAG, "Sync button detected — auto-pairing sensor $address")
        }

        // Only update sensors that have been paired
        if (address in pairedAddresses) {
            updateSensor(scanned)
        }
    }

    private fun updateSensor(scanned: ScannedSensor) {
        val parsed = scanned.parsed
        val address = scanned.address

        val preset = presetOverrides[address]
            ?: pairedSensors.value[address]?.tankPreset
            ?: TankPreset.defaults.first()

        val level = calculateTankLevel.calculateTankLevel(
            rawHeightMeters = parsed.reading.rawHeightMeters,
            tankHeightMm = preset.heightMm,
            tankType = preset.type
        )

        // CR2032 battery: decompiled formula (voltage - 2.2) / 0.65 * 100
        val batteryPercent = ((parsed.reading.batteryVoltage - 2.2f) / 0.65f * 100f)
            .coerceIn(0f, 100f).toInt()

        val readQuality = when (parsed.reading.quality) {
            3 -> ReadQuality.GOOD
            2 -> ReadQuality.FAIR
            else -> ReadQuality.POOR
        }

        val defaultName = if (parsed.sensorType.isLpg) {
            "New LPG Device"
        } else if (parsed.sensorType == MopekaSensorType.BOTTOM_UP_WATER) {
            "New water sensor"
        } else {
            "New ${parsed.sensorType.displayName} Device"
        }
        val name = scanned.name
            ?: pairedSensors.value[address]?.name
            ?: defaultName

        val sensor = Sensor(
            address = address,
            name = name,
            tankPreset = preset,
            level = level,
            batteryPercent = batteryPercent,
            rssi = parsed.reading.rssi,
            temperatureCelsius = parsed.reading.temperatureCelsius,
            readQuality = readQuality,
            lastUpdated = System.currentTimeMillis(),
            isPaired = true
        )

        val current = pairedSensors.value.toMutableMap()
        current[address] = sensor
        pairedSensors.value = current
    }
}
