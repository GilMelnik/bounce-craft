package com.colorbounce.baby

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private const val GEOM_EPS = 1e-4f
internal const val POLYGON_EDGE_SLOP_PX = 28f

/** Pentagram inner vertex radius / outer radius — classic proportion. */
private const val STAR_INNER_RATIO = 0.382f
internal const val HEART_SAMPLES = 48

internal fun usesPolygonPhysics(type: ShapeType): Boolean =
    type == ShapeType.STAR || type == ShapeType.HEART || type == ShapeType.DIAMOND

/**
 * Writes outline vertices of [shape] in world space into [outX]/[outY] and returns vertex count.
 * Only defined for [ShapeType.STAR], [ShapeType.HEART], [ShapeType.DIAMOND]; otherwise returns 0.
 */
internal fun fillPolygonVertices(shape: GameShape, outX: FloatArray, outY: FloatArray): Int {
    val cx = shape.x
    val cy = shape.y
    val hw = shape.width / 2f
    val hh = shape.height / 2f
    return when (shape.type) {
        ShapeType.DIAMOND -> {
            outX[0] = cx
            outY[0] = cy - hh
            outX[1] = cx + hw
            outY[1] = cy
            outX[2] = cx
            outY[2] = cy + hh
            outX[3] = cx - hw
            outY[3] = cy
            4
        }
        ShapeType.STAR -> {
            val outerR = min(hw, hh)
            val innerR = outerR * STAR_INNER_RATIO
            for (i in 0 until 10) {
                val angle = (-PI.toFloat() / 2f) + i * PI.toFloat() / 5f
                val rad = if (i % 2 == 0) outerR else innerR
                outX[i] = cx + cos(angle) * rad
                outY[i] = cy + sin(angle) * rad
            }
            10
        }
        ShapeType.HEART -> fillHeartVertices(cx, cy, shape.width, shape.height, outX, outY)
        else -> 0
    }
}

private fun fillHeartVertices(
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    outX: FloatArray,
    outY: FloatArray
): Int {
    val hx = FloatArray(HEART_SAMPLES)
    val hy = FloatArray(HEART_SAMPLES)
    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (i in 0 until HEART_SAMPLES) {
        val t = (i.toFloat() / HEART_SAMPLES) * (2f * PI.toFloat())
        val x = 16f * sin(t).pow(3)
        val y = 13f * cos(t) - 5f * cos(2f * t) - 2f * cos(3f * t) - cos(4f * t)
        hx[i] = x
        hy[i] = y
        minX = min(minX, x)
        maxX = max(maxX, x)
        minY = min(minY, y)
        maxY = max(maxY, y)
    }
    val bw = max(maxX - minX, GEOM_EPS)
    val bh = max(maxY - minY, GEOM_EPS)
    val scale = min(w / bw, h / bh) * 0.94f
    val ox = (minX + maxX) / 2f
    val oy = (minY + maxY) / 2f
    for (i in 0 until HEART_SAMPLES) {
        outX[i] = cx + (hx[i] - ox) * scale
        // Parametric heart is y-up; negate so the cusp points down in screen space.
        outY[i] = cy - (hy[i] - oy) * scale
    }
    return HEART_SAMPLES
}

internal fun pointInPolygon(px: Float, py: Float, vx: FloatArray, vy: FloatArray, n: Int): Boolean {
    var inside = false
    var j = n - 1
    for (i in 0 until n) {
        val xi = vx[i]
        val yi = vy[i]
        val xj = vx[j]
        val yj = vy[j]
        val denom = yj - yi
        if (abs(denom) >= GEOM_EPS) {
            val intersect = (yi > py) != (yj > py) &&
                px < (xj - xi) * (py - yi) / denom + xi
            if (intersect) inside = !inside
        }
        j = i
    }
    return inside
}

internal fun closestPointOnPolygonBoundary(
    px: Float,
    py: Float,
    vx: FloatArray,
    vy: FloatArray,
    n: Int
): Pair<Float, Float> {
    var bestX = vx[0]
    var bestY = vy[0]
    var bestD2 = Float.MAX_VALUE
    for (i in 0 until n) {
        val ax = vx[i]
        val ay = vy[i]
        val bx = vx[(i + 1) % n]
        val by = vy[(i + 1) % n]
        val (qx, qy) = closestPointOnSegment(px, py, ax, ay, bx, by)
        val dx = px - qx
        val dy = py - qy
        val d2 = dx * dx + dy * dy
        if (d2 < bestD2) {
            bestD2 = d2
            bestX = qx
            bestY = qy
        }
    }
    return Pair(bestX, bestY)
}

private fun closestPointOnSegment(
    px: Float,
    py: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float
): Pair<Float, Float> {
    val abx = bx - ax
    val aby = by - ay
    val apx = px - ax
    val apy = py - ay
    val ab2 = abx * abx + aby * aby
    if (ab2 < GEOM_EPS * GEOM_EPS) return Pair(ax, ay)
    var t = (apx * abx + apy * aby) / ab2
    t = t.coerceIn(0f, 1f)
    return Pair(ax + abx * t, ay + aby * t)
}

/** Hit-testing: filled interior or within [edgeSlopPx] of an edge (matches arch stroke slop). */
internal fun pointInPolygonShapeStroke(
    px: Float,
    py: Float,
    shape: GameShape,
    edgeSlopPx: Float = POLYGON_EDGE_SLOP_PX
): Boolean {
    if (!usesPolygonPhysics(shape.type)) return false
    val vx = FloatArray(HEART_SAMPLES)
    val vy = FloatArray(HEART_SAMPLES)
    val n = fillPolygonVertices(shape, vx, vy)
    if (pointInPolygon(px, py, vx, vy, n)) return true
    val (qx, qy) = closestPointOnPolygonBoundary(px, py, vx, vy, n)
    return hypot(px - qx, py - qy) <= edgeSlopPx
}
