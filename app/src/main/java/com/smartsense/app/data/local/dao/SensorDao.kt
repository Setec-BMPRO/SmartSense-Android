package com.smartsense.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartsense.app.data.local.entity.SensorEntity
import com.smartsense.app.data.local.entity.SyncStatus
import com.smartsense.app.data.local.entity.TankEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {

    // =========================================================================
    // 📡 SENSOR OPERATIONS (UI & Domain)
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSensor(sensor: SensorEntity)

    /**
     * Updated: Now uses a parameter for status.
     * This ensures the Enum is correctly handled by your TypeConverter.
     */
    @Query(" UPDATE sensors SET name = :newName, sync_status = :status,last_modified_locally = :lastModifiedLocally WHERE address = :address")
    suspend fun updateSensorName(
        address: String,
        newName: String,
        status: SyncStatus = SyncStatus.PENDING,
        lastModifiedLocally: Long = System.currentTimeMillis()
    )

    @Query("""
        SELECT * FROM sensors 
        WHERE registered = 1 AND sync_status != 'DELETED' 
        ORDER BY lastSeenMillis DESC
    """)
    fun getAllRegisteredSensors(): Flow<List<SensorEntity>>

    @Query("SELECT MAX(last_modified_locally) FROM sensors")
    suspend fun getLatestSensorModified(): Long?

    @Query("SELECT address FROM sensors WHERE registered = 1 AND sync_status != 'DELETED'")
    fun observeRegisteredAddresses(): Flow<List<String>>

    @Query("SELECT * FROM sensors WHERE address = :address LIMIT 1")
    suspend fun getSensor(address: String): SensorEntity?


    // =========================================================================
    // 🛢️ TANK OPERATIONS (UI & Domain)
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTank(tank: TankEntity)

    @Query("SELECT * FROM tanks WHERE sensorAddress = :sensorAddress LIMIT 1")
    suspend fun getTank(sensorAddress: String): TankEntity?

    @Query("SELECT MAX(last_modified_locally) FROM tanks")
    suspend fun getLatestTankModified(): Long?

    @Query("SELECT * FROM tanks WHERE sensorAddress = :sensorAddress")
    fun observeTank(sensorAddress: String): Flow<TankEntity?>

    @Query("SELECT * FROM tanks")
    fun observeAllTanks(): Flow<List<TankEntity>>

    @Query("UPDATE tanks SET sync_status = :status, last_modified_locally = :timestamp WHERE sensorAddress = :sensorAddress")
    suspend fun markTankForDeletion(
        sensorAddress: String,
        status: SyncStatus = SyncStatus.DELETED,
        timestamp: Long = System.currentTimeMillis()
    )


    // =========================================================================
    // ☁️ CLOUD SYNC OPERATIONS (Worker Facing)
    // =========================================================================

    @Query("SELECT * FROM sensors WHERE sync_status != 'SYNCED'")
    suspend fun getUnsyncedSensors(): List<SensorEntity>

    @Query(" UPDATE sensors SET sync_status = :status, last_modified_locally = :timestamp WHERE address = :address")
    suspend fun updateSyncStatus(
        address: String,
        status: SyncStatus,
        timestamp: Long = System.currentTimeMillis()
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSensor(sensor: SensorEntity)


    // --- Tank Sync ---

    @Query("SELECT * FROM tanks WHERE sync_status != 'SYNCED'")
    suspend fun getUnsyncedTanks(): List<TankEntity>

    @Query(" UPDATE tanks SET sync_status = :status, last_modified_locally = :timestamp WHERE sensorAddress = :address")
    suspend fun updateTankSyncStatus(
        address: String,
        status: SyncStatus,
        timestamp: Long = System.currentTimeMillis()
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTank(tank: TankEntity)

    @Query("DELETE FROM tanks WHERE sensorAddress = :sensorAddress")
    suspend fun deleteTankPermanently(sensorAddress: String)

    // =========================================================================
    // 🗑️ DELETION LOGIC (Soft & Permanent)
    // =========================================================================

    /**
     * Updated: Uses parameter to ensure DELETED status is applied correctly.
     */
    @Query("UPDATE sensors SET sync_status = :status, last_modified_locally = :timestamp WHERE address = :address")
    suspend fun markSensorForDeletion(
        address: String,
        status: SyncStatus = SyncStatus.DELETED,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM sensors WHERE address = :address")
    suspend fun deleteSensorPermanently(address: String)

    @Query("DELETE FROM sensors")
    suspend fun deleteAllSensors()

    @Query("DELETE FROM tanks")
    suspend fun deleteAllTanks()
}