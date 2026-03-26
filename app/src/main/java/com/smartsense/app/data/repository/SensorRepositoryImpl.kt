package com.smartsense.app.data.repository

import com.smartsense.app.data.ble.BleScanner
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.TankPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorRepositoryImpl @Inject constructor(
    private val bleScanner: BleScanner
) : SensorRepository {

    private val presetOverrides = MutableStateFlow<Map<String, TankPreset>>(emptyMap())
    private val removedAddresses = MutableStateFlow<Set<String>>(emptySet())
    private val latestScanData = MutableStateFlow(bleScanner.getInitialSensors())
    private val _sensorCount = MutableStateFlow(0)

    init {
        _sensorCount.value = latestScanData.value.size
    }

    override val isScanning: StateFlow<Boolean> = bleScanner.isScanning
    override val sensorCount: StateFlow<Int> = _sensorCount.asStateFlow()

    override fun getSensors(): Flow<List<Sensor>> {
        return combine(latestScanData, presetOverrides, removedAddresses) { sensors, overrides, removed ->
            sensors.filter { it.address !in removed }
                .map { sensor ->
                    val preset = overrides[sensor.address] ?: sensor.tankPreset
                    sensor.copy(tankPreset = preset, isPaired = true)
                }
        }
    }

    override fun getSensorByAddress(address: String): Flow<Sensor?> {
        return getSensors().map { sensors ->
            sensors.find { it.address == address }
        }
    }

    override suspend fun updateTankPreset(address: String, preset: TankPreset) {
        val current = presetOverrides.value.toMutableMap()
        current[address] = preset
        presetOverrides.value = current
    }

    override suspend fun removeSensor(address: String) {
        bleScanner.removePairedSensor(address)
        val current = removedAddresses.value.toMutableSet()
        current.add(address)
        removedAddresses.value = current
    }

    override fun scanForSensors(): Flow<List<Sensor>> {
        return bleScanner.startScan().map { sensors ->
            latestScanData.value = sensors

            // If a sensor was removed but re-paired via sync button, un-remove it
            val removed = removedAddresses.value
            val rePaired = sensors.filter { it.address in removed }.map { it.address }.toSet()
            if (rePaired.isNotEmpty()) {
                removedAddresses.value = removed - rePaired
            }

            val visible = sensors.filter { it.address !in (removed - rePaired) }
            _sensorCount.value = visible.size
            visible
        }
    }

    override fun stopScan() {
        bleScanner.stopScan()
    }
}
