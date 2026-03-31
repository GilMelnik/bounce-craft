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
        val themeMode = stringPreferencesKey("theme_mode")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val lockApp = booleanPreferencesKey("lock_app")
        val disableNotifications = booleanPreferencesKey("disable_notifications")
        val autoSpawnSeconds = intPreferencesKey("auto_spawn_seconds")
        val maxVelocity = intPreferencesKey("max_velocity")
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

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
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

    suspend fun updateAutoSpawnSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.autoSpawnSeconds] = seconds.coerceIn(0, 30) }
    }

    suspend fun updateMaxVelocity(velocity: Int) {
        context.dataStore.edit { it[Keys.maxVelocity] = velocity.coerceIn(100, 3000) }
    }

    private fun toSettings(prefs: Preferences): AppSettings {
        val shapeMode = runCatching {
            ShapeMode.valueOf(prefs[Keys.shapeMode] ?: ShapeMode.ALTERNATING.name)
        }.getOrDefault(ShapeMode.ALTERNATING)

        val themeMode = runCatching {
            ThemeMode.valueOf(prefs[Keys.themeMode] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        return AppSettings(
            shapeMode = shapeMode,
            shapeTimeoutSeconds = prefs[Keys.timeoutSeconds] ?: 10,
            maxShapes = prefs[Keys.maxShapes] ?: 24,
            themeMode = themeMode,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            lockApp = prefs[Keys.lockApp] ?: true,
            disableNotifications = prefs[Keys.disableNotifications] ?: false,
            autoSpawnInactivitySeconds = prefs[Keys.autoSpawnSeconds] ?: 8,
            maxVelocityPxPerSec = prefs[Keys.maxVelocity] ?: 1200
        )
    }
}
