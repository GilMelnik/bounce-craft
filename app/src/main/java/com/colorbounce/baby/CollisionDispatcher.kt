package com.colorbounce.baby

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Narrow-phase entry point.
 *
 * Important fixes vs naive compound collision:
 *  - **Circle–circle** runs only when both bodies are [ShapeType.CIRCLE]. Two “small” polygons must
 *    not be approximated as circles (that caused stars/hearts to ignore real geometry).
 *  - **MTV across convex pieces** uses **minimum** penetration among overlapping hulls / contacts — max
 *    penetration picked the wrong separating axis and let circles tunnel between hulls.
 *  - **Stars** use the full 10-vertex outline for circle tests (non-convex safe boundary distance +
 *    point-in-polygon), so gaps between convex tiles cannot swallow circles.
 */
class CollisionDispatcher {

    private val manifold = CollisionManifold()
    private var projMin: Float = 0f
    private var projMax: Float = 0f

    /** Scratch for polygon outline helpers (max outline = heart 48 verts). */
    private val outlineVx = FloatArray(HEART_SAMPLES)
    private val outlineVy = FloatArray(HEART_SAMPLES)

    fun evaluate(a: GameShape, b: GameShape): CollisionManifold? {
        manifold.clear()
        if (!a.boundingBox.intersects(b.boundingBox)) return null

        if (a is ArchBody || b is ArchBody) {
            val m = archDispatch(a, b)
            if (m != null) return m
            // Containment fallback: one center inside the other's solid band.
            return containmentFallback(a, b)
        }

        val aCircle = a.type == ShapeType.CIRCLE
        val bCircle = b.type == ShapeType.CIRCLE

        if (aCircle && bCircle) {
            val ar = a.width * 0.5f
            val br = b.width * 0.5f
            return circleVsCircle(a.x, a.y, ar, b.x, b.y, br, bIntoA = false)
        }
        if (aCircle) {
            val m = circleVsShape(a, b, flipNormal = false)
            if (m != null) return m
            return containmentFallback(a, b)
        }
        if (bCircle) {
            val m = circleVsShape(b, a, flipNormal = true)
            if (m != null) return m
            return containmentFallback(a, b)
        }

        val m = polygonVsPolygon(a, b)
        if (m != null) return m
        return containmentFallback(a, b)
    }

    // -----------------------------------------------------------------------------------------
    // Circle vs arbitrary non-circle (polygon/hulls/star outline)
    // -----------------------------------------------------------------------------------------

    private fun circleVsShape(circle: GameShape, other: GameShape, flipNormal: Boolean): CollisionManifold? {
        val cx = circle.x
        val cy = circle.y
        val r = circle.width * 0.5f

        val hit = when (other) {
            is StarBody -> {
                val n = fillPolygonVertices(other, outlineVx, outlineVy)
                if (n != 10) false
                else circleVsPolygonOutline(cx, cy, r, outlineVx, outlineVy, n)
            }
            is HeartBody -> {
                val n = fillPolygonVertices(other, outlineVx, outlineVy)
                if (n != HEART_SAMPLES) false
                else circleVsPolygonOutline(cx, cy, r, outlineVx, outlineVy, n)
            }
            else -> circleVsConvexHulls(cx, cy, r, other)
        }
        if (!hit) return null
        if (flipNormal) {
            manifold.set(-manifold.nx, -manifold.ny, manifold.penetrationDepth)
        }
        return manifold
    }

    /**
     * Circle vs union of convex hulls — penetration is the **minimum** translation among hulls that
     * actually overlap (deepest resolving contact).
     */
    private fun circleVsConvexHulls(cx: Float, cy: Float, r: Float, polyBody: GameShape): Boolean {
        var bestPen = Float.MAX_VALUE
        var bestNx = 0f
        var bestNy = 0f
        var any = false
        for (hull in polyBody.convexHulls) {
            if (hull.count < 3) continue
            if (circleVsConvex(cx, cy, r, hull)) {
                val pen = manifold.penetrationDepth
                if (pen < bestPen) {
                    bestPen = pen
                    bestNx = manifold.nx
                    bestNy = manifold.ny
                    any = true
                }
            }
        }
        if (!any) return false
        manifold.set(bestNx, bestNy, bestPen)
        return true
    }

