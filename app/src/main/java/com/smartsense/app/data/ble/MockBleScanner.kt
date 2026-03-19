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

        // Discovery phase: add sensors one by one
        while (_isScanning.value && remaining.isNotEmpty()) {
            val delayMs = Random.nextLong(3000, 6001)
            delay(delayMs)
            if (!_isScanning.value) break

            discovered.add(remaining.removeFirst())
            emit(discovered.map { it.toSensor(tick) })
            tick++
        }

        // Live update phase: continuously update sensor states
        while (_isScanning.value) {
            val delayMs = Random.nextLong(2000, 4001)
            delay(delayMs)
            if (!_isScanning.value) break

            emit(discovered.map { it.toSensor(tick) })
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
            val levelDrift = (Random.nextFloat() - 0.5f) * 6f
            val currentLevel = (baseLevel + levelDrift - tick * 0.1f).coerceIn(0f, 100f)
            val heightMm = preset.heightMm * currentLevel / 100f
            val tempDrift = (Random.nextFloat() - 0.5f) * 2f
            val rssiDrift = Random.nextInt(-8, 9)
            val currentRssi = (baseRssi + rssiDrift).coerceIn(-100, -20)
            val batteryDrift = Random.nextInt(-2, 1)

            return Sensor(
                address = address,
                name = name,
                tankPreset = preset,
                level = TankLevel(
                    percentage = currentLevel,
                    heightMm = heightMm
                ),
                batteryPercent = (baseBattery + batteryDrift).coerceIn(0, 100),
                rssi = currentRssi,
                temperatureCelsius = baseTemp + tempDrift,
                readQuality = when {
                    currentRssi >= -60 -> ReadQuality.GOOD
                    currentRssi >= -80 -> ReadQuality.FAIR
                    else -> ReadQuality.POOR
                },
                lastUpdated = System.currentTimeMillis(),
                isPaired = false
            )
        }
    }
}
