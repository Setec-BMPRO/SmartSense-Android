package com.smartsense.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smartsense.app.data.local.entity.SensorEntity
import com.smartsense.app.data.local.entity.TankEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {
    // Sensor operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSensor(sensor: SensorEntity)

    @Query("SELECT * FROM sensors WHERE is_registered = 1 ORDER BY lastSeenMillis DESC")
    fun getAllRegisteredSensors(): Flow<List<SensorEntity>>

    @Query("SELECT address FROM sensors WHERE is_registered = 1")
    fun observeRegisteredAddresses(): Flow<List<String>>

    @Query("SELECT * FROM sensors WHERE address = :address")
    suspend fun getSensor(address: String): SensorEntity?

    @Query("DELETE FROM sensors WHERE address = :address")
    suspend fun deleteSensor(address: String)

    @Query("DELETE FROM sensors")
    suspend fun deleteAllSensors()

    @Query("DELETE FROM tanks")
    suspend fun deleteAllTanks()


    // Tank operations
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
}
