package com.colorbounce.baby

import androidx.compose.ui.graphics.Color

enum class ShapeType {
    CIRCLE, RECTANGLE, TRIANGLE, ARCH, STAR, HEART, DIAMOND
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
    val lastInteractionMillis: Long,
    /** If true, shape is static in physics and only moves when the user drags it. */
    val isPinned: Boolean = false,
    /** If true, shape is not removed by shape timeout rules. */
    val isImmortal: Boolean = false,
    /** If true, this shape's hue does not animate while the user is dragging it (when ruler hue lock is off). */
    val freezeHueWhileDragging: Boolean = false,
    /**
     * When creation ruler hue lock is on, this shape may still animate hue while dragged.
     * Default false: follow the ruler lock for every shape until the user opts out in the shape menu.
     */
    val exemptFromGlobalHueLock: Boolean = false,
    /** When ruler pins all shapes, this shape stays unpinned until cleared by ruler. */
    val exemptFromGlobalPin: Boolean = false,
    /** When ruler makes all shapes immortal, this shape still times out until cleared by ruler. */
    val exemptFromGlobalImmortal: Boolean = false
)

val GameShape.color: Color
    get() = Color.hsv(hue.normalizeHue(), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
