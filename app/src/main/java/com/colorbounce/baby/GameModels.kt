package com.colorbounce.baby

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

enum class ShapeType {
    CIRCLE, RECTANGLE, TRIANGLE, ARCH, STAR, HEART, DIAMOND
}

/**
 * Mutable physics body. Sealed hierarchy lets the dispatcher pick a narrow-phase by `is` checks instead
 * of a type enum switch, while every subclass carries the same gameplay state (hue, lifetime, pin/imm,
 * ruler exemptions) so renderer/menu/tutorial code can keep reading scalar fields uniformly.
 *
 * Position/size setters auto-set [dirty] so [refreshAabb]/[refreshHulls] only run on shapes that moved
 * or resized this frame.
 */
sealed class GameShape : Collidable {

    abstract override val id: Long
    abstract override val type: ShapeType
    abstract override val collisionRadius: Float

    private var _x: Float = 0f
    private var _y: Float = 0f
    private var _width: Float = 0f
    private var _height: Float = 0f

    var x: Float
        get() = _x
        set(value) {
            if (_x != value) {
                _x = value
                dirty = true
            }
        }
    var y: Float
        get() = _y
        set(value) {
            if (_y != value) {
                _y = value
                dirty = true
            }
        }
    var width: Float
        get() = _width
        set(value) {
            if (_width != value) {
                _width = value
                dirty = true
            }
        }
    var height: Float
        get() = _height
        set(value) {
            if (_height != value) {
                _height = value
                dirty = true
            }
        }

    var vx: Float = 0f
    var vy: Float = 0f
    /** Current hue in degrees. */
    var hue: Float = 0f
    var saturation: Float = 1f
    var value: Float = 1f
    var lastInteractionMillis: Long = 0L
    /** If true, shape is static in physics and only moves when the user drags it. */
    var isPinned: Boolean = false
    /** If true, shape is not removed by shape timeout rules. */
    var isImmortal: Boolean = false
    /** If true, this shape's hue does not animate while the user is dragging it (when ruler hue lock is off). */
    var freezeHueWhileDragging: Boolean = false
    /**
     * When creation ruler hue lock is on, this shape may still animate hue while dragged.
     * Default false: follow the ruler lock for every shape until the user opts out in the shape menu.
     */
    var exemptFromGlobalHueLock: Boolean = false
    /** When ruler pins all shapes, this shape stays unpinned until cleared by ruler. */
    var exemptFromGlobalPin: Boolean = false
    /** When ruler makes all shapes immortal, this shape still times out until cleared by ruler. */
    var exemptFromGlobalImmortal: Boolean = false

    override val boundingBox: MutableAabb = MutableAabb()
    override val normal: MutableVector2 = MutableVector2()
    override var dirty: Boolean = true

    final override val isSmall: Boolean
        get() = type == ShapeType.CIRCLE || collisionRadius < SMALL_RADIUS_THRESHOLD

    /**
     * Populate all gameplay fields. Subclasses call this from the [GameShape.create] factory; the dirty
     * flag is forced true so caches refresh on the next physics tick.
     */
    internal fun populate(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        vx: Float = 0f,
        vy: Float = 0f,
        hue: Float = 0f,
        saturation: Float = 1f,
        value: Float = 1f,
        lastInteractionMillis: Long = 0L,
        isPinned: Boolean = false,
        isImmortal: Boolean = false,
        freezeHueWhileDragging: Boolean = false,
        exemptFromGlobalHueLock: Boolean = false,
        exemptFromGlobalPin: Boolean = false,
        exemptFromGlobalImmortal: Boolean = false
    ) {
        _x = x
        _y = y
        _width = width
        _height = height
        this.vx = vx
        this.vy = vy
        this.hue = hue
        this.saturation = saturation
        this.value = value
        this.lastInteractionMillis = lastInteractionMillis
        this.isPinned = isPinned
        this.isImmortal = isImmortal
        this.freezeHueWhileDragging = freezeHueWhileDragging
        this.exemptFromGlobalHueLock = exemptFromGlobalHueLock
        this.exemptFromGlobalPin = exemptFromGlobalPin
        this.exemptFromGlobalImmortal = exemptFromGlobalImmortal
        dirty = true
    }

