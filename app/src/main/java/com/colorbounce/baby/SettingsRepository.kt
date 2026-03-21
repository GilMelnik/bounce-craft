package com.colorbounce.baby

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "colorbounce_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val shapeMode = stringPreferencesKey("shape_mode")
        val timeoutSeconds = intPreferencesKey("timeout_seconds")
        val maxShapes = intPreferencesKey("max_shapes")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val lockApp = booleanPreferencesKey("lock_app")
        val disableNotifications = booleanPreferencesKey("disable_notifications")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map(::toSettings)

    suspend fun updateShapeMode(mode: ShapeMode) {
        context.dataStore.edit { it[Keys.shapeMode] = mode.name }
    }

    suspend fun updateTimeoutSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.timeoutSeconds] = seconds.coerceIn(3, 60) }
    }

    suspend fun updateMaxShapes(maxShapes: Int) {
        context.dataStore.edit { it[Keys.maxShapes] = maxShapes.coerceIn(4, 80) }
    }

    suspend fun updateKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.keepScreenOn] = enabled }
    }

    suspend fun updateLockApp(enabled: Boolean) {
        context.dataStore.edit { it[Keys.lockApp] = enabled }
    }

    suspend fun updateDisableNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.disableNotifications] = enabled }
    }

    private fun toSettings(prefs: Preferences): AppSettings {
        val shapeMode = runCatching {
            ShapeMode.valueOf(prefs[Keys.shapeMode] ?: ShapeMode.ALTERNATING.name)
        }.getOrDefault(ShapeMode.ALTERNATING)

        return AppSettings(
            shapeMode = shapeMode,
            shapeTimeoutSeconds = prefs[Keys.timeoutSeconds] ?: 10,
            maxShapes = prefs[Keys.maxShapes] ?: 24,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            lockApp = prefs[Keys.lockApp] ?: true,
            disableNotifications = prefs[Keys.disableNotifications] ?: false
        )
    }
}
