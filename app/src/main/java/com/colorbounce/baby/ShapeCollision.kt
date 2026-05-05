package com.colorbounce.baby

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val EPS = 1e-4f

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

internal enum class ArchBoundaryFeatureKind {
    OUTER_ARC,
    INNER_ARC,
    CAP
}

internal data class ArchBoundaryClosest(
    val x: Float,
    val y: Float,
    val feature: ArchBoundaryFeatureKind
)

/**
 * Top semicircle of circle centered at cx,cy: arc from left equator through top to right equator.
 * Points on that arc satisfy qy <= cy (screen coords, y down).
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
internal fun closestOnArchSolidBoundary(arch: GameShape, px: Float, py: Float): ArchBoundaryClosest {
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
        ArchBoundaryClosest(outer.first, outer.second, ArchBoundaryFeatureKind.OUTER_ARC),
        ArchBoundaryClosest(inner.first, inner.second, ArchBoundaryFeatureKind.INNER_ARC),
        ArchBoundaryClosest(leftCap.first, leftCap.second, ArchBoundaryFeatureKind.CAP),
        ArchBoundaryClosest(rightCap.first, rightCap.second, ArchBoundaryFeatureKind.CAP)
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
