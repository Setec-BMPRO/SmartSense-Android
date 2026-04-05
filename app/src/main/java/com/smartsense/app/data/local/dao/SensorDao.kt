package com.smartsense.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smartsense.app.data.local.entity.SensorEntity
import com.smartsense.app.data.local.entity.SyncStatus
import com.smartsense.app.data.local.entity.TankEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface SensorDao {

    // =========================================================================
    // 📡 SENSOR OPERATIONS (UI & Domain)
    // =========================================================================

    /**
     * Inserts or updates a sensor. When created locally, it defaults to PENDING.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSensor(sensor: SensorEntity)

    @Query("UPDATE sensors SET name = :newName, sync_status = 'PENDING' WHERE address = :address")
    suspend fun updateSensorName(address: String, newName: String)

    /**
     * Observes all registered sensors that aren't marked for deletion.
     * Use this for the main list screen.
     */
    @Query("""
        SELECT * FROM sensors 
        WHERE registered = 1 AND sync_status != 'DELETED' 
        ORDER BY lastSeenMillis DESC
    """)
    fun getAllRegisteredSensors(): Flow<List<SensorEntity>>

    /**
     * Optimized flow returning only addresses for high-frequency scan matching.
     */
    @Query("SELECT address FROM sensors WHERE registered = 1 AND sync_status != 'DELETED'")
    fun observeRegisteredAddresses(): Flow<List<String>>

    @Query("SELECT * FROM sensors WHERE address = :address LIMIT 1")
    suspend fun getSensor(address: String): SensorEntity?


    // =========================================================================
    // 🛢️ TANK OPERATIONS (UI & Domain)
    // =========================================================================

    /**
     * Standard insert for Tank settings.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTank(tank: TankEntity)


    @Query("SELECT * FROM tanks WHERE sensorAddress = :sensorAddress LIMIT 1")
    suspend fun getTank(sensorAddress: String): TankEntity?

    @Query("SELECT * FROM tanks WHERE sensorAddress = :sensorAddress")
    fun observeTank(sensorAddress: String): Flow<TankEntity?>

    @Query("SELECT * FROM tanks")
    fun observeAllTanks(): Flow<List<TankEntity>>

    @Query("DELETE FROM tanks WHERE sensorAddress = :sensorAddress")
    suspend fun deleteTank(sensorAddress: String)


    // =========================================================================
    // ☁️ CLOUD SYNC OPERATIONS (Worker Facing)
    // =========================================================================

    // --- Sensor Sync ---

    @Query("SELECT * FROM sensors WHERE sync_status != 'SYNCED'")
    suspend fun getUnsyncedSensors(): List<SensorEntity>

    @Query("UPDATE sensors SET sync_status = :status WHERE address = :address")
    suspend fun updateSyncStatus(address: String, status: SyncStatus)

    // upsert : for the Cloud-Sync methods. In your repository, when downloading from Firebase,
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSensor(sensor: SensorEntity)


    // --- Tank Sync ---

    /**
     * Fetches all tank configurations that need to be pushed to Firestore.
     */
    @Query("SELECT * FROM tanks WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedTanks(): List<TankEntity>

    @Query("UPDATE tanks SET syncStatus = :status WHERE sensorAddress = :address")
    suspend fun updateTankSyncStatus(address: String, status: SyncStatus)

    /**
     * Used during Cloud Download to force SYNCED status.
     */
    // upsert : for the Cloud-Sync methods. In your repository, when downloading from Firebase,
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTank(tank: TankEntity)


    // =========================================================================
    // 🗑️ DELETION LOGIC (Soft & Permanent)
    // =========================================================================

    /**
     * SOFT DELETE: Marks sensor as DELETED so Worker can remove it from Firestore.
     * The UI filters these out automatically in getAllRegisteredSensors().
     */
    @Query("UPDATE sensors SET sync_status = 'DELETED' WHERE address = :address")
    suspend fun markSensorForDeletion(address: String)

    /**
     * PERMANENT DELETE: Only called by SyncWorker AFTER Firestore confirmation.
     */
    @Query("DELETE FROM sensors WHERE address = :address")
    suspend fun deleteSensorPermanently(address: String)

    @Query("DELETE FROM sensors")
    suspend fun deleteAllSensors()

    @Query("DELETE FROM tanks")
    suspend fun deleteAllTanks()
}