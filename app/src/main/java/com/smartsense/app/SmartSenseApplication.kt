package com.smartsense.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.smartsense.app.data.preferences.UserPreferences
import com.smartsense.app.domain.model.AppLanguage
import com.smartsense.app.domain.model.AppTheme
import com.smartsense.app.util.LocaleManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject


@HiltAndroidApp
class SmartSenseApplication : Application() , Configuration.Provider{

    @Inject
    lateinit var userPreferences: UserPreferences

    // Hilt will inject the custom factory here
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory) // This bridges Hilt and WorkManager
            .setMinimumLoggingLevel(android.util.Log.DEBUG) // Helpful for your debug logs!
            .build()

    override fun onCreate() {
        super.onCreate()

        // Apply theme + language synchronously at startup to prevent double-recreation flicker.
        val theme = runBlocking { userPreferences.appTheme.first() }
        applyTheme(theme.displayName)

        bootstrapLanguage()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    /**
     * Decide which UI language to use on this launch.
     *
     * - **First run**: read the device's primary locale and pick the closest [AppLanguage]
     *   we ship translations for (falls back to English). Persist the choice and mark
     *   first-run complete so the user can override it later without us clobbering them.
     * - **Subsequent runs**: just apply whatever the user has stored (which may be
     *   [AppLanguage.SYSTEM] meaning "follow OS").
     *
     * AppCompat-1.6 also persists locales internally; the DataStore mirror keeps the
     * picker UI aligned without a second read path.
     */
    private fun bootstrapLanguage() {
        val firstRunDone = runBlocking { userPreferences.firstRunCompleted.first() }
        if (!firstRunDone) {
            val deviceTag = LocaleManager.deviceLocaleTag()
            val match = AppLanguage.bestMatchForLocale(deviceTag)
            Timber.tag("Locale").i("First run: device=$deviceTag → app=$match")
            // Persist asynchronously — applying the locale doesn't depend on the write completing.
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                userPreferences.setAppLanguage(match)
                userPreferences.setFirstRunCompleted(true)
            }
            LocaleManager.apply(match)
        } else {
            val saved = runBlocking { userPreferences.appLanguage.first() }
            LocaleManager.apply(saved)
        }
    }


    companion object {
        fun applyTheme(theme: String) {
            val mode = when (theme) {
                AppTheme.LIGHT.displayName -> AppCompatDelegate.MODE_NIGHT_NO
                AppTheme.DARK.displayName -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (AppCompatDelegate.getDefaultNightMode() != mode) {
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
    }
}
