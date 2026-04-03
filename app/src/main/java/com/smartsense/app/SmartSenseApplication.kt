package com.smartsense.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.domain.model.AppTheme

import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


@HiltAndroidApp
class SmartSenseApplication : Application() , Configuration.Provider{

    @Inject
    lateinit var userPreferences: UserPreferences

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Hilt will inject the custom factory here
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory) // This bridges Hilt and WorkManager
            .setMinimumLoggingLevel(android.util.Log.DEBUG) // Helpful for your debug logs!
            .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            val theme = userPreferences.appTheme.first()
            applyTheme(theme.displayName)
        }
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }


    companion object {
        fun applyTheme(theme: String) {
            when (theme) {
                AppTheme.LIGHT.displayName -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                AppTheme.DARK.displayName-> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

            }
        }
    }
}
