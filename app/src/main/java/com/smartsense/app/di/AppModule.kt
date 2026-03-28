package com.smartsense.app.di

import android.content.Context
import androidx.room.Room
import com.smartsense.app.data.local.MopekaDatabase
import com.smartsense.app.data.local.dao.SensorDao
import com.smartsense.app.data.preferences.UserPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideUserPreferences(
        @ApplicationContext context: Context
    ): UserPreferences = UserPreferences(context)


    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MopekaDatabase {
        return Room.databaseBuilder(
            context,
            MopekaDatabase::class.java,
            "mopeka_db"
        )
            .addMigrations(MopekaDatabase.MIGRATION_1_2, MopekaDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideSensorDao(database: MopekaDatabase): SensorDao {
        return database.sensorDao()
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object CoroutineModule {

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

}
