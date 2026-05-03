package com.colorbounce.baby

enum class ShapeMode {
    CIRCLE_ONLY,
    RECTANGLE_ONLY,
    ALTERNATING,
    RANDOM
}

enum class ShapeSelectionMode {
    ALTERNATE,
    RANDOM
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AppSettings(
    val shapeMode: ShapeMode = ShapeMode.ALTERNATING,
    val selectedShapes: Set<ShapeType> = setOf(ShapeType.CIRCLE, ShapeType.RECTANGLE, ShapeType.TRIANGLE, ShapeType.ARCH),
    val shapeSelectionMode: ShapeSelectionMode = ShapeSelectionMode.ALTERNATE,
    val shapeTimeoutSeconds: Int = 10,
    val maxShapes: Int = 24,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val keepScreenOn: Boolean = true,
    val lockApp: Boolean = true,
    val disableNotifications: Boolean = false,
    /** After this many seconds of inactivity, a new shape is spawned automatically. */
    val autoSpawnInactivitySeconds: Int = 8,
    val maxVelocityPxPerSec: Int = 1200,
    /** Tutorial has been shown to the user. */
    val tutorialSeen: Boolean = false
)

/** Accessibility label for shape pool toggles (settings and creation ruler). */
fun shapePoolChipDescription(type: ShapeType, included: Boolean): String {
    val name = when (type) {
        ShapeType.CIRCLE -> "Circles"
        ShapeType.RECTANGLE -> "Rectangles"
        ShapeType.TRIANGLE -> "Triangles"
        ShapeType.ARCH -> "Arches"
    }
    return if (included) {
        "$name in pool. Tap to remove."
    } else {
        "$name not in pool. Tap to add."
    }
}
