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
    val color: Color,
    val lastInteractionMillis: Long
)
