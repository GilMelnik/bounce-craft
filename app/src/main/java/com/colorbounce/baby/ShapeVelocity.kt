package com.colorbounce.baby

import kotlin.math.hypot

/**
 * Internal motion limits: caps launch speed from drags and keeps physics from exceeding a calm max speed.
 * Values are not exposed in settings by design.
 */
object ShapeVelocity {

    /** Scales pointer delta to launch velocity; kept low for gentle motion. */
    const val LAUNCH_DRAG_FACTOR = 8f

    fun clamp(vx: Float, vy: Float, maxSpeed: Float = 1200f): Pair<Float, Float> {
        val speed = hypot(vx, vy)
        if (speed <= maxSpeed) return vx to vy
        val scale = maxSpeed / speed
        return vx * scale to vy * scale
    }
}
