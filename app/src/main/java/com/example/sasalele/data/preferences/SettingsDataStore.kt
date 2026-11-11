package com.example.sasalele.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val AUTO_COPY = booleanPreferencesKey("auto_copy")
        val ENABLE_REMINDERS = booleanPreferencesKey("enable_reminders")
        val FLOATING_WINDOW_Y = intPreferencesKey("floating_window_y")
        val LAST_RUN_TIME = longPreferencesKey("last_run_time")
        val LAST_UNBIND_TIME = longPreferencesKey("last_unbind_time")
        
        val FIRST_WAIT_TIME = longPreferencesKey("first_wait_time")
        val REMINDER_INTERVAL = longPreferencesKey("reminder_interval")
        val MAX_RETRY_COUNT = intPreferencesKey("max_retry_count")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATE_ENABLED = booleanPreferencesKey("vibrate_enabled")
        
        val BOUND_CLIENT_JSON = stringPreferencesKey("bound_client_json")
    }

    val autoCopyFlow: Flow<Boolean> = context.dataStore.data.map { it[AUTO_COPY] ?: true }
    val enableRemindersFlow: Flow<Boolean> = context.dataStore.data.map { it[ENABLE_REMINDERS] ?: false }
    val floatingWindowYFlow: Flow<Int> = context.dataStore.data.map { it[FLOATING_WINDOW_Y] ?: 0 }
    
    val firstWaitTimeFlow: Flow<Long> = context.dataStore.data.map { it[FIRST_WAIT_TIME] ?: 5000L }
    val reminderIntervalFlow: Flow<Long> = context.dataStore.data.map { it[REMINDER_INTERVAL] ?: 15000L }
    val maxRetryCountFlow: Flow<Int> = context.dataStore.data.map { it[MAX_RETRY_COUNT] ?: 3 }
    val lastUnbindTimeFlow: Flow<Long> = context.dataStore.data.map { it[LAST_UNBIND_TIME] ?: 0L }
    val boundClientJsonFlow: Flow<String?> = context.dataStore.data.map { it[BOUND_CLIENT_JSON] }
    val soundEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[SOUND_ENABLED] ?: true }
    val vibrateEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[VIBRATE_ENABLED] ?: true }

    suspend fun setAutoCopy(value: Boolean) { context.dataStore.edit { it[AUTO_COPY] = value } }
    suspend fun setEnableReminders(value: Boolean) { context.dataStore.edit { it[ENABLE_REMINDERS] = value } }
    suspend fun setFloatingWindowY(value: Int) { context.dataStore.edit { it[FLOATING_WINDOW_Y] = value } }
    suspend fun setFirstWaitTime(value: Long) { context.dataStore.edit { it[FIRST_WAIT_TIME] = value } }
    suspend fun setReminderInterval(value: Long) { context.dataStore.edit { it[REMINDER_INTERVAL] = value } }
    suspend fun setMaxRetryCount(value: Int) { context.dataStore.edit { it[MAX_RETRY_COUNT] = value } }
    suspend fun setLastUnbindTime(value: Long) { context.dataStore.edit { it[LAST_UNBIND_TIME] = value } }
    suspend fun setBoundClientJson(value: String?) { 
        context.dataStore.edit { 
            if (value == null) {
                it.remove(BOUND_CLIENT_JSON)
            } else {
                it[BOUND_CLIENT_JSON] = value 
            }
        } 
    }
}
