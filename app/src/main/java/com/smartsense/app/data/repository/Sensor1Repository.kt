package com.smartsense.app.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartsense.app.data.ble.BleManager
import com.smartsense.app.data.ble.ScannedSensor
import com.smartsense.app.data.worker.SyncWorker
import com.smartsense.app.domain.model.MopekaSensorType
import com.smartsense.app.domain.model.NotificationFrequency

import com.smartsense.app.data.local.dao.SensorDao
import com.smartsense.app.data.local.entity.SensorEntity
import com.smartsense.app.data.local.entity.SyncStatus
import com.smartsense.app.data.local.entity.TankEntity
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.domain.model.QualityThreshold
import com.smartsense.app.domain.model.ReadQuality
import com.smartsense.app.domain.model.Sensor1
import com.smartsense.app.domain.model.SortPreference
import com.smartsense.app.domain.model.Tank
import com.smartsense.app.domain.model.TankLevelUnit
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankRegion
import com.smartsense.app.domain.model.TankType
import com.smartsense.app.domain.model.TriggerAlarmUnit
import com.smartsense.app.domain.usecase.CalculateTankUseCase
import com.smartsense.app.util.uppercaseFirst
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class Sensor1Repository @Inject constructor(
    private val bleManager: BleManager,
    private val sensorDao: SensorDao,
    private val calculateTankUseCase: CalculateTankUseCase,
    private val userPreferences: UserPreferences,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Sensor1Repository"
    }
    private val liveReadings = MutableStateFlow<Map<String, ScannedSensor>>(emptyMap())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private var isMatchedTest=false
    // --------------------------------------
    // 🔍 SCANNING
    // --------------------------------------

    fun discoverSensors(scanIntervalMillis: Long): Flow<List<Sensor1>> {
        return bleManager.startScan()
            .onEach(::cacheReading)
            .sample(scanIntervalMillis)
            .map {
                Timber.tag(TAG).i("discoverSensors")
                mapToSensorList(liveReadings.value)
            }
            .onCompletion { _isScanning.value = false }
            .distinctUntilChanged()
    }

       fun getAllRegisteredSensors():Flow<List<Sensor1>> = sensorDao.getAllRegisteredSensors().map { it.map { it.toDomain() } }

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
        // 1. Create the ticker to pulse updates
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
                sensorDao.observeAllTanks().take(1),
                userPreferences.sortPreference // 2. Add Sort Preference to the stream
            ) { addresses, readings, tanks, sortPref ->

                val tankMap = tanks.associateBy { it.sensorAddress }

                val mappedSensors = addresses.mapNotNull { address ->
                    val scanned = readings[address] ?: return@mapNotNull null
                    val tank = tankMap[address]?.toDomain()

                    mapToSensor(
                        scanned = scanned,
                        tank = tank,
                        mapToSensorEnum = MapToSensorEnum.OBSERVE_REGISTERED
                    )
                }

                // 3. Sort reactively without using .first()
                if (sortPref == SortPreference.NAME) {
                    mappedSensors.sortedBy { it.name ?: "" }
                } else {
                    // Usually for levels, you want Highest at the top
                    mappedSensors.sortedByDescending { it.tankLevel?.percentage ?: 0f }
                }
            }
        }.distinctUntilChanged()
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
                Timber.i("observeSensorForDetail")
                mapToSensor(
                    scanned, tank,
                    mapToSensorEnum = MapToSensorEnum.OBSERVE_DETAIL
                )
            }
        }
    }

    fun filterSensors(
        sensorsFlow: Flow<List<Sensor1>>,
        queryFlow: Flow<String>
    ): Flow<List<Sensor1>> = combine(sensorsFlow, queryFlow) { sensors, query ->
        if (query.isBlank()) {
            sensors
        } else {
            sensors.filter { sensor ->
                sensor.name?.contains(query, ignoreCase = true) == true ||
                        sensor.address.contains(query, ignoreCase = true)
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

        tankLevel?.percentage = Random.nextFloat() * 100f
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
                registered = true
            )
        )
        sensorDao.insertTank(
            TankEntity(sensorAddress = address)
        )
        // 2. Trigger the SyncWorker to push to Firebase
        triggerSync()
    }

    suspend fun unregisterSensor(address: String) {
        sensorDao.markSensorForDeletion(address)
        sensorDao.deleteTank(address)
    }

    suspend fun saveTankConfig(tank: Tank) {
        sensorDao.insertTank(tank.toEntity())
        sensorDao.insertSensor(
            SensorEntity(
                address = tank.sensorAddress,
                name = tank.name,
                registered = true
            )
        )
    }

    suspend fun unregisterAllSensors() {
        sensorDao.deleteAllSensors()
        sensorDao.deleteAllTanks()
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

     fun SensorEntity.toDomain(): Sensor1 = Sensor1(
        address = address,
        name = name,
        sensorType = null,
        syncStatus = syncStatus
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T {
        return try {
            enumValueOf<T>(value)
        } catch (_: Exception) {
            default
        }
    }

    suspend fun uploadPendingChanges() {
        val userId = auth.currentUser?.uid ?: return
        val pendingSensors = sensorDao.getUnsyncedSensors()

        pendingSensors.forEach { sensor ->
            val docRef = firestore.collection("users/$userId/sensors").document(sensor.address)

            try {
                if (sensor.syncStatus == SyncStatus.DELETED) {
                    // 1. Attempt to delete from Cloud
                    docRef.delete().await()

                    // 2. SUCCESS! Now safe to wipe from local Room
                    sensorDao.deleteSensorPermanently(sensor.address)
                    Timber.d("Successfully deleted ${sensor.address} from Cloud and Local.")
                } else {
                    // Handle normal PENDING upload...
                    docRef.set(sensor).await()
                    sensorDao.updateSyncStatus(sensor.address, SyncStatus.SYNCED)
                }
            } catch (_: Exception) {
                // If network fails, the sensor stays in Room as 'DELETED'.
                // The Worker will try again automatically on the next run.
                Timber.e("Cloud delete failed for ${sensor.address}, will retry.")
            }
        }
    }

    fun triggerSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "sensor_sync_job",
            ExistingWorkPolicy.REPLACE, // Restart the sync if one is already queued
            syncRequest
        )
    }
    suspend fun downloadRemoteChanges() {
        val user = auth.currentUser
        if (user == null) {
            Timber.e("Download aborted: No authenticated user found.")
            return
        }
        val userId = user.uid
        Timber.d("Download started for User: $userId")

        try {
            // 1. Fetch from Firestore
            val snapshot = firestore.collection("users/$userId/sensors").get().await()

            if (snapshot.isEmpty) {
                Timber.w("Download finished: No sensors found in Firestore for this user.")
                return
            }

            Timber.i("Found ${snapshot.size()} sensors on cloud. Starting local sync...")

            // 2. Map to objects
            val remoteSensors = snapshot.toObjects(SensorEntity::class.java)

            remoteSensors.forEach { remoteSensor ->
                Timber.d("Processing remote sensor: ${remoteSensor.address} (${remoteSensor.name})")

                // 3. Update local Room
                // We set status to SYNCED so the Worker doesn't immediately try to upload it back
                sensorDao.upsertSensor(remoteSensor.copy(syncStatus = SyncStatus.SYNCED))
            }

            Timber.i("Download sync completed successfully.")

        } catch (e: Exception) {
            Timber.e(e, "Error downloading remote changes: ${e.message}")
        }
    }

    enum class MapToSensorEnum{
        DISCOVER,OBSERVE_REGISTERED,OBSERVE_DETAIL
    }
}