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

    // --- SENSOR OPERATIONS (UI Facing) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSensor(sensor: SensorEntity)

    // CRITICAL: Filter out DELETED status so they vanish from the UI immediately
    @Query("SELECT * FROM sensors WHERE registered = 1 AND sync_status != 'DELETED' ORDER BY lastSeenMillis DESC")
    fun getAllRegisteredSensors(): Flow<List<SensorEntity>>

    @Query("SELECT address FROM sensors WHERE registered = 1 AND sync_status != 'DELETED'")
    fun observeRegisteredAddresses(): Flow<List<String>>

    @Query("SELECT * FROM sensors WHERE address = :address")
    suspend fun getSensor(address: String): SensorEntity?


    // --- SOFT DELETE LOGIC ---

    // Instead of deleting from DB, we mark it so the Worker sees it
    @Query("UPDATE sensors SET sync_status = 'DELETED' WHERE address = :address")
    suspend fun markSensorForDeletion(address: String)

    // The Worker calls this ONLY after Firestore confirms the delete
    @Query("DELETE FROM sensors WHERE address = :address")
    suspend fun deleteSensorPermanently(address: String)

    @Query("DELETE FROM sensors")
    suspend fun deleteAllSensors()

    @Query("DELETE FROM tanks")
    suspend fun deleteAllTanks()


    // --- TANK OPERATIONS ---
    // (Note: If you want tanks to sync too, they usually need a sync_status as well)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTank(tank: TankEntity)

    @Update
    suspend fun updateTank(tank: TankEntity)

    @Query("SELECT * FROM tanks WHERE sensorAddress = :sensorAddress")
    suspend fun getTank(sensorAddress: String): TankEntity?

    @Query("SELECT * FROM tanks WHERE sensorAddress = :sensorAddress")
    fun observeTank(sensorAddress: String): Flow<TankEntity?>

    @Query("SELECT * FROM tanks")
    fun observeAllTanks(): Flow<List<TankEntity>>

    @Query("DELETE FROM tanks WHERE sensorAddress = :sensorAddress")
    suspend fun deleteTank(sensorAddress: String)


    // --- SYNC WORKER OPERATIONS ---

    // Now fetches both PENDING (updates) and DELETED (removals)
    @Query("SELECT * FROM sensors WHERE sync_status != 'SYNCED'")
    suspend fun getUnsyncedSensors(): List<SensorEntity>

    @Query("UPDATE sensors SET sync_status = :status WHERE address = :address")
    suspend fun updateSyncStatus(address: String, status: SyncStatus)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSensor(sensor: SensorEntity)
}