    companion object {
        /** Builds a body of [type] and applies the supplied gameplay state in one call. */
        fun create(
            id: Long,
            type: ShapeType,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            vx: Float = 0f,
            vy: Float = 0f,
            hue: Float = 0f,
            saturation: Float = 1f,
            value: Float = 1f,
            lastInteractionMillis: Long = 0L,
            isPinned: Boolean = false,
            isImmortal: Boolean = false,
            freezeHueWhileDragging: Boolean = false,
            exemptFromGlobalHueLock: Boolean = false,
            exemptFromGlobalPin: Boolean = false,
            exemptFromGlobalImmortal: Boolean = false
        ): GameShape {
            val body: GameShape = when (type) {
                ShapeType.CIRCLE -> CircleBody(id)
                ShapeType.RECTANGLE -> RectangleBody(id)
                ShapeType.TRIANGLE -> TriangleBody(id)
                ShapeType.ARCH -> ArchBody(id)
                ShapeType.STAR -> StarBody(id)
                ShapeType.HEART -> HeartBody(id)
                ShapeType.DIAMOND -> DiamondBody(id)
            }
            body.populate(
                x, y, width, height, vx, vy, hue, saturation, value,
                lastInteractionMillis, isPinned, isImmortal,
                freezeHueWhileDragging, exemptFromGlobalHueLock,
                exemptFromGlobalPin, exemptFromGlobalImmortal
            )
            return body
        }
    }
}

val GameShape.color: Color
    get() = Color.hsv(hue.normalizeHue(), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))

// ---------------------------------------------------------------------------------------------
// Subclasses
// ---------------------------------------------------------------------------------------------

class CircleBody(override val id: Long) : GameShape() {
    override val type: ShapeType = ShapeType.CIRCLE
    override val collisionRadius: Float
        get() = width / 2f
    override val convexHulls: Array<MutablePolygon> = emptyArray()

    override fun refreshAabb() {
        val r = width / 2f
        boundingBox.setFromCenter(x, y, r, r)
    }

    override fun refreshHulls() = Unit
}

class RectangleBody(override val id: Long) : GameShape() {
    override val type: ShapeType = ShapeType.RECTANGLE
    override val collisionRadius: Float
        get() = max(width, height) / 2f
    override val convexHulls: Array<MutablePolygon> = arrayOf(MutablePolygon(4))

    override fun refreshAabb() {
        boundingBox.setFromCenter(x, y, width / 2f, height / 2f)
    }

    override fun refreshHulls() {
        val hw = width / 2f
        val hh = height / 2f
        val p = convexHulls[0]
        p.xs[0] = x - hw; p.ys[0] = y - hh
        p.xs[1] = x + hw; p.ys[1] = y - hh
        p.xs[2] = x + hw; p.ys[2] = y + hh
        p.xs[3] = x - hw; p.ys[3] = y + hh
        p.count = 4
    }
}

class TriangleBody(override val id: Long) : GameShape() {
    override val type: ShapeType = ShapeType.TRIANGLE
    override val collisionRadius: Float
        get() = max(width, height) / 2f
    override val convexHulls: Array<MutablePolygon> = arrayOf(MutablePolygon(3))

    override fun refreshAabb() {
        boundingBox.setFromCenter(x, y, width / 2f, height / 2f)
    }

    override fun refreshHulls() {
        val hw = width / 2f
        val hh = height / 2f
        val p = convexHulls[0]
        p.xs[0] = x; p.ys[0] = y - hh
        p.xs[1] = x - hw; p.ys[1] = y + hh
        p.xs[2] = x + hw; p.ys[2] = y + hh
        p.count = 3
    }
}

class DiamondBody(override val id: Long) : GameShape() {
    override val type: ShapeType = ShapeType.DIAMOND
    override val collisionRadius: Float
        get() = max(width, height) / 2f
    override val convexHulls: Array<MutablePolygon> = arrayOf(MutablePolygon(4))

