package com.smartsense.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartsense.app.data.ble.BleManager
import com.smartsense.app.data.ble.ScannedSensor
import com.smartsense.app.data.local.dao.SensorDao
import com.smartsense.app.data.local.entity.SensorEntity
import com.smartsense.app.data.local.entity.SyncStatus
import com.smartsense.app.data.local.entity.TankEntity
import com.smartsense.app.di.ApplicationScope
import com.smartsense.app.domain.model.MapToSensorEnum
import com.smartsense.app.domain.model.MopekaSensorType
import com.smartsense.app.domain.model.NotificationFrequency
import com.smartsense.app.domain.model.QualityThreshold
import com.smartsense.app.domain.model.ReadQuality
import com.smartsense.app.domain.model.Sensor
import com.smartsense.app.domain.model.Tank
import com.smartsense.app.domain.model.TankLevelUnit
import com.smartsense.app.domain.model.TankOrientation
import com.smartsense.app.domain.model.TankRegion
import com.smartsense.app.domain.model.TankType
import com.smartsense.app.domain.model.TriggerAlarmUnit
import com.smartsense.app.domain.model.SensorReading
import com.smartsense.app.domain.usecase.CalculateTankUseCase
import com.smartsense.app.util.uppercaseFirst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import timber.log.Timber
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
    appScope: ApplicationScope.AppScope
) {
    companion object {
        private const val TAG = "SensorRepository"
        private const val SYNC_WORK_NAME = "SensorSyncWork"
    }

    private val appCoroutineScope: CoroutineScope = appScope.scope

    // --- State Management ---
    private val _rawReadings = kotlinx.coroutines.flow.MutableSharedFlow<ScannedSensor>(extraBufferCapacity = 1)
    private val liveReadings = MutableStateFlow<Map<String, ScannedSensor>>(emptyMap())

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

    fun discoverSensors(scanIntervalMillis: Long): Flow<List<Sensor>> =
        bleManager.startScan()
            .onEach(::cacheReading)
            .sample(scanIntervalMillis)
            .map {
                Timber.tag(TAG).i("discoverSensors triggered")
                // Apply RSSI filtering only for discovery (not registered sensors)
                val filtered = liveReadings.value.filter { (_, scanned) ->
                    val threshold = if (scanned.parsed?.syncPressed == true)
                        com.smartsense.app.data.ble.BleConstants.SYNC_RSSI_THRESHOLD
                    else
                        com.smartsense.app.data.ble.BleConstants.DEFAULT_RSSI_THRESHOLD
                    scanned.rssi >= threshold
                }
                mapToSensorList(filtered)
            }
            .distinctUntilChanged()

    fun stopScan() = bleManager.stopScan()

    fun observeRawReadings(): Flow<ScannedSensor> = _rawReadings


    private fun cacheReading(scanned: ScannedSensor) {
        liveReadings.update { it + (scanned.address to scanned) }
        _rawReadings.tryEmit(scanned)
    }

    // --------------------------------------
    // 📡 OBSERVATION (Registered & Detail)
    // --------------------------------------

    fun observeRegisteredSensors(scanIntervalMillis: Long): Flow<List<Sensor>> {
        val ticker = createTicker(scanIntervalMillis)

        return combine(
            sensorDao.observeRegisteredSensors(),
            ticker
        ) { sensorEntities, _ ->
            Timber.d("⏱ tick (list) with ${sensorEntities.size} sensors")

            val currentReadings = sharedReadings.value
            val tankMap = sharedTanks.value.associateBy { it.sensorAddress }

            sensorEntities.map { entity ->
                val address = entity.address
                val scanned = currentReadings[address]
                val tank = tankMap[address]?.toDomain()

                if (scanned != null) {
                    scanned.parsed?.reading?.timestampMillis = System.currentTimeMillis()
                    persistReading(scanned, address)
                    mapToSensor(
                        scanned = scanned,
                        tank = tank,
                        mapToSensorEnum = MapToSensorEnum.OBSERVE_REGISTERED
                    )
                } else {
                    val entity = sensorDao.getSensor(address)
                    if (entity != null) {
                        mapFromPersistedReading(entity, tank, MapToSensorEnum.OBSERVE_REGISTERED)
                    } else null
                }
            }.filterNotNull().also { Timber.d("🚀 emit list size=${it.size}") }
        }.distinctUntilChanged()
    }

    fun observeSensorForDetail(address: String, scanIntervalMillis: Long): Flow<Sensor?> =
        createTicker(scanIntervalMillis).map {
            Timber.d("⏱ tick (detail)")
            val scanned = sharedReadings.value[address]
            val tankMap = sharedTanks.value.associateBy { it.sensorAddress }
            val tank = tankMap[address]?.toDomain()

            if (scanned != null) {
                scanned.parsed?.reading?.timestampMillis = System.currentTimeMillis()
                persistReading(scanned, address)
                mapToSensor(
                    scanned = scanned,
                    tank = tank,
                    mapToSensorEnum = MapToSensorEnum.OBSERVE_DETAIL
                )
            } else {
                val entity = sensorDao.getSensor(address)
                if (entity != null) {
                    mapFromPersistedReading(entity, tank, MapToSensorEnum.OBSERVE_DETAIL)
                } else null
            }.also { Timber.d("🚀 emit detail: $it") }
        }

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
    // 💾 LOCAL DATABASE OPERATIONS
    // --------------------------------------

    fun getAllRegisteredSensors(): Flow<List<Sensor>> =
        sensorDao.getAllRegisteredSensors().map { entities -> entities.map { it.toDomain() } }

    suspend fun registerSensor(address: String, name: String) {
        Timber.i("💾 Repository: Processing registration for $address")
        val existing = sensorDao.getSensor(address)

        if (existing != null && existing.syncStatus != SyncStatus.DELETED) {
            Timber.d("ℹ️ Sensor $address is already active. No changes needed.")
            return
        }

        val now = System.currentTimeMillis()
        val scanned = liveReadings.value[address]
        val reading = scanned?.parsed?.reading

        val sensor = SensorEntity(
            address = address,
            name = name.ifBlank { address },
            lastSeenMillis = now,
            registered = true,
            syncStatus = SyncStatus.PENDING,
            lastModifiedLocally = now,
            lastBatteryVoltage = reading?.batteryVoltage ?: 0f,
            lastRssi = reading?.rssi ?: 0,
            lastQuality = reading?.quality ?: 0,
            lastTemperatureCelsius = reading?.temperatureCelsius ?: 0f,
            lastRawHeightMeters = reading?.rawHeightMeters ?: 0.0,
            lastReadingTimestamp = if (reading != null) now else 0,
            lastSensorType = scanned?.parsed?.sensorType?.name ?: ""
        )
        val tank = TankEntity(
            name=name,
            sensorAddress = address,
            syncStatus = SyncStatus.PENDING,
            lastModifiedLocally = now
        )

        try {
            sensorDao.insertSensor(sensor)
            sensorDao.insertTank(tank)
            Timber.d("✅ Database updated: $address status set to PENDING")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to write sensor to DB")
            throw e
        }
    }

    suspend fun markSensorTankAsDeleted(address: String) {
        Timber.i("💾 Repository: Marking sensor and tank as DELETED: $address")
        try {
            sensorDao.markSensorForDeletion(address)
            sensorDao.markTankForDeletion(address)
            Timber.d("✅ Local DB updated successfully for $address")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to update local status for $address")
            throw e
        }
    }

    suspend fun markAllSensorsTanksAsDeleted() {
        Timber.i("💾 Repository: Marking all sensors and tanks as DELETED")
        try {
            sensorDao.markAllSensorsForDeletion()
            sensorDao.markAllTanksForDeletion()
            Timber.d("✅ Local DB updated successfully")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to update local status")
            throw e
        }
    }

    suspend fun saveTankConfig(tank: Tank) {
        val now = System.currentTimeMillis()
        val tankEntity = tank.toEntity().copy(syncStatus = SyncStatus.PENDING, lastModifiedLocally = now)
        sensorDao.insertTank(tankEntity)

        val existingSensor = sensorDao.getSensor(tank.sensorAddress)
        if (existingSensor == null) {
            sensorDao.insertSensor(SensorEntity(
                address = tank.sensorAddress,
                name = tank.name,
                registered = true,
                syncStatus = SyncStatus.PENDING,
                lastModifiedLocally = now
            ))
        } else {
            sensorDao.updateSensorName(tank.sensorAddress, tank.name)
        }
    }

    suspend fun unregisterSensorTankPermanent(address: String) {
        sensorDao.deleteSensorPermanently(address)
        sensorDao.deleteTankPermanently(address)
    }

//    suspend fun unregisterAllSensors() {
//        sensorDao.deleteAllSensors()
//        sensorDao.deleteAllTanks()
//    }

    suspend fun getTankConfig(sensorAddress: String): Tank? =
        sensorDao.getTank(sensorAddress)?.toDomain()

    // --------------------------------------
    // 🔄 CLOUD SYNC & WORKER
    // --------------------------------------
    /**
     * Pushes local changes (PENDING or DELETED) to Firestore.
     * @return Total number of successful cloud operations.
     */
    suspend fun uploadPendingChanges(): Int {
        val userId = auth.currentUser?.uid ?: run {
            Timber.tag(TAG).w("📤 uploadPendingChanges: Aborted. No authenticated user.")
            return 0
        }

        var totalProcessed = 0
        Timber.tag(TAG).i("📤 uploadPendingChanges: Starting upload for user: $userId")

        try {
            // --- 1. Sync Sensors ---
            val pendingSensors = sensorDao.getUnsyncedSensors()
            Timber.tag(TAG).d("📤 Sync: Found ${pendingSensors.size} sensors requiring sync.")

            pendingSensors.forEach { sensor ->
                val docRef = firestore.collection("users/$userId/sensors").document(sensor.address)

                if (sensor.syncStatus == SyncStatus.DELETED) {
                    Timber.tag(TAG).v("🗑️ Sync: Deleting sensor document [${sensor.address}] from Cloud...")
                    docRef.delete().await()
                    sensorDao.deleteSensorPermanently(sensor.address)
                    Timber.tag(TAG).d("✅ Sync: Permanent local delete for [${sensor.address}] after Cloud success.")
                } else {
                    Timber.tag(TAG).v("📤 Sync: Uploading sensor [${sensor.address}] (Status: ${sensor.syncStatus})...")
                    val sensorToUpload = sensor.copy(syncStatus = SyncStatus.SYNCED)
                    docRef.set(sensorToUpload).await()
                    sensorDao.updateSyncStatus(sensor.address, SyncStatus.SYNCED)
                    Timber.tag(TAG).d("✅ Sync: Sensor [${sensor.address}] marked as SYNCED.")
                }
                totalProcessed++
            }

            // --- 2. Sync Tanks ---
            val pendingTanks = sensorDao.getUnsyncedTanks()
            Timber.tag(TAG).d("📤 Sync: Found ${pendingTanks.size} tanks requiring sync.")

            pendingTanks.forEach { tank ->
                val docRef = firestore.collection("users/$userId/tanks").document(tank.sensorAddress)

                if (tank.syncStatus == SyncStatus.DELETED) {
                    Timber.tag(TAG).v("🗑️ Sync: Deleting tank document [${tank.sensorAddress}] from Cloud...")
                    docRef.delete().await()
                    sensorDao.deleteTankPermanently(tank.sensorAddress)
                    Timber.tag(TAG).d("✅ Sync: Permanent local delete for tank [${tank.sensorAddress}] after Cloud success.")
                } else {
                    Timber.tag(TAG).v("📤 Sync: Uploading tank for [${tank.sensorAddress}]...")
                    val tankToUpload = tank.copy(syncStatus = SyncStatus.SYNCED)
                    docRef.set(tankToUpload).await()
                    sensorDao.updateTankSyncStatus(tank.sensorAddress, SyncStatus.SYNCED)
                    Timber.tag(TAG).d("✅ Sync: Tank [${tank.sensorAddress}] marked as SYNCED.")
                }
                totalProcessed++
            }

            Timber.tag(TAG).i("🏁 uploadPendingChanges: Finished. Total processed: $totalProcessed")
            return totalProcessed
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "🔥 uploadPendingChanges: Critical failure during upload loop.")
            throw e
        }
    }

    /**
     * Pulls changes from Firestore and updates local database.
     * @return Total number of records downloaded/updated.
     */
    suspend fun downloadRemoteChanges(): Int {
        val userId = auth.currentUser?.uid ?: run {
            Timber.tag(TAG).w("📥 downloadRemoteChanges: Aborted. No authenticated user.")
            return 0
        }

        var totalDownloaded = 0
        Timber.tag(TAG).i("📥 downloadRemoteChanges: Starting fetch for user: $userId")

        try {
            // --- 1. Sync Sensors ---
            val lastSensorSyncTime: Long = sensorDao.getLatestSensorModified() ?: 0L
            val sensorQuery = firestore.collection("users/$userId/sensors")

            Timber.tag(TAG).d("📥 Fetching sensors modified after: $lastSensorSyncTime")
            val sensorSnapshot = if (lastSensorSyncTime == 0L) {
                sensorQuery.get().await()
            } else {
                sensorQuery.whereGreaterThan("last_modified_locally", lastSensorSyncTime).get().await()
            }

            val remoteSensors = sensorSnapshot.toObjects(SensorEntity::class.java)
            Timber.tag(TAG).d("📥 Found ${remoteSensors.size} remote sensor updates.")

            remoteSensors.forEach { remote ->
                val local = sensorDao.getSensor(remote.address)

                if (local == null || local.syncStatus == SyncStatus.SYNCED) {
                    Timber.tag(TAG).v("📥 Applying remote sensor update: [${remote.address}]")
                    sensorDao.upsertSensor(remote.copy(syncStatus = SyncStatus.SYNCED))
                    totalDownloaded++
                } else {
                    Timber.tag(TAG).w("⏭️ Conflict: Skipping sensor [${remote.address}]. Local has ${local.syncStatus} changes.")
                }
            }

            // --- 2. Sync Tanks ---
            val lastTankSyncTime: Long = sensorDao.getLatestTankModified() ?: 0L
            val tankQuery = firestore.collection("users/$userId/tanks")

            Timber.tag(TAG).d("📥 Fetching tanks modified after: $lastTankSyncTime")
            val tankSnapshot = if (lastTankSyncTime == 0L) {
                tankQuery.get().await()
            } else {
                tankQuery.whereGreaterThan("last_modified_locally", lastTankSyncTime).get().await()
            }

            val remoteTanks = tankSnapshot.toObjects(TankEntity::class.java)
            Timber.tag(TAG).d("📥 Found ${remoteTanks.size} remote tank updates.")

            remoteTanks.forEach { remote ->
                val local = sensorDao.getTank(remote.sensorAddress)

                if (local == null || local.syncStatus == SyncStatus.SYNCED) {
                    Timber.tag(TAG).v("📥 Applying remote tank update for: [${remote.sensorAddress}]")
                    sensorDao.upsertTank(remote.copy(syncStatus = SyncStatus.SYNCED))
                    totalDownloaded++
                } else {
                    Timber.tag(TAG).w("⏭️ Conflict: Skipping tank [${remote.sensorAddress}]. Local has ${local.syncStatus} changes.")
                }
            }

            Timber.tag(TAG).i("🏁 downloadRemoteChanges: Finished. Total items downloaded/updated: $totalDownloaded")
            return totalDownloaded

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "🔥 downloadRemoteChanges: Error during remote fetch.")
            throw e
        }
    }

    suspend fun resetLocalDataForNewAccount()=sensorDao.resetLocalDataForNewAccount()

    // --------------------------------------
    // 💾 READING PERSISTENCE
    // --------------------------------------

    private fun persistReading(scanned: ScannedSensor, address: String) {
        val reading = scanned.parsed?.reading ?: return
        appCoroutineScope.launch {
            try {
                sensorDao.updateLastReading(
                    address = address,
                    batteryVoltage = reading.batteryVoltage,
                    rssi = reading.rssi,
                    quality = reading.quality,
                    temperatureCelsius = reading.temperatureCelsius,
                    rawHeightMeters = reading.rawHeightMeters,
                    timestamp = System.currentTimeMillis(),
                    sensorType = scanned.parsed.sensorType.name
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to persist reading for $address")
            }
        }
    }

    private fun mapFromPersistedReading(
        entity: SensorEntity,
        tank: Tank?,
        mapToSensorEnum: MapToSensorEnum
    ): Sensor {
        val hasReading = entity.lastReadingTimestamp > 0
        val sensorType = if (entity.lastSensorType.isNotEmpty()) {
            try { MopekaSensorType.valueOf(entity.lastSensorType) } catch (_: Exception) { null }
        } else null

        val reading = if (hasReading) SensorReading(
            rawHeightMeters = entity.lastRawHeightMeters,
            batteryVoltage = entity.lastBatteryVoltage,
            rssi = entity.lastRssi,
            quality = entity.lastQuality,
            temperatureCelsius = entity.lastTemperatureCelsius,
            firmwareVersion = "",
            timestampMillis = entity.lastReadingTimestamp
        ) else null

        val tankLevel = if (hasReading && mapToSensorEnum != MapToSensorEnum.DISCOVER) {
            calculateTankUseCase.calculateTankLevel(
                rawHeightMeters = entity.lastRawHeightMeters,
                tankHeightMm = calculateTankUseCase.calculateTankHeightMm(tank),
                tankType = calculateTankUseCase.calculateTankType(tank)
            )
        } else null

        val tankTypeDisplay = tank?.let {
            if (it.type == TankType.ARBITRARY)
                "${it.type.displayName} ${it.orientation.name.lowercase().replaceFirstChar { c -> c.uppercase() }}"
            else it.type.displayName
        }

        return Sensor(
            address = entity.address,
            name = calculateTankUseCase.calculateName(sensorType, tank?.name),
            sensorType = sensorType,
            reading = reading,
            tankLevel = tankLevel,
            readQuality = if (mapToSensorEnum == MapToSensorEnum.OBSERVE_DETAIL && hasReading)
                entity.lastQuality.toReadQuality() else null,
            tankType = tankTypeDisplay
        )
    }

    // --------------------------------------
    // 🧠 MAPPING LOGIC
    // --------------------------------------

    private fun mapToSensorList(readings: Map<String, ScannedSensor>): List<Sensor> =
        readings.values.map { mapToSensor(it, null, MapToSensorEnum.DISCOVER) }
            .sortedByDescending { it.reading?.timestampMillis }

    private fun mapToSensor(scanned: ScannedSensor, tank: Tank? = null, mapToSensorEnum: MapToSensorEnum): Sensor {
        val reading = scanned.parsed?.reading
        val tankLevel = if (mapToSensorEnum != MapToSensorEnum.DISCOVER) {
            calculateTankUseCase.calculateTankLevel(
                rawHeightMeters = reading?.rawHeightMeters ?: 0.0,
                tankHeightMm = calculateTankUseCase.calculateTankHeightMm(tank),
                tankType = calculateTankUseCase.calculateTankType(tank),
                rawData = scanned.parsed?.rawData
            )
            //    .apply { percentage = Random.nextFloat() * 100f }
        } else null

        return Sensor(
            address = scanned.address,
            name = calculateTankUseCase.calculateName(scanned.parsed?.sensorType, tank?.name),
            advertisedName = scanned.name,
            sensorType = scanned.parsed?.sensorType,
            syncPressed = scanned.parsed?.syncPressed ?: false,
            reading = reading,
            tankLevel = tankLevel,
            readQuality = if (mapToSensorEnum == MapToSensorEnum.OBSERVE_DETAIL) reading?.quality?.toReadQuality() else null,
            tankType = if (tank != null) {
                // Logging the string construction for the UI
                val displayType = if (tank.type == TankType.ARBITRARY) {
                    "${tank.type.displayName} ${tank.orientation.name.uppercaseFirst()}"
                } else {
                    tank.type.displayName
                }
                Timber.d("Final tankType string: ${tank.type}")
                displayType
            } else null
        )
    }

    private fun Int?.toReadQuality(): ReadQuality = when (this) {
        3 -> ReadQuality.GOOD
        2 -> ReadQuality.FAIR
        else -> ReadQuality.POOR
    }



    // --- Entity/Domain Mappers ---

    private fun TankEntity.toDomain(): Tank = Tank(
        sensorAddress = sensorAddress, name = name, type = enumOrDefault(tankType, TankType.default()),
        customHeightMeters = customHeightMeters, orientation = enumOrDefault(orientation, TankOrientation.VERTICAL),
        alarmThresholdPercent = alarmThresholdPercent, region = enumOrDefault(region, TankRegion.UNITED_STATE),
        levelUnit = enumOrDefault(levelUnit, TankLevelUnit.PERCENT), notificationsEnabled = notificationsEnabled,
        notificationFrequency = enumOrDefault(notificationFrequency, NotificationFrequency.EVERY_12_HOURS),
        triggerAlarmUnit = enumOrDefault(triggerAlarmUnit, TriggerAlarmUnit.ABOVE),
        qualityThreshold = enumOrDefault(qualityThreshold, QualityThreshold.DISABLE)
    )

    private fun Tank.toEntity(): TankEntity = TankEntity(
        sensorAddress = sensorAddress, name = name, tankType = type.name, customHeightMeters = customHeightMeters,
        orientation = orientation.name, alarmThresholdPercent = alarmThresholdPercent, region = region.name,
        levelUnit = levelUnit.name, notificationsEnabled = notificationsEnabled, notificationFrequency = notificationFrequency.name,
        triggerAlarmUnit = triggerAlarmUnit.name, qualityThreshold = qualityThreshold.name
    )

    fun SensorEntity.toDomain(): Sensor = Sensor(address = address, name = name, sensorType = null, syncStatus = syncStatus)

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        try { enumValueOf<T>(value) } catch (_: Exception) { default }
}