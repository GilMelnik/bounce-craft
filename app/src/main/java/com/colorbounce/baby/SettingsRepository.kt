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
        val selectedShapes = stringPreferencesKey("selected_shapes")
        val shapeSelectionMode = stringPreferencesKey("shape_selection_mode")
        val timeoutSeconds = intPreferencesKey("timeout_seconds")
        val shapeTimeoutImmortal = booleanPreferencesKey("shape_timeout_immortal")
        val maxShapes = intPreferencesKey("max_shapes")
        val themeMode = stringPreferencesKey("theme_mode")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val lockApp = booleanPreferencesKey("lock_app")
        val disableNotifications = booleanPreferencesKey("disable_notifications")
        val autoSpawnSeconds = intPreferencesKey("auto_spawn_seconds")
        val maxVelocity = intPreferencesKey("max_velocity")
        val tutorialSeen = booleanPreferencesKey("tutorial_seen")
        val showPlayGameRuler = booleanPreferencesKey("show_play_game_ruler")
        val doubleTapShapeMenu = booleanPreferencesKey("double_tap_shape_menu")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map(::toSettings)

    suspend fun updateTimeoutSeconds(seconds: Int) {
        context.dataStore.edit {
            it[Keys.timeoutSeconds] = seconds.coerceIn(3, 60)
            it[Keys.shapeTimeoutImmortal] = false
        }
    }

    suspend fun updateShapeTimeoutImmortal(immortal: Boolean) {
        context.dataStore.edit { it[Keys.shapeTimeoutImmortal] = immortal }
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

    suspend fun updateSelectedShapes(shapes: Set<ShapeType>) {
        if (shapes.isEmpty()) return // Prevent empty set
        context.dataStore.edit { it[Keys.selectedShapes] = shapes.joinToString(",") { it -> it.name } }
    }

    suspend fun updateShapeSelectionMode(mode: ShapeSelectionMode) {
        context.dataStore.edit { it[Keys.shapeSelectionMode] = mode.name }
    }

    suspend fun markTutorialSeen() {
        context.dataStore.edit { it[Keys.tutorialSeen] = true }
    }

    suspend fun updateShowPlayGameRuler(enabled: Boolean) {
        context.dataStore.edit { it[Keys.showPlayGameRuler] = enabled }
    }

    suspend fun updateDoubleTapShapeMenu(enabled: Boolean) {
        context.dataStore.edit { it[Keys.doubleTapShapeMenu] = enabled }
    }

    private fun toSettings(prefs: Preferences): AppSettings {
        val selectedShapes = runCatching {
            val str = prefs[Keys.selectedShapes]
                ?: "CIRCLE,RECTANGLE,TRIANGLE,ARCH,STAR,HEART,DIAMOND"
            str.split(",").mapNotNull { runCatching { ShapeType.valueOf(it) }.getOrNull() }.toSet()
        }.getOrDefault(
            setOf(
                ShapeType.CIRCLE,
                ShapeType.RECTANGLE,
                ShapeType.TRIANGLE,
                ShapeType.ARCH,
                ShapeType.STAR,
                ShapeType.HEART,
                ShapeType.DIAMOND
            )
        ).takeIf { it.isNotEmpty() } ?: setOf(ShapeType.CIRCLE)

        val shapeSelectionMode = runCatching {
            ShapeSelectionMode.valueOf(prefs[Keys.shapeSelectionMode] ?: ShapeSelectionMode.ALTERNATE.name)
        }.getOrDefault(ShapeSelectionMode.ALTERNATE)

        val themeMode = runCatching {
            ThemeMode.valueOf(prefs[Keys.themeMode] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        val storedSeconds = prefs[Keys.timeoutSeconds] ?: 10
        val immortalExplicit = prefs[Keys.shapeTimeoutImmortal]
        val shapeTimeoutImmortal = when {
            immortalExplicit != null -> immortalExplicit
            storedSeconds >= 60 -> true
            else -> false
        }
        val shapeTimeoutSeconds = storedSeconds.coerceIn(3, 60)

        return AppSettings(
            selectedShapes = selectedShapes,
            shapeSelectionMode = shapeSelectionMode,
            shapeTimeoutImmortal = shapeTimeoutImmortal,
            shapeTimeoutSeconds = shapeTimeoutSeconds,
            maxShapes = prefs[Keys.maxShapes] ?: 24,
            themeMode = themeMode,
            keepScreenOn = prefs[Keys.keepScreenOn] ?: true,
            lockApp = prefs[Keys.lockApp] ?: true,
            disableNotifications = prefs[Keys.disableNotifications] ?: false,
            autoSpawnInactivitySeconds = prefs[Keys.autoSpawnSeconds] ?: 8,
            maxVelocityPxPerSec = prefs[Keys.maxVelocity] ?: 1200,
            tutorialSeen = prefs[Keys.tutorialSeen] ?: false,
            showPlayGameRuler = prefs[Keys.showPlayGameRuler] ?: false,
            doubleTapShapeMenu = prefs[Keys.doubleTapShapeMenu] ?: false
        )
    }
}
