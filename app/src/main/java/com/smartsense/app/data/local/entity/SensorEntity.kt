package com.smartsense.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName
import com.smartsense.app.domain.model.Sensor

@Entity(tableName = "sensors")
data class SensorEntity(
    @PrimaryKey
    val address: String="",
    val name: String = "",
    var lastSeenMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "registered", defaultValue = "0")
    val registered: Boolean = false,

    // --- Sync Fields ---
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.SYNCED,

    @ColumnInfo(name = "last_modified_locally")
    val lastModifiedLocally: Long = System.currentTimeMillis(),

    // --- Last Known Reading (persisted for offline display) ---
    @ColumnInfo(name = "last_battery_voltage", defaultValue = "0")
    val lastBatteryVoltage: Float = 0f,

    @ColumnInfo(name = "last_rssi", defaultValue = "0")
    val lastRssi: Int = 0,

    @ColumnInfo(name = "last_quality", defaultValue = "0")
    val lastQuality: Int = 0,

    @ColumnInfo(name = "last_temperature_celsius", defaultValue = "0")
    val lastTemperatureCelsius: Float = 0f,

    @ColumnInfo(name = "last_raw_height_meters", defaultValue = "0")
    val lastRawHeightMeters: Double = 0.0,

    @ColumnInfo(name = "last_reading_timestamp", defaultValue = "0")
    val lastReadingTimestamp: Long = 0,

    @ColumnInfo(name = "last_sensor_type", defaultValue = "")
    val lastSensorType: String = "",
)

fun SensorEntity.toSensor(): Sensor = Sensor(
    address = this.address,
    name = this.name
)
enum class SyncStatus {
    SYNCED,    // Matches Cloud
    PENDING,   // Created/Updated locally, needs upload
    DELETED    // Marked for deletion locally, needs cloud removal
}