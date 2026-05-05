package com.colorbounce.baby

/** Maximum number of physics bodies expected on screen — used to size the broad-phase visited matrix. */
internal const val MAX_BODIES = 128

/**
 * isSmall threshold: any shape whose [Collidable.collisionRadius] is below this is treated as a circle
 * by [CollisionDispatcher] (skipping SAT). Circles are always small regardless of radius.
 */
internal const val SMALL_RADIUS_THRESHOLD = 24f

/** Default cell size for [SpatialGrid] — chosen so typical 60–150 px shapes span 1–2 cells. */
internal const val DEFAULT_GRID_CELL_SIZE = 128f

internal const val COLLISION_EPS = 1e-4f

/** Narrow-phase / resolver iterations per physics tick — clears residual overlap from pair order & MTV approximation. */
internal const val COLLISION_SOLVER_PASSES = 4

/** Extra positional separation along the contact normal to kill lingering visual overlap from discrete stepping. */
internal const val POSITION_SEPARATION_SLOP_PX = 1f

/** Mutable axis-aligned bounding box. Reused frame-to-frame; never reallocated in hot paths. */
class MutableAabb {
    var minX: Float = 0f
    var minY: Float = 0f
    var maxX: Float = 0f
    var maxY: Float = 0f

    fun set(minX: Float, minY: Float, maxX: Float, maxY: Float) {
        this.minX = minX
        this.minY = minY
        this.maxX = maxX
        this.maxY = maxY
    }

    fun setFromCenter(cx: Float, cy: Float, halfW: Float, halfH: Float) {
        minX = cx - halfW
        minY = cy - halfH
        maxX = cx + halfW
        maxY = cy + halfH
    }

    fun intersects(other: MutableAabb): Boolean =
        maxX >= other.minX && minX <= other.maxX &&
            maxY >= other.minY && minY <= other.maxY
}

/** Mutable 2D vector buffer for collision math. */
class MutableVector2 {
    var x: Float = 0f
    var y: Float = 0f

    fun set(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    fun zero() {
        x = 0f
        y = 0f
    }
}

/**
 * Pre-allocated convex polygon. [count] tracks the number of valid vertices in [xs]/[ys].
 * Capacity is fixed at construction; refresh code writes vertices in place.
 */
class MutablePolygon(capacity: Int) {
    val xs: FloatArray = FloatArray(capacity)
    val ys: FloatArray = FloatArray(capacity)
    var count: Int = 0
}

/** Mutable narrow-phase result. [valid] tells the caller whether [nx]/[ny]/[penetrationDepth] are populated. */
class CollisionManifold {
    var nx: Float = 0f
    var ny: Float = 0f
    var penetrationDepth: Float = 0f
    var valid: Boolean = false

    fun clear() {
        nx = 0f
        ny = 0f
        penetrationDepth = 0f
        valid = false
    }

    fun set(nx: Float, ny: Float, depth: Float) {
        this.nx = nx
        this.ny = ny
        this.penetrationDepth = depth
        this.valid = true
    }
}

/**
 * Anything the broad/narrow phase can operate on.
 *
 * - [boundingBox] is refreshed once per frame (when [dirty]).
 * - [convexHulls] is empty for circle/arch (which use specialised dispatch); other shapes pre-allocate
 *   one or more polygons and rewrite the vertices in place when [dirty].
 * - [normal] is a scratch buffer for response code; safe to overwrite per-pair.
 * - [isSmall] is a fast circle hint; when both bodies report `true`, narrow phase short-circuits to a
 *   squared-distance check.
 */
interface Collidable {
    val id: Long
    val type: ShapeType
    val isSmall: Boolean
    val collisionRadius: Float
    val boundingBox: MutableAabb
    val convexHulls: Array<MutablePolygon>
    val normal: MutableVector2
    var dirty: Boolean

    fun refreshAabb()
    fun refreshHulls()
}

/** Convenience: refresh both caches when dirty, then clear the flag. */
internal fun Collidable.refreshCachesIfDirty() {
    if (!dirty) return
    refreshAabb()
    refreshHulls()
    dirty = false
}
