package com.smartsense.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartsense.app.domain.model.AppTheme
import com.smartsense.app.domain.model.ScanIntervals
import com.smartsense.app.domain.model.SortPreference
import com.smartsense.app.domain.model.UnitSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "UserPreferences"

        // --- Default Values ---
        val DEFAULT_UNIT_SYSTEM = UnitSystem.METRIC
        val DEFAULT_SCAN_INTERVAL = ScanIntervals.default()
        val DEFAULT_APP_THEME = AppTheme.SYSTEM
        val DEFAULT_SORT_PREFERENCE = SortPreference.NAME
        const val DEFAULT_NOTIFICATIONS_ENABLED = true
        const val DEFAULT_UPLOAD_SENSOR_DATA = true
        const val DEFAULT_GROUP_FILTER_ENABLED = false
        const val DEFAULT_DEVICE_SEARCH_FILTER_ENABLED = false
        const val DEFAULT_IS_SIGNED_IN = false
    }

    private object Keys {
        val UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val SCAN_INTERVAL = intPreferencesKey("scan_interval_value")
        val APP_THEME = stringPreferencesKey("app_theme")
        val SORT_PREFERENCE = stringPreferencesKey("sort_preference")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val UPLOAD_SENSOR_DATA = booleanPreferencesKey("upload_sensor_data")
        val GROUP_FILTER_ENABLED = booleanPreferencesKey("group_filter_enabled")
        val DEVICE_SEARCH_FILTER_ENABLED = booleanPreferencesKey("device_search_filter_enabled")
        val IS_SIGNED_IN = booleanPreferencesKey("is_signed_in")
        val USER_EMAIL = stringPreferencesKey("user_email")

        // Keys for Tank Alert States
        fun lastLevelKey(address: String) = intPreferencesKey("last_level_$address")
        fun lastTimeKey(address: String) = intPreferencesKey("last_time_$address")
    }

    // -------------------------------------------------------------------------
    // 📡 Enum-based Flow Observables
    // -------------------------------------------------------------------------

    val unitSystem: Flow<UnitSystem> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.UNIT_SYSTEM] ?: DEFAULT_UNIT_SYSTEM.name
        UnitSystem.entries.find { it.name == name } ?: DEFAULT_UNIT_SYSTEM
    }

    val scanInterval: Flow<ScanIntervals> = context.dataStore.data.map { prefs ->
        val value = prefs[Keys.SCAN_INTERVAL] ?: DEFAULT_SCAN_INTERVAL.value
        ScanIntervals.entries.find { it.value == value } ?: DEFAULT_SCAN_INTERVAL
    }

    val appTheme: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.APP_THEME] ?: DEFAULT_APP_THEME.name
        AppTheme.entries.find { it.name == name } ?: DEFAULT_APP_THEME
    }

    val sortPreference: Flow<SortPreference> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.SORT_PREFERENCE] ?: DEFAULT_SORT_PREFERENCE.name
        SortPreference.entries.find { it.name == name } ?: DEFAULT_SORT_PREFERENCE
    }

    // -------------------------------------------------------------------------
    // 📡 Boolean Flow Observables
    // -------------------------------------------------------------------------

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATIONS_ENABLED] ?: DEFAULT_NOTIFICATIONS_ENABLED }
    val uploadSensorData: Flow<Boolean> = context.dataStore.data.map { it[Keys.UPLOAD_SENSOR_DATA] ?: DEFAULT_UPLOAD_SENSOR_DATA }
    val groupFilterEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.GROUP_FILTER_ENABLED] ?: DEFAULT_GROUP_FILTER_ENABLED }
    val deviceSearchFilterEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEVICE_SEARCH_FILTER_ENABLED] ?: DEFAULT_DEVICE_SEARCH_FILTER_ENABLED }
    val isSignedIn: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_SIGNED_IN] ?: DEFAULT_IS_SIGNED_IN }

    val userEmail: Flow<String?> = context.dataStore.data.map { it[Keys.USER_EMAIL] }

    // -------------------------------------------------------------------------
    // ✍️ Update Functions (Setters)
    // -------------------------------------------------------------------------

    suspend fun setUnitSystem(unitSystem: UnitSystem) {
        Timber.tag(TAG).d("Setting UnitSystem: ${unitSystem.name}")
        context.dataStore.edit { it[Keys.UNIT_SYSTEM] = unitSystem.name }
    }

    suspend fun setScanInterval(interval: ScanIntervals) {
        Timber.tag(TAG).d("Setting ScanInterval: ${interval.name} (${interval.value}ms)")
        context.dataStore.edit { it[Keys.SCAN_INTERVAL] = interval.value }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        Timber.tag(TAG).d("Setting AppTheme: ${theme.name}")
        context.dataStore.edit { it[Keys.APP_THEME] = theme.name }
    }

    suspend fun setSortPreference(sort: SortPreference) {
        Timber.tag(TAG).d("Setting SortPreference: ${sort.name}")
        context.dataStore.edit { it[Keys.SORT_PREFERENCE] = sort.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        Timber.tag(TAG).d("Setting NotificationsEnabled: $enabled")
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setUploadSensorData(enabled: Boolean) {
        Timber.tag(TAG).d("Setting UploadSensorData: $enabled")
        context.dataStore.edit { it[Keys.UPLOAD_SENSOR_DATA] = enabled }
    }

    suspend fun setGroupFilterEnabled(enabled: Boolean) {
        Timber.tag(TAG).d("Setting GroupFilterEnabled: $enabled")
        context.dataStore.edit { it[Keys.GROUP_FILTER_ENABLED] = enabled }
    }

    suspend fun setDeviceSearchFilterEnabled(enabled: Boolean) {
        Timber.tag(TAG).d("Setting DeviceSearchFilterEnabled: $enabled")
        context.dataStore.edit { it[Keys.DEVICE_SEARCH_FILTER_ENABLED] = enabled }
    }

    suspend fun setIsSignedIn(isSignedIn: Boolean) {
        Timber.tag(TAG).i("Setting IsSignedIn status: $isSignedIn")
        context.dataStore.edit { it[Keys.IS_SIGNED_IN] = isSignedIn }
    }

    suspend fun setUserEmail(email: String?) {
        Timber.tag(TAG).d("Setting UserEmail: $email")
        context.dataStore.edit { prefs ->
            if (email == null) {
                prefs.remove(Keys.USER_EMAIL)
            } else {
                prefs[Keys.USER_EMAIL] = email
            }
        }
    }

    // -------------------------------------------------------------------------
    // 🔔 Tank Alert States (using DataStore)
    // -------------------------------------------------------------------------

    fun getLastAlertLevel(address: String): Flow<Int> = context.dataStore.data.map { 
        it[Keys.lastLevelKey(address)] ?: -1 
    }

    fun getLastAlertTime(address: String): Flow<Long> = context.dataStore.data.map { 
        (it[Keys.lastTimeKey(address)] ?: 0).toLong()
    }

    suspend fun saveAlertState(address: String, level: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.lastLevelKey(address)] = level
            prefs[Keys.lastTimeKey(address)] = System.currentTimeMillis().toInt() // DataStore int limit
        }
    }

    suspend fun resetAlertState(address: String) {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.lastLevelKey(address))
            prefs.remove(Keys.lastTimeKey(address))
        }
    }
}
