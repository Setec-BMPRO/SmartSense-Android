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
    val lastSeenMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "registered", defaultValue = "0")
    val registered: Boolean = false,

    // --- Sync Fields ---
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.SYNCED,

    @ColumnInfo(name = "last_modified_locally")
    val lastModifiedLocally: Long = System.currentTimeMillis(),


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