    /**
     * Simple polygon (possibly non-convex): closest point on boundary + winding point test.
     * No allocations — writes [manifold] on hit.
     */
    private fun circleVsPolygonOutline(
        cx: Float,
        cy: Float,
        r: Float,
        xs: FloatArray,
        ys: FloatArray,
        n: Int
    ): Boolean {
        var bestQx = xs[0]
        var bestQy = ys[0]
        var bestD2 = Float.MAX_VALUE
        var j = n - 1
        for (i in 0 until n) {
            val ax = xs[j]
            val ay = ys[j]
            val bx = xs[i]
            val by = ys[i]
            val abx = bx - ax
            val aby = by - ay
            val apx = cx - ax
            val apy = cy - ay
            val ab2 = abx * abx + aby * aby
            val t = if (ab2 > COLLISION_EPS * COLLISION_EPS) {
                ((apx * abx + apy * aby) / ab2).coerceIn(0f, 1f)
            } else {
                0f
            }
            val qx = ax + abx * t
            val qy = ay + aby * t
            val dx = cx - qx
            val dy = cy - qy
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) {
                bestD2 = d2
                bestQx = qx
                bestQy = qy
            }
            j = i
        }

        val inside = pointInPolygon(cx, cy, xs, ys, n)
        val dist = sqrt(bestD2.toDouble()).toFloat()

        if (inside) {
            val nx: Float
            val ny: Float
            if (dist < COLLISION_EPS) {
                nx = 1f
                ny = 0f
            } else {
                nx = -(cx - bestQx) / dist
                ny = -(cy - bestQy) / dist
            }
            manifold.set(nx, ny, r + dist)
            return true
        }

