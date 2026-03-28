package com.smartsense.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartsense.app.domain.model.UnitSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val SCAN_INTERVAL = intPreferencesKey("scan_interval")
        val APP_THEME = stringPreferencesKey("app_theme")
    }

    val unitSystem: Flow<UnitSystem> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.UNIT_SYSTEM]) {
            "IMPERIAL" -> UnitSystem.IMPERIAL
            else -> UnitSystem.METRIC
        }
    }

    val scanInterval: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.SCAN_INTERVAL] ?: 5
    }

    val appTheme: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_THEME] ?: "System"
    }

    suspend fun setUnitSystem(unitSystem: UnitSystem) {
        context.dataStore.edit { prefs ->
            prefs[Keys.UNIT_SYSTEM] = unitSystem.name
        }
    }

    suspend fun setScanInterval(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCAN_INTERVAL] = seconds
        }
    }

    suspend fun setAppTheme(theme: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_THEME] = theme
        }
    }
}