    override fun refreshAabb() {
        boundingBox.setFromCenter(x, y, width / 2f, height / 2f)
    }

    override fun refreshHulls() {
        val hw = width / 2f
        val hh = height / 2f
        val p = convexHulls[0]
        p.xs[0] = x; p.ys[0] = y - hh
        p.xs[1] = x + hw; p.ys[1] = y
        p.xs[2] = x; p.ys[2] = y + hh
        p.xs[3] = x - hw; p.ys[3] = y
        p.count = 4
    }
}

/**
 * Star: pre-decomposed into 5 outer triangles + 1 inner pentagon. Uses the same vertex math as
 * [fillPolygonVertices] so collision and rendering align.
 */
class StarBody(override val id: Long) : GameShape() {
    override val type: ShapeType = ShapeType.STAR
    override val collisionRadius: Float
        get() = max(width, height) / 2f

    override val convexHulls: Array<MutablePolygon> = arrayOf(
        MutablePolygon(3),
        MutablePolygon(3),
        MutablePolygon(3),
        MutablePolygon(3),
        MutablePolygon(3),
        MutablePolygon(5)
    )

    private val tmpVx = FloatArray(10)
    private val tmpVy = FloatArray(10)

    override fun refreshAabb() {
        boundingBox.setFromCenter(x, y, width / 2f, height / 2f)
    }

    override fun refreshHulls() {
        val n = fillPolygonVertices(this, tmpVx, tmpVy)
        if (n != 10) return
        // Outer triangles: (innerLeft, outerTip, innerRight) for each of the 5 points.
        // Vertices are interleaved outer/inner starting at the top: indices 0,2,4,6,8 are tips;
        // 1,3,5,7,9 are inner points.
        for (k in 0 until 5) {
            val tip = 2 * k
            val left = (tip + 9) % 10
            val right = (tip + 1) % 10
            val tri = convexHulls[k]
            tri.xs[0] = tmpVx[left]; tri.ys[0] = tmpVy[left]
            tri.xs[1] = tmpVx[tip]; tri.ys[1] = tmpVy[tip]
            tri.xs[2] = tmpVx[right]; tri.ys[2] = tmpVy[right]
            tri.count = 3
        }
        val pent = convexHulls[5]
        for (k in 0 until 5) {
            pent.xs[k] = tmpVx[2 * k + 1]
            pent.ys[k] = tmpVy[2 * k + 1]
        }
        pent.count = 5
    }
}

/**
 * Heart: approximated by the convex hull of the 48-sample parametric curve. Treats the small top
 * notch as filled — visually undetectable in collision feel for a children's app.
 *
 * The hull index set is fixed (parametric is a uniform scaling of a single shape) so we precompute
 * once and reuse on every refresh.
 */
class HeartBody(override val id: Long) : GameShape() {
    override val type: ShapeType = ShapeType.HEART
    override val collisionRadius: Float
        get() = max(width, height) / 2f

    override val convexHulls: Array<MutablePolygon> =
        arrayOf(MutablePolygon(HEART_HULL_INDEX_COUNT))

    private val tmpVx = FloatArray(HEART_SAMPLES)
    private val tmpVy = FloatArray(HEART_SAMPLES)

    override fun refreshAabb() {
        boundingBox.setFromCenter(x, y, width / 2f, height / 2f)
    }

    override fun refreshHulls() {
        val n = fillPolygonVertices(this, tmpVx, tmpVy)
        if (n != HEART_SAMPLES) return
        val hull = convexHulls[0]
        val indices = HEART_HULL_INDICES
        for (k in indices.indices) {
            val idx = indices[k]
            hull.xs[k] = tmpVx[idx]
            hull.ys[k] = tmpVy[idx]
        }
        hull.count = indices.size
    }

