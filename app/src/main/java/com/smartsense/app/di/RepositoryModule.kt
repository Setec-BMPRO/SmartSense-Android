package com.smartsense.app.di

import com.smartsense.app.data.repository.Sensor1Repository
import com.smartsense.app.data.repository.SensorRepository
import com.smartsense.app.data.repository.SensorRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSensorRepository(impl: SensorRepositoryImpl): SensorRepository

}
