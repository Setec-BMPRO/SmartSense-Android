package com.smartsense.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.smartsense.app.domain.model.* // Assuming your Enums are here
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
        val SCAN_INTERVAL = intPreferencesKey("scan_interval_value") // Store the Int value
        val APP_THEME = stringPreferencesKey("app_theme")
        val SORT_PREFERENCE = stringPreferencesKey("sort_preference")

        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val UPLOAD_SENSOR_DATA = booleanPreferencesKey("upload_sensor_data")
        val GROUP_FILTER_ENABLED = booleanPreferencesKey("group_filter_enabled")
        val DEVICE_SEARCH_FILTER_ENABLED = booleanPreferencesKey("device_search_filter_enabled")
    }

    // --- Enum-based Flow Observables ---

    val unitSystem: Flow<UnitSystem> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.UNIT_SYSTEM] ?: UnitSystem.METRIC.name
        UnitSystem.entries.find { it.name == name } ?: UnitSystem.METRIC
    }

    val scanInterval: Flow<ScanIntervals> = context.dataStore.data.map { prefs ->
        val value = prefs[Keys.SCAN_INTERVAL] ?: ScanIntervals.default().value
        ScanIntervals.entries.find { it.value == value } ?: ScanIntervals.default()
    }

    val appTheme: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.APP_THEME] ?: AppTheme.SYSTEM.name
        AppTheme.entries.find { it.name == name } ?: AppTheme.SYSTEM
    }

    val sortPreference: Flow<SortPreference> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.SORT_PREFERENCE] ?: SortPreference.NAME.name
        SortPreference.entries.find { it.name == name } ?: SortPreference.NAME
    }

    // --- Boolean Flow Observables ---

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }
    val uploadSensorData: Flow<Boolean> = context.dataStore.data.map { it[Keys.UPLOAD_SENSOR_DATA] ?: true }
    val groupFilterEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.GROUP_FILTER_ENABLED] ?: false }
    val deviceSearchFilterEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEVICE_SEARCH_FILTER_ENABLED] ?: false }

    // --- Update Functions ---

    suspend fun setUnitSystem(unitSystem: UnitSystem) {
        context.dataStore.edit { it[Keys.UNIT_SYSTEM] = unitSystem.name }
    }

    suspend fun setScanInterval(interval: ScanIntervals) {
        context.dataStore.edit { it[Keys.SCAN_INTERVAL] = interval.value }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.APP_THEME] = theme.name }
    }

    suspend fun setSortPreference(sort: SortPreference) {
        context.dataStore.edit { it[Keys.SORT_PREFERENCE] = sort.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setUploadSensorData(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UPLOAD_SENSOR_DATA] = enabled }
    }

    suspend fun setGroupFilterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GROUP_FILTER_ENABLED] = enabled }
    }

    suspend fun setDeviceSearchFilterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEVICE_SEARCH_FILTER_ENABLED] = enabled }
    }
}