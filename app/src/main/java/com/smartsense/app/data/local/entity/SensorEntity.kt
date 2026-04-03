package com.smartsense.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val syncStatus: SyncStatus = SyncStatus.PENDING,

    @ColumnInfo(name = "last_modified_locally")
    val lastModifiedLocally: Long = System.currentTimeMillis(),

)

enum class SyncStatus {
    SYNCED,    // Matches Cloud
    PENDING,   // Created/Updated locally, needs upload
    DELETED    // Marked for deletion locally, needs cloud removal
}