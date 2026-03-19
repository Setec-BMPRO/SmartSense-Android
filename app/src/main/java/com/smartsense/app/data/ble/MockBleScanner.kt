package com.smartsense.app.data.ble

import com.smartsense.app.domain.model.ReadQuality
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.TankLevel
import com.smartsense.app.domain.model.TankPreset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class MockBleScanner @Inject constructor() : BleScanner {

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val mockSensors = listOf(
        MockSensorState(
            address = "AA:BB:CC:DD:EE:01",
            name = "Propane - BBQ",
            presetId = "20lb",
            baseLevel = 72f,
            baseTemp = 22.5f,
            baseRssi = -45,
            baseBattery = 95
        ),
        MockSensorState(
            address = "AA:BB:CC:DD:EE:02",
            name = "Propane - RV Main",
            presetId = "40lb",
            baseLevel = 35f,
            baseTemp = 19.3f,
            baseRssi = -62,
            baseBattery = 78
        ),
        MockSensorState(
            address = "AA:BB:CC:DD:EE:03",
            name = "Propane - RV Aux",
            presetId = "30lb",
            baseLevel = 8f,
            baseTemp = 20.1f,
            baseRssi = -75,
            baseBattery = 42
        ),
        MockSensorState(
            address = "AA:BB:CC:DD:EE:04",
            name = "Propane - House",
            presetId = "500gal",
            baseLevel = 55f,
            baseTemp = 15.8f,
            baseRssi = -88,
            baseBattery = 65
        ),
        MockSensorState(
            address = "AA:BB:CC:DD:EE:05",
            name = "Propane - Heater",
            presetId = "100lb",
            baseLevel = 91f,
            baseTemp = 24.2f,
            baseRssi = -53,
            baseBattery = 88
        )
    )

    override fun getInitialSensors(): List<Sensor> {
        return emptyList()
    }

    override fun startScan(): Flow<List<Sensor>> = flow {
        _isScanning.value = true
        var tick = 0
        val remaining = mockSensors.shuffled().toMutableList()
        val discovered = mutableListOf<MockSensorState>()

        while (_isScanning.value) {
            // Wait a random 5–10 seconds before discovering the next sensor
            val delayMs = Random.nextLong(5000, 10001)
            delay(delayMs)

            if (!_isScanning.value) break

            // Add next sensor if any remain
            if (remaining.isNotEmpty()) {
                discovered.add(remaining.removeFirst())
            }

            val sensors = discovered.map { it.toSensor(tick) }
            emit(sensors)
            tick++
        }
    }

    override fun stopScan() {
        _isScanning.value = false
    }

    private data class MockSensorState(
        val address: String,
        val name: String,
        val presetId: String,
        val baseLevel: Float,
        val baseTemp: Float,
        val baseRssi: Int,
        val baseBattery: Int
    ) {
        fun toSensor(tick: Int): Sensor {
            val preset = TankPreset.findById(presetId) ?: TankPreset.defaults.first()
            val levelDrift = (Random.nextFloat() - 0.5f) * 2f
            val currentLevel = (baseLevel + levelDrift - tick * 0.05f).coerceIn(0f, 100f)
            val heightMm = preset.heightMm * currentLevel / 100f
            val tempDrift = (Random.nextFloat() - 0.5f) * 0.5f
            val rssiDrift = Random.nextInt(-3, 4)

            return Sensor(
                address = address,
                name = name,
                tankPreset = preset,
                level = TankLevel(
                    percentage = currentLevel,
                    heightMm = heightMm
                ),
                batteryPercent = baseBattery,
                rssi = (baseRssi + rssiDrift).coerceIn(-100, -20),
                temperatureCelsius = baseTemp + tempDrift,
                readQuality = when {
                    baseRssi >= -60 -> ReadQuality.GOOD
                    baseRssi >= -80 -> ReadQuality.FAIR
                    else -> ReadQuality.POOR
                },
                lastUpdated = System.currentTimeMillis(),
                isPaired = false
            )
        }
    }
}
