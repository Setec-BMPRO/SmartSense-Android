package com.smartsense.app.data.repository

import android.util.Log
import com.smartsense.app.data.ble.BleManager
import com.smartsense.app.data.ble.ScannedSensor
import com.smartsense.app.domain.model.MopekaSensorType
import com.smartsense.app.domain.model.NotificationFrequency

import com.smartsense.app.data.local.dao.SensorDao
import com.smartsense.app.data.local.entity.SensorEntity
import com.smartsense.app.data.local.entity.TankEntity
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.di.ApplicationScope
import com.smartsense.app.domain.model.QualityThreshold
import com.smartsense.app.domain.model.ReadQuality
import com.smartsense.app.domain.model.Sensor1
import com.smartsense.app.domain.model.Tank
import com.smartsense.app.domain.model.TankLevel
import com.smartsense.app.domain.model.TankLevelUnit
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankPreset
import com.smartsense.app.domain.model.TankRegion
import com.smartsense.app.domain.model.TankType
import com.smartsense.app.domain.model.TriggerAlarmUnit
import com.smartsense.app.domain.usecase.CalculateTankUseCase
import com.smartsense.app.util.uppercaseFirst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Sensor1Repository @Inject constructor(
    private val bleManager: BleManager,
    private val sensorDao: SensorDao,
    private val calculateTankUseCase: CalculateTankUseCase
) {
    companion object {
        private const val TAG = "Sensor1Repository"
    }
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
            .sample(scanIntervalMillis)
            .map {
                Log.i(TAG,"discoverSensors")
                mapToSensorList(liveReadings.value)
            }
            .onCompletion { _isScanning.value = false }
            .distinctUntilChanged()
    }

//    fun startScanIfNeeded() {
//        bleManager.startScan()
//            .onStart { _isScanning.value = true }
//            .onEach(::cacheReading)
//            .onCompletion { _isScanning.value = false }
//            .launchIn(externalScope)
//    }

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

    fun observeRegisteredSensors(scanIntervalMillis: Long): Flow<List<Sensor1>> {
        // 1. Create the ticker based on your interval
        val ticker = flow {
            while (currentCoroutineContext().isActive) {
                emit(Unit)
                delay(scanIntervalMillis)
            }
        }
        return ticker.flatMapLatest {
            combine(
                sensorDao.observeRegisteredAddresses().take(1),
                liveReadings.take(1),
                sensorDao.observeAllTanks().take(1)
            ) { addresses, readings, tanks ->
                val tankMap = tanks.associateBy { it.sensorAddress }
                addresses.mapNotNull { address ->
                    val scanned = readings[address] ?: return@mapNotNull null
                    val tank = tankMap[address]?.toDomain()
                    Log.i(TAG,"observeRegisteredSensors")
                    mapToSensor(
                        scanned = scanned,
                        tank = tank,
                        mapToSensorEnum = MapToSensorEnum.OBSERVE_REGISTERED
                    )
                }.sortedBy { it.name }
            }.distinctUntilChanged()
        }
    }

    fun observeSensorForDetail(address: String,scanIntervalMillis: Long): Flow<Sensor1?> {
        // 1. Create the ticker based on your interval
        val ticker = flow {
            while (currentCoroutineContext().isActive) {
                emit(Unit)
                delay(scanIntervalMillis)
            }
        }
        return ticker.flatMapLatest {
            combine(
                liveReadings.take(1),
                sensorDao.observeTank(address).take(1),
            ) { readings, tankEntity ->

                val scanned = readings[address] ?: return@combine null
                val tank = tankEntity?.toDomain()
                Log.i(TAG,"observeSensorForDetail-tankEntity:${tankEntity}-tank:${tank}")
                mapToSensor(
                    scanned, tank,
                    mapToSensorEnum = MapToSensorEnum.OBSERVE_DETAIL
                )
            }
        }
    }

    // --------------------------------------
    // 🧠 MAPPING
    // --------------------------------------

    private fun mapToSensorList(readings: Map<String, ScannedSensor>): List<Sensor1> {
        return readings.values.map { scanned ->
            mapToSensor(
                scanned = scanned,
                mapToSensorEnum = MapToSensorEnum.DISCOVER
            )
        }.sortedByDescending { it.reading?.timestampMillis }
    }

    private fun mapToSensor(
        scanned: ScannedSensor,
        tank: Tank?=null,
        mapToSensorEnum:MapToSensorEnum
    ): Sensor1 {
        val reading = scanned.parsed.reading
        // 1. Extract calculations to scoped variables to avoid repetition
        val tankLevel = when (mapToSensorEnum) {
            MapToSensorEnum.OBSERVE_REGISTERED, MapToSensorEnum.OBSERVE_DETAIL -> {
                calculateTankUseCase.calculateTankLevel(
                    rawHeightMeters = reading.rawHeightMeters,
                    tankHeightMm = calculateTankUseCase.calculateTankHeightMm(tank),
                    tankType = calculateTankUseCase.calculateTankType(tank)
                )
            }
            MapToSensorEnum.DISCOVER -> null
        }
        var readQuality: ReadQuality?=null
        var tankType: String?=null

        if (mapToSensorEnum == MapToSensorEnum.OBSERVE_DETAIL) {
            readQuality=reading.quality.toQuality()
            tankType= if(tank?.type== TankType.ARBITRARY)
                tank.type.displayName+" "+tank.type.orientation.name.uppercaseFirst()
            else tank?.type?.displayName
        }

        // 2. Return using named arguments
        return Sensor1(
            address = scanned.address,
            name = calculateName(scanned, tank),
            advertisedName = scanned.name,
            sensorType = scanned.parsed.sensorType,
            syncPressed = scanned.parsed.syncPressed,
            reading = reading,
            tankLevel = tankLevel,
            readQuality = readQuality,
            tankType=tankType
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
        sensorDao.insertTank(
            TankEntity(sensorAddress = address)
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
        type = enumOrDefault(tankType, TankType.default()),
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

    enum class MapToSensorEnum{
        DISCOVER,OBSERVE_REGISTERED,OBSERVE_DETAIL
    }
}