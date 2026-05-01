package com.colorbounce.baby

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val EPS = 1e-4f

/** Normal points from [a] toward [b]; overlap is penetration depth along that normal. */
internal data class CollisionManifold(
    val nx: Float,
    val ny: Float,
    val overlap: Float
)

/** Matches [GamePlayfield] arch stroke width. */
internal fun archStrokeWidth(shape: GameShape): Float {
    val r = shape.width / 2f
    return min(shape.height, r * 0.6f)
}

internal fun archCircleCenter(shape: GameShape): Offset =
    Offset(shape.x, shape.y + shape.width / 2f)

private fun archOuterRadius(shape: GameShape): Float =
    shape.width / 2f + archStrokeWidth(shape) / 2f

internal fun collisionRadius(shape: GameShape): Float =
    when (shape.type) {
        ShapeType.CIRCLE -> shape.width / 2f
        else -> max(shape.width, shape.height) / 2f
    }

/**
 * Closest point on the outer collision boundary of the arch to [px],[py].
 * The drawable arch is the top semicircle (same as [GamePlayfield] drawArc start 180°, sweep 180°).
 */
private fun closestPointOnArchOuterBoundary(
    arch: GameShape,
    px: Float,
    py: Float
): Pair<Float, Float> {
    val c = archCircleCenter(arch)
    val radius = archOuterRadius(arch)
    return closestPointOnTopSemicircle(c.x, c.y, radius, px, py)
}

/**
 * Top semicircle of circle centered at [cx],[cy]: arc from left equator through top to right equator.
 * Points on that arc satisfy [qy] <= [cy] (screen coords, y down).
 */
private fun closestPointOnTopSemicircle(
    cx: Float,
    cy: Float,
    radius: Float,
    px: Float,
    py: Float
): Pair<Float, Float> {
    val vx = px - cx
    val vy = py - cy
    val len = hypot(vx, vy)
    if (len < EPS) return Pair(cx, cy - radius)
    val nx = vx / len
    val ny = vy / len
    val qx = cx + radius * nx
    val qy = cy + radius * ny
    return if (qy <= cy + EPS) {
        Pair(qx, qy)
    } else {
        val e1x = cx - radius
        val e1y = cy
        val e2x = cx + radius
        val e2y = cy
        val d1 = hypot(px - e1x, py - e1y)
        val d2 = hypot(px - e2x, py - e2y)
        if (d1 <= d2) Pair(e1x, e1y) else Pair(e2x, e2y)
    }
}

private fun manifoldArchVsCircle(
    arch: GameShape,
    ox: Float,
    oy: Float,
    otherRadius: Float
): CollisionManifold? {
    val (qx, qy) = closestPointOnArchOuterBoundary(arch, ox, oy)
    val dx = ox - qx
    val dy = oy - qy
    val dist = hypot(dx, dy)
    if (dist < EPS) {
        val c = archCircleCenter(arch)
        val vx = ox - c.x
        val vy = oy - c.y
        val vlen = hypot(vx, vy)
        val nnx = if (vlen < EPS) 0f else vx / vlen
        val nny = if (vlen < EPS) -1f else vy / vlen
        return CollisionManifold(nnx, nny, otherRadius)
    }
    val penetration = otherRadius - dist
    if (penetration <= 0f) return null
    val nx = dx / dist
    val ny = dy / dist
    return CollisionManifold(nx, ny, penetration)
}

private fun manifoldCircleVsCircle(
    ax: Float,
    ay: Float,
    ar: Float,
    bx: Float,
    by: Float,
    br: Float
): CollisionManifold? {
    val dx = bx - ax
    val dy = by - ay
    val dist = max(EPS, hypot(dx, dy))
    val minDist = ar + br
    if (dist >= minDist) return null
    val overlap = minDist - dist
    val nx = dx / dist
    val ny = dy / dist
    return CollisionManifold(nx, ny, overlap)
}

/** Approximation: outer semicircles as full circles at arch centers (may overlap slightly early when tops face). */
private fun manifoldArchVsArch(a: GameShape, b: GameShape): CollisionManifold? {
    val ca = archCircleCenter(a)
    val cb = archCircleCenter(b)
    val ra = archOuterRadius(a)
    val rb = archOuterRadius(b)
    return manifoldCircleVsCircle(ca.x, ca.y, ra, cb.x, cb.y, rb)
}

internal fun computePairCollision(a: GameShape, b: GameShape): CollisionManifold? {
    val aArch = a.type == ShapeType.ARCH
    val bArch = b.type == ShapeType.ARCH
    return when {
        aArch && bArch -> manifoldArchVsArch(a, b)
        aArch -> manifoldArchVsCircle(a, b.x, b.y, collisionRadius(b))
        bArch -> {
            val m = manifoldArchVsCircle(b, a.x, a.y, collisionRadius(a))
                ?: return null
            CollisionManifold(-m.nx, -m.ny, m.overlap)
        }
        else -> manifoldCircleVsCircle(
            a.x,
            a.y,
            collisionRadius(a),
            b.x,
            b.y,
            collisionRadius(b)
        )
    }
}
