package com.colorbounce.baby

enum class ShapeMode {
    CIRCLE_ONLY,
    RECTANGLE_ONLY,
    ALTERNATING,
    RANDOM
}

data class AppSettings(
    val shapeMode: ShapeMode = ShapeMode.ALTERNATING,
    val shapeTimeoutSeconds: Int = 10,
    val maxShapes: Int = 24,
    val keepScreenOn: Boolean = true,
    val lockApp: Boolean = true,
    val disableNotifications: Boolean = false
)