    companion object {
        /** Precomputed convex hull indices over [HEART_SAMPLES] heart samples. */
        internal val HEART_HULL_INDICES: IntArray = computeHeartHullIndices()

        internal val HEART_HULL_INDEX_COUNT: Int = HEART_HULL_INDICES.size

        private fun computeHeartHullIndices(): IntArray {
            // Sample parametric heart at unit scale; geometry is identical up to uniform scaling so
            // the hull index set is invariant.
            val xs = FloatArray(HEART_SAMPLES)
            val ys = FloatArray(HEART_SAMPLES)
            sampleHeartUnit(xs, ys)
            return grahamScanIndices(xs, ys, HEART_SAMPLES)
        }

        private fun sampleHeartUnit(outX: FloatArray, outY: FloatArray) {
            for (i in 0 until HEART_SAMPLES) {
                val t = (i.toFloat() / HEART_SAMPLES) * (2f * PI.toFloat())
                outX[i] = 16f * sin(t).pow(3)
                // Match renderer: invert y so the cusp points down in screen space.
                outY[i] = -(13f * cos(t) - 5f * cos(2f * t) - 2f * cos(3f * t) - cos(4f * t))
            }
        }

        /**
         * Returns indices of [xs]/[ys] forming a counter-clockwise convex hull (Graham scan).
         * Stable for the heart sample (no collinear duplicates).
         */
        private fun grahamScanIndices(xs: FloatArray, ys: FloatArray, n: Int): IntArray {
            if (n < 3) return IntArray(n) { it }
            // Find pivot: lowest-y (then lowest-x).
            var pivot = 0
            for (i in 1 until n) {
                val py = ys[pivot]; val cy = ys[i]
                if (cy < py || (cy == py && xs[i] < xs[pivot])) pivot = i
            }
            val px = xs[pivot]
            val py = ys[pivot]
            // Sort the other points by polar angle around the pivot.
            val idx = IntArray(n) { it }
            // Move pivot to position 0 via swap.
            val tmp = idx[0]; idx[0] = idx[pivot]; idx[pivot] = tmp
            // Sort idx[1..n-1] by polar angle, breaking ties by distance.
            val sortable = idx.copyOfRange(1, n).toTypedArray()
            java.util.Arrays.sort(sortable) { a, b ->
                val ax = xs[a] - px; val ay = ys[a] - py
                val bx = xs[b] - px; val by = ys[b] - py
                val cross = ax * by - ay * bx
                when {
                    cross > 0f -> -1
                    cross < 0f -> 1
                    else -> {
                        val da = ax * ax + ay * ay
                        val db = bx * bx + by * by
                        da.compareTo(db)
                    }
                }
            }
            for (k in sortable.indices) idx[k + 1] = sortable[k]
            val stack = IntArray(n)
            var top = -1
            for (k in 0 until n) {
                val i = idx[k]
                while (top >= 1) {
                    val a = stack[top - 1]
                    val b = stack[top]
                    val crossX = (xs[b] - xs[a]) * (ys[i] - ys[a]) -
                        (ys[b] - ys[a]) * (xs[i] - xs[a])
                    if (crossX <= 0f) top-- else break
                }
                top++
                stack[top] = i
            }
            return stack.copyOfRange(0, top + 1)
        }
    }
}

/**
 * Arch: empty hulls — narrow phase uses inner/outer radii from [ShapeCollision] directly. AABB covers
 * the stroked top semicircle band, conservatively the full bounding box of the upper-half arc band.
 */
class ArchBody(override val id: Long) : GameShape() {
    override val type: ShapeType = ShapeType.ARCH
    override val collisionRadius: Float
        get() = max(width, height) / 2f
    override val convexHulls: Array<MutablePolygon> = emptyArray()

    override fun refreshAabb() {
        // Centerline is at (x, y + width/2), radius = width/2; band thickness = stroke.
        val rOut = archOuterRadius(this)
        val cy = y + width / 2f
        val minY = cy - rOut
        val maxY = cy + min(archStrokeWidth(this) / 2f, rOut)
        boundingBox.set(x - rOut, minY, x + rOut, maxY)
    }

    override fun refreshHulls() = Unit
}
