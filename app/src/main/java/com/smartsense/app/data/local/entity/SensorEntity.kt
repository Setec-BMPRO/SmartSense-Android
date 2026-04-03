package com.smartsense.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensors")
data class SensorEntity(
    @PrimaryKey
    val address: String,
    val name: String = "",
    val lastSeenMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_registered", defaultValue = "0")
    val isRegistered: Boolean = false,

    // --- Sync Fields ---
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.SYNCED,

    @ColumnInfo(name = "last_modified_locally")
    val lastModifiedLocally: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "remote_id")
    val remoteId: String? = null // Firestore Document ID if different from address
)

enum class SyncStatus {
    SYNCED,    // Matches Cloud
    PENDING,   // Created/Updated locally, needs upload
    DELETED    // Marked for deletion locally, needs cloud removal
}