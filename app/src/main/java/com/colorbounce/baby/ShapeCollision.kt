package com.colorbounce.baby

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

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

internal fun archMidRadius(shape: GameShape): Float = shape.width / 2f

/** Inner radius of the stroked band (centerline − half stroke). */
internal fun archInnerRadius(shape: GameShape): Float =
    max(EPS, archMidRadius(shape) - archStrokeWidth(shape) / 2f)

/** Outer radius of the stroked band (centerline + half stroke). */
internal fun archOuterRadius(shape: GameShape): Float =
    archMidRadius(shape) + archStrokeWidth(shape) / 2f

internal fun collisionRadius(shape: GameShape): Float =
    when (shape.type) {
        ShapeType.CIRCLE -> shape.width / 2f
        else -> max(shape.width, shape.height) / 2f
    }

private enum class ArchBoundaryFeature {
    OUTER_ARC,
    INNER_ARC,
    CAP
}

private data class ArchBoundaryClosest(
    val x: Float,
    val y: Float,
    val feature: ArchBoundaryFeature
)

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

private fun closestPointOnHorizontalSegment(
    px: Float,
    py: Float,
    x0: Float,
    x1: Float,
    segY: Float
): Pair<Float, Float> {
    val xa = min(x0, x1)
    val xb = max(x0, x1)
    val qx = px.coerceIn(xa, xb)
    return Pair(qx, segY)
}

/**
 * Closest point on the boundary of the arch **solid** (stroked top semicircle band):
 * outer arc, inner arc, and the two horizontal side caps between radii.
 */
private fun closestOnArchSolidBoundary(arch: GameShape, px: Float, py: Float): ArchBoundaryClosest {
    val c = archCircleCenter(arch)
    val cx = c.x
    val cy = c.y
    val rOut = archOuterRadius(arch)
    val rIn = archInnerRadius(arch)

    val outer = closestPointOnTopSemicircle(cx, cy, rOut, px, py)
    val inner = closestPointOnTopSemicircle(cx, cy, rIn, px, py)
    val leftCap = closestPointOnHorizontalSegment(px, py, cx - rOut, cx - rIn, cy)
    val rightCap = closestPointOnHorizontalSegment(px, py, cx + rIn, cx + rOut, cy)

    val candidates = listOf(
        ArchBoundaryClosest(outer.first, outer.second, ArchBoundaryFeature.OUTER_ARC),
        ArchBoundaryClosest(inner.first, inner.second, ArchBoundaryFeature.INNER_ARC),
        ArchBoundaryClosest(leftCap.first, leftCap.second, ArchBoundaryFeature.CAP),
        ArchBoundaryClosest(rightCap.first, rightCap.second, ArchBoundaryFeature.CAP)
    )
    return candidates.minBy { hypot(px - it.x, py - it.y) }
}

/** True if [px],[py] lies in the filled stroked arch (annular sector + side caps). */
internal fun pointInArchSolid(px: Float, py: Float, shape: GameShape): Boolean {
    val c = archCircleCenter(shape)
    val cx = c.x
    val cy = c.y
    val rMid = archMidRadius(shape)
    val stroke = archStrokeWidth(shape)
    val rIn = archInnerRadius(shape)
    val rOut = archOuterRadius(shape)
    val halfThick = stroke / 2f + 2f

    if (kotlin.math.abs(py - cy) <= halfThick) {
        if (px >= cx - rOut && px <= cx - rIn) return true
        if (px >= cx + rIn && px <= cx + rOut) return true
    }

    val vx = px - cx
    val vy = py - cy
    val d = hypot(vx, vy)
    if (d < rIn || d > rOut) return false
    if (d < EPS) return false
    val projY = cy + rMid * (vy / d)
    return projY <= cy + 2f
}

/** Hit-testing: interior of stroked arch or near its boundary (for touches). */
internal fun pointInArchStroke(px: Float, py: Float, shape: GameShape, edgeSlopPx: Float = 28f): Boolean {
    if (pointInArchSolid(px, py, shape)) return true
    val cl = closestOnArchSolidBoundary(shape, px, py)
    return hypot(px - cl.x, py - cl.y) <= edgeSlopPx
}

private fun manifoldArchVsCircle(
    arch: GameShape,
    ox: Float,
    oy: Float,
    otherRadius: Float
): CollisionManifold? {
    val closest = closestOnArchSolidBoundary(arch, ox, oy)
    val c = archCircleCenter(arch)
    val dx = ox - closest.x
    val dy = oy - closest.y
    val dist = hypot(dx, dy)
    if (dist < EPS) {
        val nn = when (closest.feature) {
            ArchBoundaryFeature.OUTER_ARC -> {
                val vx = ox - c.x
                val vy = oy - c.y
                val vlen = hypot(vx, vy)
                if (vlen < EPS) Pair(0f, -1f) else Pair(vx / vlen, vy / vlen)
            }
            ArchBoundaryFeature.INNER_ARC -> {
                val vx = ox - c.x
                val vy = oy - c.y
                val vlen = hypot(vx, vy)
                if (vlen < EPS) Pair(0f, 1f) else Pair(vx / vlen, vy / vlen)
            }
            ArchBoundaryFeature.CAP -> {
                val towardBall = oy - closest.y
                when {
                    kotlin.math.abs(towardBall) < EPS -> Pair(0f, -1f)
                    else -> Pair(0f, sign(towardBall))
                }
            }
        }
        return CollisionManifold(nn.first, nn.second, otherRadius)
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
