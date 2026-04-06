package com.smartsense.app.data.repository

import android.annotation.SuppressLint
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
import kotlinx.coroutines.flow.first
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
    appScope: ApplicationScope.AppScope,
    private val userPreferences: UserPreferences
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

    suspend fun registerSensor(address: String, name: String,uploadSensorData: Boolean) {
        if (sensorDao.getSensor(address) != null) return

        sensorDao.insertSensor(
            SensorEntity(
                address = address,
                name = name.ifBlank { address },
                lastSeenMillis = System.currentTimeMillis(),
                registered = true,
                syncStatus = SyncStatus.PENDING
            )
        )
        sensorDao.insertTank(TankEntity(sensorAddress = address, syncStatus = SyncStatus.PENDING, lastModifiedLocally = System.currentTimeMillis()))
        if(uploadSensorData)
            triggerSync()
    }

    suspend fun unregisterSensor(address: String, uploadSensorData: Boolean) {
        sensorDao.markSensorForDeletion(address)
        sensorDao.markTankForDeletion(address)
        if(uploadSensorData)
        triggerSync()
    }

    suspend fun unregisterSensorTankPermanent(address: String) {
        sensorDao.deleteSensorPermanently(address)
        sensorDao.deleteTankPermanently(address)
    }



    suspend fun saveTankConfig(tank: Tank,uploadSensorData: Boolean) {
        // 1. Convert domain Tank to Entity and mark as PENDING for cloud sync
        val tankEntity = tank.toEntity().copy(syncStatus = SyncStatus.PENDING, lastModifiedLocally = System.currentTimeMillis())

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
                    syncStatus = SyncStatus.PENDING,
                    lastModifiedLocally = System.currentTimeMillis()
                )
            )
        } else {
            // If the sensor exists, we ONLY update the name in the Sensor table
            // to keep it consistent with the Tank table.
            sensorDao.updateSensorName(tank.sensorAddress, tank.name)
        }

        // 4. Push to Cloud
        if(uploadSensorData)
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

    /**
     * Pushes local changes (inserts, updates, and deletes) to Firestore.
     * @return Total number of successful cloud operations.
     */
    /**
     * Pushes local changes (PENDING or DELETED) to Firestore.
     * @return Total number of successful cloud operations.
     */
    suspend fun uploadPendingChanges(): Int {
        val userId = auth.currentUser?.uid ?: return 0
        var totalProcessed = 0

        try {
            // --- 1. Sync Sensors ---
            val pendingSensors = sensorDao.getUnsyncedSensors()
            pendingSensors.forEach { sensor ->
                val docRef = firestore.collection("users/$userId/sensors").document(sensor.address)
                if (sensor.syncStatus == SyncStatus.DELETED) {
                    docRef.delete().await()
                    sensorDao.deleteSensorPermanently(sensor.address)
                } else {
                    val sensorToUpload = sensor.copy(syncStatus = SyncStatus.SYNCED)
                    docRef.set(sensorToUpload).await()
                    sensorDao.updateSyncStatus(sensor.address, SyncStatus.SYNCED)
                }
                totalProcessed++
            }

            // --- 2. Sync Tanks ---
            val pendingTanks = sensorDao.getUnsyncedTanks()
            pendingTanks.forEach { tank ->
                val docRef = firestore.collection("users/$userId/tanks").document(tank.sensorAddress)

                // ADDED: Check for DELETED status here too!
                if (tank.syncStatus == SyncStatus.DELETED) {
                    docRef.delete().await()
                    sensorDao.deleteTankPermanently(tank.sensorAddress)
                } else {
                    // Normal Upload
                    val tankToUpload = tank.copy(syncStatus = SyncStatus.SYNCED)
                    docRef.set(tankToUpload).await()
                    sensorDao.updateTankSyncStatus(tank.sensorAddress, SyncStatus.SYNCED)
                }
                totalProcessed++
            }
            return totalProcessed
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Pulls changes from Firestore and updates local database.
     * @return Total number of records downloaded/updated.
     */
    /**
     * Pulls changes from Firestore and updates local database.
     * @return Total number of records downloaded/updated.
     */
    suspend fun downloadRemoteChanges(): Int {
        val userId = auth.currentUser?.uid ?: run {
            Timber.tag(TAG).w("📥 Download aborted: No authenticated user.")
            return 0
        }

        var totalDownloaded = 0
        Timber.tag(TAG).d("📥 Starting remote sync for user: $userId")

        try {
            // --- 1. Sync Sensors ---
            // 1. Get the latest timestamp from your local Room DB
            val lastSensorSyncTime: Long = sensorDao.getLatestSensorModified()?:0L

            // To something more robust for first-time sync:
            val query = firestore.collection("users/$userId/sensors")

            val sensorSnapshot = if (lastSensorSyncTime == 0L) {
                // Fresh install: Get everything
                query.get().await()
            } else {
                // Update: Get only new stuff
                query.whereGreaterThan("last_modified_locally", lastSensorSyncTime).get().await()
            }

            val remoteSensors = sensorSnapshot.toObjects(SensorEntity::class.java)

            remoteSensors.forEach { remote ->
                val local = sensorDao.getSensor(remote.address)

                // Logic: If local doesn't exist (new app) OR local is already SYNCED,
                // then it's safe to update with cloud data.
                if (local == null || local.syncStatus == SyncStatus.SYNCED) {
                    // Force status to SYNCED so the local app knows it's up to date
                    sensorDao.upsertSensor(remote.copy(syncStatus = SyncStatus.SYNCED))
                    totalDownloaded++
                } else {
                    Timber.tag(TAG).d("⏭️ Skipping sensor ${remote.address}: Local has unsynced changes.")
                }
            }

            // --- 2. Sync Tanks ---
            val lastTankSyncTime: Long = sensorDao.getLatestTankModified()?:0L
            val queryTank = firestore.collection("users/$userId/tanks")

            val tankSnapshot = if (lastTankSyncTime == 0L) {
                // Fresh install: Get everything
                queryTank.get().await()
            } else {
                // Update: Get only new stuff
                queryTank.whereGreaterThan("last_modified_locally", lastSensorSyncTime).get().await()
            }
            val remoteTanks = tankSnapshot.toObjects(TankEntity::class.java)

            remoteTanks.forEach { remote ->
                val local = sensorDao.getTank(remote.sensorAddress)

                if (local == null || local.syncStatus == SyncStatus.SYNCED) {
                    sensorDao.upsertTank(remote.copy(syncStatus = SyncStatus.SYNCED))
                    totalDownloaded++
                } else {
                    Timber.tag(TAG).d("⏭️ Skipping tank ${remote.sensorAddress}: Local has unsynced changes.")
                }
            }

            Timber.tag(TAG).i("🏁 Download sync complete. Updated: $totalDownloaded")
            return totalDownloaded

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "❌ Error during remote download sync")
            throw e
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