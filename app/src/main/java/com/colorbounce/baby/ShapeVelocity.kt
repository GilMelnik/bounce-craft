package com.colorbounce.baby

import kotlin.math.hypot

/**
 * Internal motion limits: caps launch speed from drags and keeps physics from exceeding a calm max speed.
 * Values are not exposed in settings by design.
 */
object ShapeVelocity {

    /** Maximum |v| for shapes (pixels per second). */
    const val MAX_SPEED_PX_PER_SEC = 1200f

    /** Scales pointer delta to launch velocity; kept low for gentle motion. */
    const val LAUNCH_DRAG_FACTOR = 8f

    fun clamp(vx: Float, vy: Float): Pair<Float, Float> {
        val speed = hypot(vx, vy)
        if (speed <= MAX_SPEED_PX_PER_SEC) return vx to vy
        val scale = MAX_SPEED_PX_PER_SEC / speed
        return vx * scale to vy * scale
    }
}
