package com.colorbounce.baby

enum class ShapeMode {
    CIRCLE_ONLY,
    RECTANGLE_ONLY,
    ALTERNATING,
    RANDOM
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AppSettings(
    val shapeMode: ShapeMode = ShapeMode.ALTERNATING,
    val shapeTimeoutSeconds: Int = 10,
    val maxShapes: Int = 24,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val keepScreenOn: Boolean = true,
    val lockApp: Boolean = true,
    val disableNotifications: Boolean = false,
    /** After this many seconds of inactivity, a new shape is spawned automatically. */
    val autoSpawnInactivitySeconds: Int = 8
)
