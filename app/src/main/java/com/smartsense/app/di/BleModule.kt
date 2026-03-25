package com.smartsense.app.di

import com.smartsense.app.data.ble.BleScanner
import com.smartsense.app.data.ble.BleScannerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {

    @Binds
    @Singleton
    abstract fun bindBleScanner(impl: BleScannerImpl): BleScanner
}
