package com.colorbounce.baby

import androidx.compose.ui.graphics.Color

enum class ShapeType {
    CIRCLE, RECTANGLE
}

data class GameShape(
    val id: Long,
    val type: ShapeType,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val vx: Float,
    val vy: Float,
    /** Current hue in degrees. */
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val lastInteractionMillis: Long
)

val GameShape.color: Color
    get() = Color.hsv(hue.normalizeHue(), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
