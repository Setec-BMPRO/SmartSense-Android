package com.smartsense.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage

import com.smartsense.app.data.local.MopekaDatabase
import com.smartsense.app.data.local.dao.SensorDao
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.domain.firebase.AuthRepository
import com.smartsense.app.domain.firebase.AuthRepositoryImpl

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

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideAuthRepository(auth: FirebaseAuth): AuthRepository {
        return AuthRepositoryImpl(auth)
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
