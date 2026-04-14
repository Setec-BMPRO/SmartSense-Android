package com.smartsense.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartsense.app.data.local.dao.SensorDao
import com.smartsense.app.data.local.entity.SensorEntity
import com.smartsense.app.data.local.entity.TankEntity

@Database(
    entities = [SensorEntity::class, TankEntity::class],
    version = 3,
    exportSchema = false
)
abstract class MopekaDatabase : RoomDatabase() {
    abstract fun sensorDao(): SensorDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to 'sensors' table
                db.execSQL("ALTER TABLE sensors ADD COLUMN registered INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sensors ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE sensors ADD COLUMN last_modified_locally INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to 'tanks' table
                db.execSQL("ALTER TABLE tanks ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE tanks ADD COLUMN last_modified_locally INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
