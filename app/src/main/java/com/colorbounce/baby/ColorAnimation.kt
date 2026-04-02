package com.colorbounce.baby

/**
 * Hue cycling logic for shapes.
 */
object ShapeColorAnimator {

    /** Degrees per second to advance hue in the HSV spectrum. */
    const val HUE_STEP_DEG_PER_SEC = 120f

    /**
     * Advances hue forward through the spectrum [0, 360]°, wrapping back to 0° seamlessly.
     */
    fun stepHue(hue: Float, deltaSeconds: Float): Float {
        val nextHue = hue + HUE_STEP_DEG_PER_SEC * deltaSeconds
        return nextHue.normalizeHue()
    }
}

internal fun Float.normalizeHue(): Float {
     var h = this % 360f
     if (h < 0f) h += 360f
     return h.coerceIn(0f, 360f)
 }
