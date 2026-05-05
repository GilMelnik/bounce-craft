package com.colorbounce.baby

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Uniform-grid broad phase. Each cell holds a packed list of body indices. Bodies whose AABB spans
 * multiple cells are inserted into every covered cell; the visited matrix in [forEachCandidatePair]
 * dedupes pairs that surface from more than one cell.
 *
 * All storage is reused across ticks. [rebuild] grows the cell array if the screen got bigger but
 * never shrinks below the previous capacity, so steady-state operation has zero allocations.
 */
class SpatialGrid(private val cellSize: Float = DEFAULT_GRID_CELL_SIZE) {

    private var cols: Int = 0
    private var rows: Int = 0
    private var cellCounts: IntArray = IntArray(0)
    private var cellEntries: IntArray = IntArray(0)
    private var cellStarts: IntArray = IntArray(0)

    private var bodies: List<GameShape> = emptyList()

    private val visited: BooleanArray = BooleanArray(MAX_BODIES * MAX_BODIES)
    private val visitedKeys: IntArray = IntArray(MAX_BODIES * MAX_BODIES)
    private var visitedCount: Int = 0

    /**
     * Resets the grid for [bodies] confined to a [screenW] x [screenH] play area, and inserts each
     * body into every cell touched by its [GameShape.boundingBox]. Bodies must already have refreshed
     * AABBs.
     */
    fun rebuild(bodies: List<GameShape>, screenW: Float, screenH: Float) {
        this.bodies = bodies
        val n = bodies.size
        cols = max1(ceil(screenW / cellSize).toInt())
        rows = max1(ceil(screenH / cellSize).toInt())
        val totalCells = cols * rows
        if (cellCounts.size < totalCells) {
            cellCounts = IntArray(totalCells)
        } else {
            for (i in 0 until totalCells) cellCounts[i] = 0
        }
        if (n == 0) return

        // First pass: count entries per cell.
        var totalEntries = 0
        for (i in 0 until n) {
            val b = bodies[i].boundingBox
            val c0 = cellCol(b.minX)
            val c1 = cellCol(b.maxX)
            val r0 = cellRow(b.minY)
            val r1 = cellRow(b.maxY)
            for (r in r0..r1) {
                for (c in c0..c1) {
                    cellCounts[r * cols + c]++
                    totalEntries++
                }
            }
        }

        if (cellEntries.size < totalEntries) cellEntries = IntArray(totalEntries)
        if (cellStarts.size < totalCells) cellStarts = IntArray(totalCells)

        // Prefix sum — cellStarts[k] is the offset into cellEntries for cell k.
        var running = 0
        for (k in 0 until totalCells) {
            cellStarts[k] = running
            running += cellCounts[k]
            cellCounts[k] = 0
        }

        // Second pass: fill entries.
        for (i in 0 until n) {
            val b = bodies[i].boundingBox
            val c0 = cellCol(b.minX)
            val c1 = cellCol(b.maxX)
            val r0 = cellRow(b.minY)
            val r1 = cellRow(b.maxY)
            for (r in r0..r1) {
                for (c in c0..c1) {
                    val k = r * cols + c
                    cellEntries[cellStarts[k] + cellCounts[k]] = i
                    cellCounts[k]++
                }
            }
        }
    }

    /**
     * Iterates every pair of bodies that share a cell or sit in adjacent cells. Each pair is yielded
     * at most once per tick; [action] receives the underlying [GameShape] instances.
     */
    fun forEachCandidatePair(action: (GameShape, GameShape) -> Unit) {
        clearVisited()
        val cs = cols
        val rs = rows
        for (r in 0 until rs) {
            for (c in 0 until cs) {
                val cellIdx = r * cs + c
                val start = cellStarts[cellIdx]
                val count = cellCounts[cellIdx]
                if (count == 0) continue
                // Pairs within this cell.
                for (a in 0 until count - 1) {
                    val ia = cellEntries[start + a]
                    for (b in a + 1 until count) {
                        val ib = cellEntries[start + b]
                        if (visitOnce(ia, ib)) {
                            action(bodies[ia], bodies[ib])
                        }
                    }
                }
                // Pairs across the half-neighborhood: right, bottom-left, bottom, bottom-right.
                visitNeighborPairs(c, r, c + 1, r, action)
                visitNeighborPairs(c, r, c - 1, r + 1, action)
                visitNeighborPairs(c, r, c, r + 1, action)
                visitNeighborPairs(c, r, c + 1, r + 1, action)
            }
        }
    }

    private fun visitNeighborPairs(
        cA: Int,
        rA: Int,
        cB: Int,
        rB: Int,
        action: (GameShape, GameShape) -> Unit
    ) {
        if (cB < 0 || cB >= cols || rB < 0 || rB >= rows) return
        val aIdx = rA * cols + cA
        val bIdx = rB * cols + cB
        val aStart = cellStarts[aIdx]
        val aCount = cellCounts[aIdx]
        val bStart = cellStarts[bIdx]
        val bCount = cellCounts[bIdx]
        if (aCount == 0 || bCount == 0) return
        for (a in 0 until aCount) {
            val ia = cellEntries[aStart + a]
            for (b in 0 until bCount) {
                val ib = cellEntries[bStart + b]
                if (ia == ib) continue
                if (visitOnce(ia, ib)) {
                    action(bodies[ia], bodies[ib])
                }
            }
        }
    }

    private fun visitOnce(a: Int, b: Int): Boolean {
        val lo = if (a < b) a else b
        val hi = if (a < b) b else a
        if (hi >= MAX_BODIES) return true
        val key = lo * MAX_BODIES + hi
        if (visited[key]) return false
        visited[key] = true
        if (visitedCount < visitedKeys.size) {
            visitedKeys[visitedCount] = key
            visitedCount++
        }
        return true
    }

    private fun clearVisited() {
        for (i in 0 until visitedCount) visited[visitedKeys[i]] = false
        visitedCount = 0
    }

    private fun cellCol(x: Float): Int {
        val c = floor(x / cellSize).toInt()
        return c.coerceIn(0, cols - 1)
    }

    private fun cellRow(y: Float): Int {
        val r = floor(y / cellSize).toInt()
        return r.coerceIn(0, rows - 1)
    }

    private fun max1(v: Int): Int = if (v < 1) 1 else v
}
