package com.smartsense.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
                db.execSQL("ALTER TABLE sensors ADD COLUMN is_registered INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tanks ADD COLUMN region TEXT NOT NULL DEFAULT 'AUSTRALIA'")
                db.execSQL("ALTER TABLE tanks ADD COLUMN levelUnit TEXT NOT NULL DEFAULT 'PERCENT'")
                db.execSQL("ALTER TABLE tanks ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE tanks ADD COLUMN notificationFrequency TEXT NOT NULL DEFAULT 'EVERY_12_HOURS'")
            }
        }
    }
}
