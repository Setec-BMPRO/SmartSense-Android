package com.smartsense.app.demo

import android.content.Context
import com.smartsense.app.data.ble.ScannedSensor
import kotlinx.coroutines.flow.Flow

/**
 * Release-build stand-in for the debug-only SmartSensePro demo injector
 * (`src/debug/.../DemoInjector.kt`). Same API that `BleManager` calls, but demo mode can
 * never be active, so release builds always scan real Bluetooth sensors.
 */
object DemoInjector {

    @JvmStatic
    fun init(@Suppress("UNUSED_PARAMETER") context: Context?) = Unit

    val isActive: Boolean
        get() = false

    @JvmStatic
    fun scanFlowOrNull(): Flow<ScannedSensor>? = null
}
