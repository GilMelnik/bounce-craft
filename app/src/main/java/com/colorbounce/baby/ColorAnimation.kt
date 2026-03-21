package com.colorbounce.baby

/**
 * Hue ping-pong while a shape is actively held: sweeps [0, 360]°, reverses at each boundary,
 * loops without wrapping to the opposite end (no abrupt jump).
 */
object ShapeColorAnimator {

    /** Degrees per second while the user is holding/creating the shape. */
    const val HUE_PING_PONG_DEG_PER_SEC = 42f

    /**
     * Advances hue along the spectrum; [direction] is +1 or -1 toward 360° or 0°.
     * Returns updated hue and direction after any boundary reflections in this frame.
     */
    fun stepHuePingPong(hue: Float, direction: Float, deltaSeconds: Float): Pair<Float, Float> {
        var h = hue.coerceIn(0f, 360f)
        var d = if (direction >= 0f) 1f else -1f
        var remaining = HUE_PING_PONG_DEG_PER_SEC * deltaSeconds
        var guard = 0
        while (remaining > 1e-4f && guard++ < 10_000) {
            if (d > 0f) {
                val space = 360f - h
                if (remaining <= space) {
                    h += remaining
                    remaining = 0f
                } else {
                    h = 360f
                    remaining -= space
                    d = -1f
                }
            } else {
                val space = h - 0f
                if (remaining <= space) {
                    h -= remaining
                    remaining = 0f
                } else {
                    h = 0f
                    remaining -= space
                    d = 1f
                }
            }
        }
        return h to d
    }
}

internal fun Float.normalizeHue(): Float {
    var h = this % 360f
    if (h < 0f) h += 360f
    return h
}
