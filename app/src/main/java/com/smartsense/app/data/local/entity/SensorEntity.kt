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
    val isRegistered: Boolean = false
)
