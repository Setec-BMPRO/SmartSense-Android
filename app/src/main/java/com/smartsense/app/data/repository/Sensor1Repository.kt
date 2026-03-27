package com.smartsense.app.data.repository

import com.smartsense.app.data.ble.BleManager
import com.smartsense.app.data.ble.ScannedSensor
import com.smartsense.app.domain.model.MopekaSensorType
import com.smartsense.app.domain.model.NotificationFrequency

import com.smartsense.app.data.local.dao.SensorDao
import com.smartsense.app.data.local.entity.SensorEntity
import com.smartsense.app.data.local.entity.TankEntity
import com.smartsense.app.domain.model.ReadQuality
import com.smartsense.app.domain.model.Sensor1
import com.smartsense.app.domain.model.Tank
import com.smartsense.app.domain.model.TankLevelUnit
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankPreset
import com.smartsense.app.domain.model.TankRegion
import com.smartsense.app.domain.model.TankType
import com.smartsense.app.domain.model.TriggerAlarmUnit
import com.smartsense.app.domain.usecase.CalculateTankLevelUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Sensor1Repository @Inject constructor(
    private val bleManager: BleManager,
    private val sensorDao: SensorDao,
    val calculateLevel: CalculateTankLevelUseCase
) {


    // In-memory cache of latest BLE readings keyed by address
    private val liveReadings = MutableStateFlow<Map<String, ScannedSensor>>(emptyMap())

    /**
     * Start scanning and update in-memory BLE reading cache.
     * Returns a flow of ALL discovered sensors (for discovery dialog).
     */
    fun discoverSensors(): Flow<List<Sensor1>> {
        _isScanning.value=true
        return combine(
            bleManager.startScan().onEach { scanned ->
                liveReadings.value += (scanned.address to scanned)
            }.map { liveReadings.value },
            sensorDao.observeAllTanks()
        ) { readings, tanks ->
            val tankMap = tanks.associateBy { it.sensorAddress }
            readings.values.map { scanned ->
                _isScanning.value=false
                Sensor1(
                    address = scanned.address,
                    name = scanned.name?:"New LPG Device",
                    advertisedName = scanned.name,
                    sensorType = scanned.parsed.sensorType,
                    syncPressed = scanned.parsed.syncPressed,
                    reading = scanned.parsed.reading
                )
            }.sortedByDescending { it.reading?.timestampMillis }
        }
    }

    /**
     * Observe only registered sensors, combining stored config with live BLE data.
     * This is used for the main sensor list.
     */
    fun observeRegisteredSensors(): Flow<List<Sensor1>> {
        return combine(
            sensorDao.observeRegisteredAddresses(),
            liveReadings,
            sensorDao.observeAllTanks()
        ) { registeredAddresses, readings, tanks ->
            val tankMap = tanks.associateBy { it.sensorAddress }
            registeredAddresses.map { address ->
                val scanned = readings[address]
                val tank=tankMap[address]?.toDomain()
                val tankLevel = calculateLevel.calculate(
                    rawHeightMeters = scanned?.parsed?.reading?.rawHeightMeters?:0.0,
                    tankHeightMm = calculateTankHeightMm(tank),
                    tankType = calculateTankType(tank)
                )
                val name = calculateName(scanned,tank)
                Sensor1(
                    address = address,
                    name = name,
                    advertisedName = scanned?.name,
                    sensorType = scanned?.parsed?.sensorType ?: MopekaSensorType.UNKNOWN,
                    syncPressed = scanned?.parsed?.syncPressed ?: false,
                    reading = scanned?.parsed?.reading,
                    tankLevel = tankLevel
                )
            }.sortedBy { it.name }
        }
    }

    fun calculateName(scanned: ScannedSensor?,tank: Tank?): String {
        val defaultName=if (scanned?.parsed?.sensorType?.isLpg ?: true) {
            "New LPG Device"
        } else if (scanned.parsed.sensorType == MopekaSensorType.BOTTOM_UP_WATER) {
            "New water sensor"
        } else {
            "New ${scanned.parsed.sensorType?.displayName} Device"
        }
        return tank?.name ?: defaultName
    }

    fun calculateTankHeightMm(tank: Tank?) = when (val type = tank?.type) {
        TankType.ARBITRARY -> tank.customHeightMeters.toFloat()
        else -> type?.heightMeters?.toFloat()
    } ?: TankType.KG_3_7.heightMeters.toFloat()

    fun calculateTankType(tank: Tank?) = when (
        tank?.let {
            if (it.type == TankType.ARBITRARY) it.orientation else it.type.orientation
        } ?: TankType.KG_3_7.orientation
    ) {
        TankOrientation.VERTICAL -> TankPreset.TankType.PROPANE_VERTICAL
        else -> TankPreset.TankType.PROPANE_HORIZONTAL
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    /**
     * Register a sensor — mark it as registered so it appears in the main list.
     */
    suspend fun registerSensor(address: String, name: String) {
        if (sensorDao.getSensor(address) != null) {
            Timber.i("Sensor register failed with address ${address}")
            return
        }
        sensorDao.insertSensor(
            SensorEntity(
                address = address,
                name = name.ifBlank { address },
                lastSeenMillis = System.currentTimeMillis(),
                isRegistered = true
            )
        )
        Timber.i("Sensor registered successful with address ${address}")
    }

    /**
     * Unregister a sensor — remove from the main list and delete tank config.
     */
    suspend fun unregisterSensor(address: String) {
        sensorDao.deleteTank(address)
        sensorDao.deleteSensor(address)
    }

    /**
     * Observe a single sensor by address, combining live readings with stored config.
     */
    fun observeSensorForDetail(address: String): Flow<Sensor1?> {
        return combine(
            liveReadings,
            sensorDao.observeTank(address)
        ) { readings, tankEntity ->
            val scanned = readings[address] ?: return@combine null
            val tank = tankEntity?.toDomain()
            Timber.i("-----observeSensorForDetail-----")
            val tankLevel = calculateLevel.calculate(
                rawHeightMeters = scanned.parsed.reading.rawHeightMeters,
                tankHeightMm =calculateTankHeightMm(tank),
                tankType = calculateTankType(tank)
            )
            val readQuality = when (scanned.parsed.reading.quality) {
                3 -> ReadQuality.GOOD
                2 -> ReadQuality.FAIR
                else -> ReadQuality.POOR
            }
            val name = calculateName(scanned,tank)
            Sensor1(
                address = scanned.address,
                name = name,
                advertisedName = scanned.name,
                sensorType = scanned.parsed.sensorType,
                syncPressed = scanned.parsed.syncPressed,
                reading = scanned.parsed.reading,
                tankLevel=tankLevel,
                readQuality = readQuality
            )
        }
    }

    suspend fun saveTankConfig(tank: Tank) {
        sensorDao.insertTank(tank.toEntity())
        sensorDao.insertSensor(
            SensorEntity(address = tank.sensorAddress, name = tank.name, isRegistered = true)
        )
    }

    suspend fun getTankConfig(sensorAddress: String): Tank? {
        return sensorDao.getTank(sensorAddress)?.toDomain()
    }

    val isBluetoothEnabled: Boolean
        get() = bleManager.isBluetoothEnabled

//    val isBluetoothSupported: Boolean
//        get() = bleManager. isBluetoothSupported

    // Mapping extensions
    private fun TankEntity.toDomain(): Tank = Tank(
        sensorAddress = sensorAddress,
        name = name,
        type = try { TankType.valueOf(tankType) } catch (_: Exception) { TankType.KG_3_7 },
        customHeightMeters = customHeightMeters,
        orientation = try { TankOrientation.valueOf(orientation) } catch (_: Exception) { TankOrientation.VERTICAL },
        alarmThresholdPercent = alarmThresholdPercent,
        region = try { TankRegion.valueOf(region) } catch (_: Exception) { TankRegion.UNITED_STATE },
        levelUnit = try { TankLevelUnit.valueOf(levelUnit) } catch (_: Exception) { TankLevelUnit.PERCENT },
        notificationsEnabled = notificationsEnabled,
        notificationFrequency = try { NotificationFrequency.valueOf(notificationFrequency) } catch (_: Exception) { NotificationFrequency.EVERY_12_HOURS },
        triggerAlarmUnit = try { TriggerAlarmUnit.valueOf(triggerAlarmUnit) } catch (_: Exception) { TriggerAlarmUnit.ABOVE }
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
        triggerAlarmUnit = triggerAlarmUnit.name
    )

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

}