        if (bestD2 >= r * r) return false
        val nx: Float
        val ny: Float
        if (dist < COLLISION_EPS) {
            nx = 1f
            ny = 0f
        } else {
            nx = (cx - bestQx) / dist
            ny = (cy - bestQy) / dist
        }
        manifold.set(nx, ny, r - dist)
        return true
    }

    // -----------------------------------------------------------------------------------------
    // Containment fallback (last-resort protection against missed collisions)
    // -----------------------------------------------------------------------------------------

    /**
     * If no collision manifold was found by the primary narrow-phase, this checks whether one shape's
     * **center** is actually inside the other's solid area and synthesizes a push-out manifold.
     *
     * This is intentionally conservative: if we say \"inside\", we will separate the pair even if the
     * MTV approximation missed due to discrete stepping or compound hull gaps.
     */
    private fun containmentFallback(a: GameShape, b: GameShape): CollisionManifold? {
        // Arch containment: center inside arch solid band.
        if (a is ArchBody) {
            val m = centerInsideArch(b, a)
            if (m != null) return m
        }
        if (b is ArchBody) {
            val m = centerInsideArch(a, b)
            if (m != null) {
                val nx = manifold.nx
                val ny = manifold.ny
                val pen = manifold.penetrationDepth
                manifold.set(-nx, -ny, pen)
                return manifold
            }
        }
        // Polygon outline containment: STAR/HEART/DIAMOND use their real outline.
        val m1 = centerInsideOutline(a, b)
        if (m1 != null) return m1
        val m2 = centerInsideOutline(b, a)
        if (m2 != null) {
            val nx = manifold.nx
            val ny = manifold.ny
            val pen = manifold.penetrationDepth
            manifold.set(-nx, -ny, pen)
            return manifold
        }
        return null
    }

    private fun centerInsideArch(other: GameShape, arch: ArchBody): CollisionManifold? {
        if (!pointInArchSolid(other.x, other.y, arch)) return null
        val cl = closestOnArchSolidBoundary(arch, other.x, other.y)
        val dx = other.x - cl.x
        val dy = other.y - cl.y
        val d2 = dx * dx + dy * dy
        val dist = sqrt(d2.toDouble()).toFloat()
        val nx: Float
        val ny: Float
        if (dist < COLLISION_EPS) {
            nx = 0f
            ny = -1f
        } else {
            nx = dx / dist
            ny = dy / dist
        }
        // If the other body has radius (circle), account for it; otherwise push the center to boundary.
        val extra = if (other.type == ShapeType.CIRCLE) other.width * 0.5f else 0f
        manifold.set(nx, ny, dist + extra)
        return manifold
    }

    private fun centerInsideOutline(pointBody: GameShape, outlineBody: GameShape): CollisionManifold? {
        val n = fillPolygonVerticesIfSupported(outlineBody, outlineVx, outlineVy)
        if (n < 3) return null
        if (!pointInPolygon(pointBody.x, pointBody.y, outlineVx, outlineVy, n)) return null
        // Compute closest boundary point to the inside point.
        val cp = closestPointOnPolyline(pointBody.x, pointBody.y, outlineVx, outlineVy, n)
        val qx = cp.x
        val qy = cp.y
        val dist = cp.dist
        val nx: Float
        val ny: Float
        if (dist < COLLISION_EPS) {
            nx = 1f
            ny = 0f
        } else {
            nx = -(pointBody.x - qx) / dist
            ny = -(pointBody.y - qy) / dist
        }
        val extra = if (pointBody.type == ShapeType.CIRCLE) pointBody.width * 0.5f else 0f
        manifold.set(nx, ny, dist + extra)
        return manifold
    }

    private fun fillPolygonVerticesIfSupported(shape: GameShape, outX: FloatArray, outY: FloatArray): Int =
        when (shape.type) {
            ShapeType.STAR, ShapeType.HEART, ShapeType.DIAMOND -> fillPolygonVertices(shape, outX, outY)
            else -> 0
        }

    /** Returns closest point on polygon boundary and distance from (px,py). No allocations. */
    private fun closestPointOnPolyline(
        px: Float,
        py: Float,
        xs: FloatArray,
        ys: FloatArray,
        n: Int
    ): ClosestPointResult {
        var bestQx = xs[0]
        var bestQy = ys[0]
        var bestD2 = Float.MAX_VALUE
        var j = n - 1
        for (i in 0 until n) {
            val ax = xs[j]
            val ay = ys[j]
            val bx = xs[i]
            val by = ys[i]
            val abx = bx - ax
            val aby = by - ay
            val apx = px - ax
            val apy = py - ay
            val ab2 = abx * abx + aby * aby
            val t = if (ab2 > COLLISION_EPS * COLLISION_EPS) ((apx * abx + apy * aby) / ab2).coerceIn(0f, 1f) else 0f
            val qx = ax + abx * t
            val qy = ay + aby * t
            val dx = px - qx
            val dy = py - qy
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) {
                bestD2 = d2
                bestQx = qx
                bestQy = qy
            }
            j = i
        }
        val dist = sqrt(bestD2.toDouble()).toFloat()
        return ClosestPointResult(bestQx, bestQy, dist)
    }

    private class ClosestPointResult(val x: Float, val y: Float, val dist: Float)

    // -----------------------------------------------------------------------------------------
    // Circle vs circle
    // -----------------------------------------------------------------------------------------

    private fun circleVsCircle(
        ax: Float,
        ay: Float,
        ar: Float,
        bx: Float,
        by: Float,
        br: Float,
        bIntoA: Boolean
    ): CollisionManifold? {
        val dx = bx - ax
        val dy = by - ay
        val rSum = ar + br
        val d2 = dx * dx + dy * dy
        if (d2 >= rSum * rSum) return null
        val dist = sqrt(d2.toDouble()).toFloat()
        val penetration = rSum - dist
        if (penetration <= 0f) return null
        val nx: Float
        val ny: Float
        if (dist < COLLISION_EPS) {
            nx = 1f
            ny = 0f
        } else {
            nx = dx / dist
            ny = dy / dist
        }
        if (bIntoA) {
            manifold.set(-nx, -ny, penetration)
        } else {
            manifold.set(nx, ny, penetration)
        }
        return manifold
    }

    // -----------------------------------------------------------------------------------------
    // Circle vs convex polygon
    // -----------------------------------------------------------------------------------------

    /**
     * Convex polygon: closest boundary point + consistent inside test (any winding).
     */
    private fun circleVsConvex(
        cx: Float,
        cy: Float,
        r: Float,
        poly: MutablePolygon
    ): Boolean {
        val n = poly.count
        var bestQx = poly.xs[0]
        var bestQy = poly.ys[0]
        var bestD2 = Float.MAX_VALUE
        for (i in 0 until n) {
            val ax = poly.xs[i]
            val ay = poly.ys[i]
            val bx = poly.xs[(i + 1) % n]
            val by = poly.ys[(i + 1) % n]
            val ex = bx - ax
            val ey = by - ay
            val apx = cx - ax
            val apy = cy - ay
            val edgeLen2 = ex * ex + ey * ey
            val tNum = apx * ex + apy * ey
            val t = if (edgeLen2 > COLLISION_EPS * COLLISION_EPS) (tNum / edgeLen2).coerceIn(0f, 1f) else 0f
            val qx = ax + ex * t
            val qy = ay + ey * t
            val dx = cx - qx
            val dy = cy - qy
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) {
                bestD2 = d2
                bestQx = qx
                bestQy = qy
            }
        }

        val inside = pointInConvexPolygon(cx, cy, poly)
        val dist = sqrt(bestD2.toDouble()).toFloat()

        if (inside) {
            val nx: Float
            val ny: Float
            if (dist < COLLISION_EPS) {
                nx = 1f
                ny = 0f
            } else {
                nx = -(cx - bestQx) / dist
                ny = -(cy - bestQy) / dist
            }
            manifold.set(nx, ny, r + dist)
            return true
        }

        if (bestD2 >= r * r) return false
        val distOut = sqrt(bestD2.toDouble()).toFloat()
        val pen = r - distOut
        if (pen <= 0f) return false
        val nx: Float
        val ny: Float
        if (distOut < COLLISION_EPS) {
            nx = 1f
            ny = 0f
        } else {
            nx = (cx - bestQx) / distOut
            ny = (cy - bestQy) / distOut
        }
        manifold.set(nx, ny, pen)
        return true
    }

    /** Works for CW or CCW convex polygons; on-edge counts as inside. */
    private fun pointInConvexPolygon(px: Float, py: Float, poly: MutablePolygon): Boolean {
        val n = poly.count
        if (n < 3) return false
        var sign = 0
        for (i in 0 until n) {
            val ax = poly.xs[i]
            val ay = poly.ys[i]
            val bx = poly.xs[(i + 1) % n]
            val by = poly.ys[(i + 1) % n]
            val cross = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
            val c = when {
                cross > COLLISION_EPS -> 1
                cross < -COLLISION_EPS -> -1
                else -> 0
            }
            if (c != 0) {
                if (sign == 0) sign = c
                else if (sign != c) return false
            }
        }
        return sign != 0
    }

    // -----------------------------------------------------------------------------------------
    // Polygon vs polygon (SAT)
    // -----------------------------------------------------------------------------------------

    private fun polygonVsPolygon(a: GameShape, b: GameShape): CollisionManifold? {
        var bestPen = Float.MAX_VALUE
        var bestNx = 0f
        var bestNy = 0f
        var anyHit = false
        for (ha in a.convexHulls) {
            if (ha.count < 3) continue
            for (hb in b.convexHulls) {
                if (hb.count < 3) continue
                if (satConvexConvex(ha, hb, a.x, a.y, b.x, b.y)) {
                    val pen = manifold.penetrationDepth
                    if (pen < bestPen) {
                        bestPen = pen
                        bestNx = manifold.nx
                        bestNy = manifold.ny
                        anyHit = true
                    }
                }
            }
        }
        if (!anyHit) return null
        manifold.set(bestNx, bestNy, bestPen)
        return manifold
    }

    private fun satConvexConvex(
        a: MutablePolygon,
        b: MutablePolygon,
        aCx: Float,
        aCy: Float,
        bCx: Float,
        bCy: Float
    ): Boolean {
        var minOverlap = Float.MAX_VALUE
        var nx = 0f
        var ny = 0f
        if (!sweepEdgeAxes(a, b) { ax, ay, ovl ->
                if (ovl < minOverlap) {
                    minOverlap = ovl
                    nx = ax
                    ny = ay
                }
            }) return false
        if (!sweepEdgeAxes(b, a) { ax, ay, ovl ->
                if (ovl < minOverlap) {
                    minOverlap = ovl
                    nx = ax
                    ny = ay
                }
            }) return false
        if (minOverlap <= 0f) return false
        val cdx = bCx - aCx
        val cdy = bCy - aCy
        if (nx * cdx + ny * cdy < 0f) {
            nx = -nx
            ny = -ny
        }
        manifold.set(nx, ny, minOverlap)
        return true
    }

    private inline fun sweepEdgeAxes(
        src: MutablePolygon,
        other: MutablePolygon,
        recordOverlap: (axisX: Float, axisY: Float, overlap: Float) -> Unit
    ): Boolean {
        val n = src.count
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ex = src.xs[j] - src.xs[i]
            val ey = src.ys[j] - src.ys[i]
            var axNx = -ey
            var axNy = ex
            val len2 = axNx * axNx + axNy * axNy
            if (len2 < COLLISION_EPS) continue
            val invLen = 1f / sqrt(len2.toDouble()).toFloat()
            axNx *= invLen
            axNy *= invLen
            projectOnto(src, axNx, axNy)
            val aMin = projMin
            val aMax = projMax
            projectOnto(other, axNx, axNy)
            val bMin = projMin
            val bMax = projMax
            if (aMax < bMin || bMax < aMin) return false
            val overlap = min(aMax, bMax) - max(aMin, bMin)
            recordOverlap(axNx, axNy, overlap)
        }
        return true
    }

    private fun projectOnto(p: MutablePolygon, nx: Float, ny: Float) {
        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        val n = p.count
        for (i in 0 until n) {
            val v = p.xs[i] * nx + p.ys[i] * ny
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        projMin = mn
        projMax = mx
    }

    // -----------------------------------------------------------------------------------------
    // Arch dispatch
    // -----------------------------------------------------------------------------------------

    private fun archDispatch(a: GameShape, b: GameShape): CollisionManifold? {
        return when {
            a is ArchBody && b is ArchBody -> archVsArch(a, b)
            a is ArchBody -> archVsCircle(a, b.x, b.y, colliderRadius(b))
            b is ArchBody -> {
                val hit = archVsCircle(b, a.x, a.y, colliderRadius(a)) ?: return null
                manifold.set(-manifold.nx, -manifold.ny, manifold.penetrationDepth)
                manifold
            }
            else -> null
        }
    }

    /** Physics radius for arch pairing when the other body is not a pure circle outline. */
    private fun colliderRadius(body: GameShape): Float =
        if (body.type == ShapeType.CIRCLE) body.width * 0.5f else body.collisionRadius

    private fun archVsArch(a: ArchBody, b: ArchBody): CollisionManifold? {
        val cax = a.x
        val cay = a.y + a.width / 2f
        val cbx = b.x
        val cby = b.y + b.width / 2f
        val ra = archOuterRadius(a)
        val rb = archOuterRadius(b)
        return circleVsCircle(cax, cay, ra, cbx, cby, rb, bIntoA = false)
    }

    private fun archVsCircle(arch: ArchBody, ox: Float, oy: Float, otherRadius: Float): CollisionManifold? {
        val cl = closestOnArchSolidBoundary(arch, ox, oy)
        val cx = arch.x
        val cy = arch.y + arch.width / 2f
        val dx = ox - cl.x
        val dy = oy - cl.y
        val d2 = dx * dx + dy * dy
        val dist = if (d2 < COLLISION_EPS * COLLISION_EPS) 0f else sqrt(d2.toDouble()).toFloat()
        if (dist < COLLISION_EPS) {
            val nnx: Float
            val nny: Float
            when (cl.feature) {
                ArchBoundaryFeatureKind.OUTER_ARC -> {
                    val vx = ox - cx
                    val vy = oy - cy
                    val vlen2 = vx * vx + vy * vy
                    if (vlen2 < COLLISION_EPS) {
                        nnx = 0f
                        nny = -1f
                    } else {
                        val l = sqrt(vlen2.toDouble()).toFloat()
                        nnx = vx / l
                        nny = vy / l
                    }
                }
                ArchBoundaryFeatureKind.INNER_ARC -> {
                    val vx = ox - cx
                    val vy = oy - cy
                    val vlen2 = vx * vx + vy * vy
                    if (vlen2 < COLLISION_EPS) {
                        nnx = 0f
                        nny = 1f
                    } else {
                        val l = sqrt(vlen2.toDouble()).toFloat()
                        nnx = vx / l
                        nny = vy / l
                    }
                }
                ArchBoundaryFeatureKind.CAP -> {
                    val towardBall = oy - cl.y
                    if (abs(towardBall) < COLLISION_EPS) {
                        nnx = 0f
                        nny = -1f
                    } else {
                        nnx = 0f
                        nny = sign(towardBall)
                    }
                }
            }
            manifold.set(nnx, nny, otherRadius)
            return manifold
        }
        val penetration = otherRadius - dist
        if (penetration <= 0f) return null
        manifold.set(dx / dist, dy / dist, penetration)
        return manifold
    }
}
