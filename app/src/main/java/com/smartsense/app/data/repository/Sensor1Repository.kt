package com.smartsense.app.data.repository

import com.smartsense.app.data.ble.BleManager
import com.smartsense.app.data.ble.ScannedSensor
import com.smartsense.app.domain.model.MopekaSensorType
import com.smartsense.app.domain.model.NotificationFrequency

import com.smartsense.app.data.local.dao.SensorDao
import com.smartsense.app.data.local.entity.SensorEntity
import com.smartsense.app.data.local.entity.TankEntity
import com.smartsense.app.di.ApplicationScope
import com.smartsense.app.domain.model.QualityThreshold
import com.smartsense.app.domain.model.ReadQuality
import com.smartsense.app.domain.model.Sensor1
import com.smartsense.app.domain.model.Tank
import com.smartsense.app.domain.model.TankLevelUnit
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankRegion
import com.smartsense.app.domain.model.TankType
import com.smartsense.app.domain.model.TriggerAlarmUnit
import com.smartsense.app.domain.usecase.CalculateTankUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Sensor1Repository @Inject constructor(
    private val bleManager: BleManager,
    private val sensorDao: SensorDao,
    private val calculateTankUseCase: CalculateTankUseCase,
    @param:ApplicationScope private val externalScope: CoroutineScope
) {

    private val liveReadings = MutableStateFlow<Map<String, ScannedSensor>>(emptyMap())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    // --------------------------------------
    // 🔍 SCANNING
    // --------------------------------------

    fun discoverSensors(scanIntervalMillis: Long): Flow<List<Sensor1>> {
        return bleManager.startScan()
            .onStart { _isScanning.value = true }
            .onEach(::cacheReading)
            .map { mapToSensorList(liveReadings.value) }
            .onCompletion { _isScanning.value = false }
    }

    fun startScanIfNeeded(scanIntervalMillis: Long) {
        bleManager.startScan()
            .onStart { _isScanning.value = true }
            .onEach(::cacheReading)
            .onCompletion { _isScanning.value = false }
            .launchIn(externalScope)
    }

    fun stopScan() {
        bleManager.stopScan()
        _isScanning.value = false
    }

    private fun cacheReading(scanned: ScannedSensor) {
        liveReadings.update { current ->
            current + (scanned.address to scanned)
        }
    }

    // --------------------------------------
    // 📡 OBSERVE REGISTERED SENSORS
    // --------------------------------------

    fun observeRegisteredSensors(): Flow<List<Sensor1>> {
        return combine(
            sensorDao.observeRegisteredAddresses(),
            liveReadings,
            sensorDao.observeAllTanks()
        ) { addresses, readings, tanks ->

            val tankMap = tanks.associateBy { it.sensorAddress }

            addresses.mapNotNull { address ->
                val scanned = readings[address] ?: return@mapNotNull null
                val tank = tankMap[address]?.toDomain()

                mapToSensor(scanned, tank)
            }.sortedBy { it.name }
        }
    }

    fun observeSensorForDetail(address: String): Flow<Sensor1?> {
        return combine(
            liveReadings,
            sensorDao.observeTank(address)
        ) { readings, tankEntity ->

            val scanned = readings[address] ?: return@combine null
            val tank = tankEntity?.toDomain()

            mapToSensor(scanned, tank, includeQuality = true)
        }
    }

    // --------------------------------------
    // 🧠 MAPPING
    // --------------------------------------

    private fun mapToSensorList(readings: Map<String, ScannedSensor>): List<Sensor1> {
        return readings.values.map { scanned ->
            mapToSensor(scanned, tank = null)
        }.sortedByDescending { it.reading?.timestampMillis }
    }

    private fun mapToSensor(
        scanned: ScannedSensor,
        tank: Tank?,
        includeQuality: Boolean = false
    ): Sensor1 {

        val reading = scanned.parsed.reading

        val tankLevel = calculateTankUseCase.calculateTankLevel(
            rawHeightMeters = reading.rawHeightMeters,
            tankHeightMm = calculateTankUseCase.calculateTankHeightMm(tank),
            tankType = calculateTankUseCase.calculateTankType(tank)
        )

        return Sensor1(
            address = scanned.address,
            name = calculateName(scanned, tank),
            advertisedName = scanned.name,
            sensorType = scanned.parsed.sensorType,
            syncPressed = scanned.parsed.syncPressed,
            reading = reading,
            tankLevel = tankLevel,
            readQuality = if (includeQuality) reading.quality.toQuality() else null
        )
    }

    private fun Int?.toQuality(): ReadQuality {
        return when (this) {
            3 -> ReadQuality.GOOD
            2 -> ReadQuality.FAIR
            else -> ReadQuality.POOR
        }
    }

    fun calculateName(scanned: ScannedSensor?, tank: Tank?): String {
        val defaultName = when {
            scanned?.parsed?.sensorType?.isLpg != false -> "New LPG Device"
            scanned.parsed.sensorType == MopekaSensorType.BOTTOM_UP_WATER -> "New water sensor"
            else -> "New ${scanned.parsed.sensorType.displayName} Device"
        }
        return tank?.name ?: defaultName
    }

    // --------------------------------------
    // 💾 DATABASE
    // --------------------------------------

    suspend fun registerSensor(address: String, name: String) {
        if (sensorDao.getSensor(address) != null) return

        sensorDao.insertSensor(
            SensorEntity(
                address = address,
                name = name.ifBlank { address },
                lastSeenMillis = System.currentTimeMillis(),
                isRegistered = true
            )
        )
    }

    suspend fun unregisterSensor(address: String) {
        sensorDao.deleteTank(address)
        sensorDao.deleteSensor(address)
    }

    suspend fun saveTankConfig(tank: Tank) {
        sensorDao.insertTank(tank.toEntity())
        sensorDao.insertSensor(
            SensorEntity(
                address = tank.sensorAddress,
                name = tank.name,
                isRegistered = true
            )
        )
    }

    suspend fun getTankConfig(sensorAddress: String): Tank? {
        return sensorDao.getTank(sensorAddress)?.toDomain()
    }

    val isBluetoothEnabled: Boolean
        get() = bleManager.isBluetoothEnabled

    // --------------------------------------
    // 🔄 MAPPERS
    // --------------------------------------

    private fun TankEntity.toDomain(): Tank = Tank(
        sensorAddress = sensorAddress,
        name = name,
        type = enumOrDefault(tankType, TankType.KG_3_7),
        customHeightMeters = customHeightMeters,
        orientation = enumOrDefault(orientation, TankOrientation.VERTICAL),
        alarmThresholdPercent = alarmThresholdPercent,
        region = enumOrDefault(region, TankRegion.UNITED_STATE),
        levelUnit = enumOrDefault(levelUnit, TankLevelUnit.PERCENT),
        notificationsEnabled = notificationsEnabled,
        notificationFrequency = enumOrDefault(notificationFrequency, NotificationFrequency.EVERY_12_HOURS),
        triggerAlarmUnit = enumOrDefault(triggerAlarmUnit, TriggerAlarmUnit.ABOVE),
        qualityThreshold = enumOrDefault(qualityThreshold, QualityThreshold.DISABLE)
    )

    private fun Tank.toEntity(): TankEntity = TankEntity(
        sensorAddress = sensorAddress,
        name = name,
        tankType = type.name,
        customHeightMeters = customHeightMeters,
        orientation = orientation.name,
        alarmThresholdPercent = alarmThresholdPercent,
        region = region.name,
        levelUnit = levelUnit.name,
        notificationsEnabled = notificationsEnabled,
        notificationFrequency = notificationFrequency.name,
        triggerAlarmUnit = triggerAlarmUnit.name,
        qualityThreshold = qualityThreshold.name
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T {
        return try {
            enumValueOf<T>(value)
        } catch (_: Exception) {
            default
        }
    }
}