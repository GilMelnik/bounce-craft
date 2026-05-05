package com.colorbounce.baby

import kotlin.math.max
import kotlin.math.min

/**
 * Applies position correction + impulse to a colliding pair, mutating the [GameShape] bodies in
 * place. Stateless beyond the wall-reflection helper buffer; the viewmodel decides which body counts
 * as immovable each call.
 *
 * Behavior split (matches the spec choice):
 *  - Both immovable: nothing to do.
 *  - One immovable: full positional push on the movable side and wall reflection
 *    (`v - 2(v·n) n`) on its velocity.
 *  - Both free: mass-weighted elastic exchange — same physics the project shipped with, just
 *    refactored to mutate bodies instead of producing copies.
 */
class CollisionResolver {

    fun resolve(
        a: GameShape,
        b: GameShape,
        manifold: CollisionManifold,
        aImmovable: Boolean,
        bImmovable: Boolean,
        maxSpeed: Float,
        screenW: Float,
        screenH: Float
    ) {
        if (!manifold.valid) return
        if (aImmovable && bImmovable) return

        val nx = manifold.nx
        val ny = manifold.ny
        val overlap = manifold.penetrationDepth + POSITION_SEPARATION_SLOP_PX

        when {
            aImmovable -> reflectIntoStatic(b, nx, ny, overlap, maxSpeed, screenW, screenH)
            bImmovable -> reflectIntoStatic(a, -nx, -ny, overlap, maxSpeed, screenW, screenH)
            else -> exchangeFree(a, b, nx, ny, overlap, maxSpeed, screenW, screenH)
        }
    }

    /**
     * Movable body bounces off an immovable surface. Push the movable body along (nx, ny) by the full
     * overlap (penetration depth), then reflect velocity by `v - 2(v·n)n`. Caller passes (nx, ny)
     * pointing FROM the static surface TOWARD this body, so a positive push separates them.
     */
    private fun reflectIntoStatic(
        body: GameShape,
        nx: Float,
        ny: Float,
        overlap: Float,
        maxSpeed: Float,
        screenW: Float,
        screenH: Float
    ) {
        body.x += nx * overlap
        body.y += ny * overlap
        val vDotN = body.vx * nx + body.vy * ny
        body.vx -= 2f * vDotN * nx
        body.vy -= 2f * vDotN * ny
        clampVelocity(body, maxSpeed)
        keepInside(body, screenW, screenH)
    }

    private fun exchangeFree(
        a: GameShape,
        b: GameShape,
        nx: Float,
        ny: Float,
        overlap: Float,
        maxSpeed: Float,
        screenW: Float,
        screenH: Float
    ) {
        // Position correction split 50/50.
        a.x -= nx * overlap * 0.5f
        a.y -= ny * overlap * 0.5f
        b.x += nx * overlap * 0.5f
        b.y += ny * overlap * 0.5f

        // Mass-weighted elastic exchange — area as a proxy for mass (preserves prior behavior).
        val massA = a.width * a.height
        val massB = b.width * b.height
        val totalMass = massA + massB
        if (totalMass <= 0f) return
        val aVn = a.vx * nx + a.vy * ny
        val bVn = b.vx * nx + b.vy * ny
        val aTx = a.vx - aVn * nx
        val aTy = a.vy - aVn * ny
        val bTx = b.vx - bVn * nx
        val bTy = b.vy - bVn * ny
        val aVnNew = ((massA - massB) / totalMass) * aVn + (2f * massB / totalMass) * bVn
        val bVnNew = (2f * massA / totalMass) * aVn + ((massB - massA) / totalMass) * bVn
        a.vx = aTx + aVnNew * nx
        a.vy = aTy + aVnNew * ny
        b.vx = bTx + bVnNew * nx
        b.vy = bTy + bVnNew * ny
        clampVelocity(a, maxSpeed)
        clampVelocity(b, maxSpeed)
        keepInside(a, screenW, screenH)
        keepInside(b, screenW, screenH)
    }

    private fun clampVelocity(body: GameShape, maxSpeed: Float) {
        if (maxSpeed <= 0f) {
            body.vx = 0f; body.vy = 0f
            return
        }
        val speed2 = body.vx * body.vx + body.vy * body.vy
        if (speed2 <= maxSpeed * maxSpeed) return
        val speed = kotlin.math.sqrt(speed2.toDouble()).toFloat()
        if (speed <= 0f) return
        val scale = maxSpeed / speed
        body.vx *= scale
        body.vy *= scale
    }

    private fun keepInside(body: GameShape, screenW: Float, screenH: Float) {
        val halfW = body.width / 2f
        val halfH = body.height / 2f
        body.x = min(max(body.x, halfW), screenW - halfW)
        body.y = min(max(body.y, halfH), screenH - halfH)
    }
}
