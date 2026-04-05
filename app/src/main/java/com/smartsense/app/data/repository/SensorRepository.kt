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
import com.smartsense.app.data.local.dao.SensorDao
import com.smartsense.app.data.local.entity.SensorEntity
import com.smartsense.app.data.local.entity.SyncStatus
import com.smartsense.app.data.local.entity.TankEntity
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.data.worker.SyncWorker
import com.smartsense.app.di.ApplicationScope
import com.smartsense.app.domain.model.MapToSensorEnum
import com.smartsense.app.domain.model.MopekaSensorType
import com.smartsense.app.domain.model.NotificationFrequency
import com.smartsense.app.domain.model.QualityThreshold
import com.smartsense.app.domain.model.ReadQuality
import com.smartsense.app.domain.model.Sensor
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
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
class SensorRepository @Inject constructor(
    private val bleManager: BleManager,
    private val sensorDao: SensorDao,
    private val calculateTankUseCase: CalculateTankUseCase,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    private val appScope: ApplicationScope.AppScope
) {
    companion object {
        private const val TAG = "SensorRepository"
        private const val SYNC_WORK_NAME = "sensor_sync_job"
    }

    private val liveReadings = MutableStateFlow<Map<String, ScannedSensor>>(emptyMap())

    // State representation of live readings for efficient access
    val sharedReadings = liveReadings.stateIn(
        scope = appScope.scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )
    val sharedTanks = sensorDao.observeAllTanks()
        .stateIn(
            scope = appScope.scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val isBluetoothEnabled: Boolean get() = bleManager.isBluetoothEnabled

    // --------------------------------------
    // 🔍 SCANNING & DISCOVERY
    // --------------------------------------

    fun discoverSensors(scanIntervalMillis: Long): Flow<List<Sensor>> {
        return bleManager.startScan()
            .onEach(::cacheReading)
            .sample(scanIntervalMillis)
            .map {
                Timber.tag(TAG).i("discoverSensors triggered")
                mapToSensorList(liveReadings.value)
            }
            .onCompletion {  }
            .distinctUntilChanged()
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    private fun cacheReading(scanned: ScannedSensor) {
        liveReadings.update { it + (scanned.address to scanned) }
    }

    // --------------------------------------
    // 📡 OBSERVATION (Registered & Detail)
    // --------------------------------------

    /**
     * Internal helper to create a ticker for periodic UI refreshes
     */
    /**
     * 📡 OBSERVE REGISTERED SENSORS
     * Reacts to Database address changes OR the Ticker pulse.
     */
    fun observeRegisteredSensors(
        scanIntervalMillis: Long
    ): Flow<List<Sensor>> {
        // 1. Unified Ticker
        val ticker = createTicker(scanIntervalMillis)

        // 2. Combine DB addresses with the Ticker
        return combine(
            sensorDao.observeRegisteredAddresses(),
            ticker
        ) { addresses, _ ->
            Timber.d("⏱ tick (list) with ${addresses.size} addresses")

            // Capture current readings snapshot once for this emission
            val currentReadings = sharedReadings.value
            val tankMap = sharedTanks.value.associateBy { it.sensorAddress }

            val result = addresses.mapNotNull { address ->
                val scanned = currentReadings[address] ?: return@mapNotNull null

                // Refresh timestamp for UI "Time Ago" logic
                scanned.parsed?.reading?.timestampMillis = System.currentTimeMillis()
                val tank = tankMap[address]?.toDomain()
                mapToSensor(
                    scanned = scanned,
                    tank = tank,
                    mapToSensorEnum = MapToSensorEnum.OBSERVE_REGISTERED
                )
            }

            Timber.d("🚀 emit list size=${result.size}")
            result
        }.distinctUntilChanged()
    }

    /**
     * 🎯 OBSERVE SENSOR FOR DETAIL
     * Reacts to the Ticker pulse to keep the "Last Updated" timer alive.
     */
    fun observeSensorForDetail(
        address: String,
        scanIntervalMillis: Long
    ): Flow<Sensor?> {

        return createTicker(scanIntervalMillis)
            .map {

                Timber.d("⏱ tick (detail)")

                val currentReadings = sharedReadings.value

                val scanned = currentReadings[address] ?: run {
                    Timber.w("⚠️ No reading for address: $address")
                    return@map null
                }
                val tankMap = sharedTanks.value.associateBy { it.sensorAddress }
                // force refresh timestamp
                scanned.parsed?.reading?.timestampMillis = System.currentTimeMillis()
                val tank = tankMap[address]?.toDomain()
                val result = mapToSensor(
                    scanned = scanned,
                    tank = tank,
                    mapToSensorEnum = MapToSensorEnum.OBSERVE_DETAIL
                )

                Timber.d("🚀 emit detail: $result")

                result
            }
        // ⚠️ see note below
        // .distinctUntilChanged()
    }

    /**
     * Internal helper to create a cold flow ticker
     */
    private fun createTicker(interval: Long): Flow<Unit> = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(interval)
        }
    }

    fun filterSensors(sensorsFlow: Flow<List<Sensor>>, queryFlow: Flow<String>): Flow<List<Sensor>> =
        combine(sensorsFlow, queryFlow) { sensors, query ->
            if (query.isBlank()) sensors
            else sensors.filter {
                it.name?.contains(query, ignoreCase = true) == true ||
                        it.address.contains(query, ignoreCase = true)
            }
        }

    // --------------------------------------
    // 🧠 MAPPING LOGIC
    // --------------------------------------

    private fun mapToSensorList(readings: Map<String, ScannedSensor>): List<Sensor> {
        return readings.values.map { scanned ->
            mapToSensor(scanned = scanned, mapToSensorEnum = MapToSensorEnum.DISCOVER)
        }.sortedByDescending { it.reading?.timestampMillis }
    }

    private fun mapToSensor(
        scanned: ScannedSensor,
        tank: Tank? = null,
        mapToSensorEnum: MapToSensorEnum
    ): Sensor {
        val reading = scanned.parsed?.reading

        // Determine Tank Level based on context
        val tankLevel = when (mapToSensorEnum) {
            MapToSensorEnum.OBSERVE_REGISTERED, MapToSensorEnum.OBSERVE_DETAIL -> {
                calculateTankUseCase.calculateTankLevel(
                    rawHeightMeters = reading?.rawHeightMeters ?: 0.0,
                    tankHeightMm = calculateTankUseCase.calculateTankHeightMm(tank),
                    tankType = calculateTankUseCase.calculateTankType(tank)
                )
            }
            MapToSensorEnum.DISCOVER -> null
        }

        // Apply visual test overrides (Randomness as requested)
        tankLevel?.percentage = Random.nextFloat() * 100f

        // Detail-specific metadata
        val readQuality = if (mapToSensorEnum == MapToSensorEnum.OBSERVE_DETAIL) {
            reading?.quality?.toReadQuality()
        } else null

        val tankTypeDisplay = if (mapToSensorEnum == MapToSensorEnum.OBSERVE_DETAIL && tank != null) {
            if (tank.type == TankType.ARBITRARY) {
                "${tank.type.displayName} ${tank.type.orientation.name.uppercaseFirst()}"
            } else tank.type.displayName
        } else null

        return Sensor(
            address = scanned.address,
            name = calculateName(scanned, tank),
            advertisedName = scanned.name,
            sensorType = scanned.parsed?.sensorType,
            syncPressed = scanned.parsed?.syncPressed ?: false,
            reading = reading,
            tankLevel = tankLevel,
            readQuality = readQuality,
            tankType = tankTypeDisplay
        )
    }

    private fun Int?.toReadQuality(): ReadQuality = when (this) {
        3 -> ReadQuality.GOOD
        2 -> ReadQuality.FAIR
        else -> ReadQuality.POOR
    }

    fun calculateName(scanned: ScannedSensor?, tank: Tank?): String {
        return tank?.name?.takeIf { it.isNotBlank() }?:run {
            when {
                scanned?.parsed?.sensorType?.isLpg != false -> "New LPG Device"
                scanned.parsed.sensorType == MopekaSensorType.BOTTOM_UP_WATER -> "New water sensor"
                else -> "New ${scanned.parsed.sensorType.displayName} Device"
            }
        }
    }

    // --------------------------------------
    // 💾 LOCAL DATABASE OPERATIONS
    // --------------------------------------

    fun getAllRegisteredSensors(): Flow<List<Sensor>> =
        sensorDao.getAllRegisteredSensors().map { entities -> entities.map { it.toDomain() } }

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
        sensorDao.insertTank(TankEntity(sensorAddress = address))
        triggerSync()
    }

    suspend fun unregisterSensor(address: String) {
        sensorDao.markSensorForDeletion(address)
        sensorDao.deleteTank(address)
    }

    suspend fun saveTankConfig(tank: Tank) {
        // 1. Convert domain Tank to Entity and mark as PENDING for cloud sync
        val tankEntity = tank.toEntity().copy(syncStatus = SyncStatus.PENDING)

        // 2. Save/Update the Tank (This is where your Name lives!)
        sensorDao.insertTank(tankEntity)

        // 3. Ensure the Sensor shell exists
        val existingSensor = sensorDao.getSensor(tank.sensorAddress)
        if (existingSensor == null) {
            // Only insert a new sensor if it doesn't exist
            sensorDao.insertSensor(
                SensorEntity(
                    address = tank.sensorAddress,
                    name = tank.name, // Initial name
                    registered = true,
                    syncStatus = SyncStatus.PENDING
                )
            )
        } else {
            // If the sensor exists, we ONLY update the name in the Sensor table
            // to keep it consistent with the Tank table.
            sensorDao.updateSensorName(tank.sensorAddress, tank.name)
        }

        // 4. Push to Cloud
        triggerSync()
    }


    suspend fun unregisterAllSensors() {
        sensorDao.deleteAllSensors()
        sensorDao.deleteAllTanks()
    }

    suspend fun getTankConfig(sensorAddress: String): Tank? =
        sensorDao.getTank(sensorAddress)?.toDomain()

    // --------------------------------------
    // 🔄 CLOUD SYNC & WORKER
    // --------------------------------------

    fun triggerSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    suspend fun uploadPendingChanges() {
        val userId = auth.currentUser?.uid ?: run {
            Timber.tag(TAG).w("📤 Upload aborted: No authenticated user.")
            return
        }

        Timber.tag(TAG).d("📤 Starting upload sync for user: $userId")

        try {
            // --- 1. Sync Sensors (Updates & Deletes) ---
            val pendingSensors = sensorDao.getUnsyncedSensors()
            if (pendingSensors.isNotEmpty()) {
                Timber.tag(TAG).i("📡 Found ${pendingSensors.size} pending sensor changes.")
            }

            pendingSensors.forEach { sensor ->
                val docRef = firestore.collection("users/$userId/sensors").document(sensor.address)

                if (sensor.syncStatus == SyncStatus.DELETED) {
                    Timber.tag(TAG).d("🗑️ Deleting sensor from cloud: ${sensor.address}")
                    docRef.delete().await()
                    sensorDao.deleteSensorPermanently(sensor.address)
                    Timber.tag(TAG).v("✅ Successfully deleted ${sensor.address} locally and from cloud.")
                } else {
                    Timber.tag(TAG).d("☁️ Uploading sensor data: ${sensor.address} (Status: ${sensor.syncStatus})")
                    docRef.set(sensor).await()
                    sensorDao.updateSyncStatus(sensor.address, SyncStatus.SYNCED)
                    Timber.tag(TAG).v("✅ Sensor ${sensor.address} marked as SYNCED.")
                }
            }

            // --- 2. Sync Tanks (Updates) ---
            val pendingTanks = sensorDao.getUnsyncedTanks()
            if (pendingTanks.isNotEmpty()) {
                Timber.tag(TAG).i("🛢️ Found ${pendingTanks.size} pending tank configurations.")
            }

            pendingTanks.forEach { tank ->
                val docRef = firestore.collection("users/$userId/tanks").document(tank.sensorAddress)

                Timber.tag(TAG).d("📤 Uploading tank config: ${tank.sensorAddress}")
                docRef.set(tank).await()
                sensorDao.updateTankSyncStatus(tank.sensorAddress, SyncStatus.SYNCED)
                Timber.tag(TAG).v("✅ Tank ${tank.sensorAddress} marked as SYNCED.")
            }

            Timber.tag(TAG).i("🏁 Local upload sync completed successfully.")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Critical error during upload sync. WorkManager will retry.")
            throw e // Rethrow to trigger WorkManager's backoff/retry policy
        }
    }

    suspend fun downloadRemoteChanges() {
        val userId = auth.currentUser?.uid ?: run {
            Timber.tag(TAG).w("📥 Download aborted: No authenticated user.")
            return
        }

        Timber.tag(TAG).d("📥 Starting remote sync for user: $userId")

        try {
            // --- 1. Sync Sensors ---
            val sensorSnapshot = firestore.collection("users/$userId/sensors").get().await()
            val remoteSensors = sensorSnapshot.toObjects(SensorEntity::class.java)

            Timber.tag(TAG).i("📡 Found ${remoteSensors.size} sensors in Firestore.")

            remoteSensors.forEach { remote ->
                val local = sensorDao.getSensor(remote.address)

                // Only update if local is already SYNCED or doesn't exist
                if (local == null || local.syncStatus == SyncStatus.SYNCED) {
                    sensorDao.upsertSensor(remote.copy(syncStatus = SyncStatus.SYNCED))
                    Timber.tag(TAG).v("✅ Updated sensor: ${remote.address}")
                } else {
                    Timber.tag(TAG).d("⏭️ Skipping sensor ${remote.address}: Local has pending changes (Status: ${local.syncStatus})")
                }
            }

            // --- 2. Sync Tanks ---
            val tankSnapshot = firestore.collection("users/$userId/tanks").get().await()
            val remoteTanks = tankSnapshot.toObjects(TankEntity::class.java)

            Timber.tag(TAG).i("🛢️ Found ${remoteTanks.size} tanks in Firestore.")

            remoteTanks.forEach { remote ->
                val local = sensorDao.getTank(remote.sensorAddress)

                if (local == null || local.syncStatus == SyncStatus.SYNCED) {
                    sensorDao.upsertTank(remote.copy(syncStatus = SyncStatus.SYNCED))
                    Timber.tag(TAG).v("✅ Updated tank config: ${remote.sensorAddress}")
                } else {
                    Timber.tag(TAG).d("⏭️ Skipping tank ${remote.sensorAddress}: Local settings are newer (Status: ${local.syncStatus})")
                }
            }

            Timber.tag(TAG).i("🏁 Remote download sync completed successfully.")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Error during remote download sync")
            throw e // Rethrow so Worker can handle retry logic if needed
        }
    }

    // --------------------------------------
    // 🏗️ INTERNAL MAPPERS (Domain <-> Entity)
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

    fun SensorEntity.toDomain(): Sensor = Sensor(
        address = address,
        name = name,
        sensorType = null,
        syncStatus = syncStatus
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T {
        return try { enumValueOf<T>(value) } catch (_: Exception) { default }
    }
}