package com.colorbounce.baby

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class GameViewModel : ViewModel() {
    private val _shapes = MutableStateFlow<List<GameShape>>(emptyList())
    val shapes: StateFlow<List<GameShape>> = _shapes.asStateFlow()

    private var nextId = 1L
    private var lastTypeIndex = 0
    private val activeShapes = mutableMapOf<Long, Long>() // pointerId to shapeId
    private val startPoints = mutableMapOf<Long, Offset>()
    private val lastDragDeltas = mutableMapOf<Long, Offset>()
    private var screenSize = Offset(1f, 1f)

    private var lastUserInteractionMillis = System.currentTimeMillis()
    private var lastAutoSpawnMillis = System.currentTimeMillis()

    fun setScreenSize(width: Float, height: Float) {
        if (width > 0f && height > 0f) {
            screenSize = Offset(width, height)
        }
    }

    fun startInteraction(point: Offset, settings: AppSettings, pointerId: Long) {
        recordInteraction()
        val current = _shapes.value
        val hit = current.lastOrNull { pointInShape(point, it) }
        if (hit != null) {
            activeShapes[pointerId] = hit.id
            startPoints[pointerId] = point
            updateInteractionTime(hit.id)
            return
        }

        val newType = chooseType(settings.selectedShapes, settings.shapeSelectionMode)
        val size = 70f
        val now = System.currentTimeMillis()
        val hue = Random.nextFloat() * 360f
        val newShape = GameShape(
            id = nextId++,
            type = newType,
            x = point.x,
            y = point.y,
            width = size,
            height = size,
            vx = 0f,
            vy = 0f,
            hue = hue,
            saturation = calmSaturation(),
            value = calmValue(),
            lastInteractionMillis = now
        )
        activeShapes[pointerId] = newShape.id
        startPoints[pointerId] = point

        _shapes.value = (current + newShape).takeLast(settings.maxShapes)
    }

    fun onDrag(point: Offset, dragAmount: Offset, settings: AppSettings, pointerId: Long) {
        recordInteraction()
        val shapeId = activeShapes[pointerId] ?: return
        lastDragDeltas[pointerId] = dragAmount
        val startPoint = startPoints[pointerId] ?: return
        val dragDistance = (point - startPoint).getDistance()
        val computedSize = dragDistance.coerceIn(40f, 500f)
        val now = System.currentTimeMillis()
        _shapes.value = _shapes.value.map { shape ->
            if (shape.id != shapeId) return@map shape
            val isNewish = shape.vx == 0f && shape.vy == 0f
            shape.copy(
                x = point.x,
                y = point.y,
                width = if (isNewish) computedSize else shape.width,
                height = if (isNewish) computedSize else shape.height,
                lastInteractionMillis = now
            )
        }
        trimToMax(settings.maxShapes)
    }

    fun endInteraction(settings: AppSettings, pointerId: Long) {
        recordInteraction()
        val shapeId = activeShapes[pointerId] ?: return
        val rawVx = lastDragDeltas[pointerId]!!.x * ShapeVelocity.LAUNCH_DRAG_FACTOR
        val rawVy = lastDragDeltas[pointerId]!!.y * ShapeVelocity.LAUNCH_DRAG_FACTOR
        val (vx, vy) = ShapeVelocity.clamp(rawVx, rawVy, settings.maxVelocityPxPerSec.toFloat())
        _shapes.value = _shapes.value.map {
            if (it.id == shapeId) {
                it.copy(
                    vx = vx,
                    vy = vy,
                    lastInteractionMillis = System.currentTimeMillis()
                )
            } else {
                it
            }
        }
        activeShapes.remove(pointerId)
        lastDragDeltas.remove(pointerId)
        startPoints.remove(pointerId)
    }

    fun updatePhysics(deltaSeconds: Float, settings: AppSettings) {
        if (deltaSeconds <= 0f) return
        val width = screenSize.x
        val height = screenSize.y
        val timeoutMs = settings.shapeTimeoutSeconds * 1000L
        val now = System.currentTimeMillis()

        checkAutoSpawn(now, settings)

        val activeIds = activeShapes.values.toSet()
        val moved = _shapes.value.mapNotNull { shape ->
            if (now - shape.lastInteractionMillis > timeoutMs) return@mapNotNull null

            val isHeld = activeIds.contains(shape.id)
            var x = shape.x
            var y = shape.y
            var vx = shape.vx
            var vy = shape.vy

            if (!isHeld) {
                x = shape.x + shape.vx * deltaSeconds
                y = shape.y + shape.vy * deltaSeconds

                val halfW = shape.width / 2f
                val halfH = shape.height / 2f

                if (x - halfW < 0f) {
                    x = halfW
                    vx = kotlin.math.abs(vx)
                } else if (x + halfW > width) {
                    x = width - halfW
                    vx = -kotlin.math.abs(vx)
                }
                if (y - halfH < 0f) {
                    y = halfH
                    vy = kotlin.math.abs(vy)
                } else if (y + halfH > height) {
                    y = height - halfH
                    vy = -kotlin.math.abs(vy)
                }
            }

            // Continuous hue cycling while held OR creating
            val hue = if (isHeld) {
                ShapeColorAnimator.stepHue(shape.hue, deltaSeconds)
            } else {
                shape.hue
            }

            val (cvx, cvy) = ShapeVelocity.clamp(vx, vy, settings.maxVelocityPxPerSec.toFloat())
            shape.copy(
                x = x,
                y = y,
                vx = cvx,
                vy = cvy,
                hue = hue
            )
        }.toMutableList()

        resolvePairCollisions(moved, settings)
        _shapes.value = moved.takeLast(settings.maxShapes)
    }

    private fun checkAutoSpawn(now: Long, settings: AppSettings) {
        val inactivityTimeoutMs = settings.autoSpawnInactivitySeconds * 1000L
        if (now - lastUserInteractionMillis > inactivityTimeoutMs) {
            if (now - lastAutoSpawnMillis > inactivityTimeoutMs) {
                spawnRandomShape(settings)
                lastAutoSpawnMillis = now
            }
        } else {
            lastAutoSpawnMillis = now
        }
    }

    private fun spawnRandomShape(settings: AppSettings) {
        if (_shapes.value.size >= settings.maxShapes) return

        val width = screenSize.x
        val height = screenSize.y
        val size = Random.nextFloat() * (150f - 60f) + 60f
        val margin = size / 2f
        
        val rx = Random.nextFloat() * (width - 2 * margin) + margin
        val ry = Random.nextFloat() * (height - 2 * margin) + margin
        
        val maxSpeed = settings.maxVelocityPxPerSec.toFloat() * 0.5f
        val rvx = (Random.nextFloat() - 0.5f) * 2f * maxSpeed
        val rvy = (Random.nextFloat() - 0.5f) * 2f * maxSpeed
        
        val newShape = GameShape(
            id = nextId++,
            type = chooseType(settings.selectedShapes, settings.shapeSelectionMode),
            x = rx,
            y = ry,
            width = size,
            height = size,
            vx = rvx,
            vy = rvy,
            hue = Random.nextFloat() * 360f,
            saturation = calmSaturation(),
            value = calmValue(),
            lastInteractionMillis = System.currentTimeMillis()
        )
        _shapes.value = (_shapes.value + newShape).takeLast(settings.maxShapes)
    }

    private fun recordInteraction() {
        lastUserInteractionMillis = System.currentTimeMillis()
    }

    private fun resolvePairCollisions(shapes: MutableList<GameShape>, settings: AppSettings) {
        val heldIds = activeShapes.values.toSet()
        for (i in 0 until shapes.size) {
            for (j in i + 1 until shapes.size) {
                val a = shapes[i]
                val b = shapes[j]

                val dx = b.x - a.x
                val dy = b.y - a.y
                val distance = max(1f, hypot(dx, dy))
                val ra = max(a.width, a.height) / 2f
                val rb = max(b.width, b.height) / 2f
                val minDist = ra + rb
                if (distance >= minDist) continue

                val nx = dx / distance
                val ny = dy / distance
                val overlap = minDist - distance

                when {
                    a.id in heldIds -> {
                        val bX = b.x + nx * overlap
                        val bY = b.y + ny * overlap
                        val bVn = b.vx * nx + b.vy * ny
                        val bTx = b.vx - bVn * nx
                        val bTy = b.vy - bVn * ny
                        var newB = b.copy(
                            x = bX,
                            y = bY,
                            vx = bTx - bVn * nx,
                            vy = bTy - bVn * ny
                        )
                        val bc = ShapeVelocity.clamp(newB.vx, newB.vy, settings.maxVelocityPxPerSec.toFloat())
                        newB = newB.copy(vx = bc.first, vy = bc.second)
                        shapes[i] = keepInside(a)
                        shapes[j] = keepInside(newB)
                    }
                    b.id in heldIds -> {
                        val aX = a.x - nx * overlap
                        val aY = a.y - ny * overlap
                        val aVn = a.vx * nx + a.vy * ny
                        val aTx = a.vx - aVn * nx
                        val aTy = a.vy - aVn * ny
                        var newA = a.copy(
                            x = aX,
                            y = aY,
                            vx = aTx - aVn * nx,
                            vy = aTy - aVn * ny
                        )
                        val ac = ShapeVelocity.clamp(newA.vx, newA.vy, settings.maxVelocityPxPerSec.toFloat())
                        newA = newA.copy(vx = ac.first, vy = ac.second)
                        shapes[i] = keepInside(newA)
                        shapes[j] = keepInside(b)
                    }
                    else -> {
                        val massA = a.width * a.height
                        val massB = b.width * b.height
                        val totalMass = massA + massB

                        val aX = a.x - nx * overlap * 0.5f
                        val aY = a.y - ny * overlap * 0.5f
                        val bX = b.x + nx * overlap * 0.5f
                        val bY = b.y + ny * overlap * 0.5f

                        val aVn = a.vx * nx + a.vy * ny
                        val bVn = b.vx * nx + b.vy * ny
                        val aTx = a.vx - aVn * nx
                        val aTy = a.vy - aVn * ny
                        val bTx = b.vx - bVn * nx
                        val bTy = b.vy - bVn * ny

                        val aVnNew = ((massA - massB) / totalMass) * aVn + (2f * massB / totalMass) * bVn
                        val bVnNew = (2f * massA / totalMass) * aVn + ((massB - massA) / totalMass) * bVn

                        var newA = a.copy(
                            x = aX,
                            y = aY,
                            vx = aTx + aVnNew * nx,
                            vy = aTy + aVnNew * ny
                        )
                        var newB = b.copy(
                            x = bX,
                            y = bY,
                            vx = bTx + bVnNew * nx,
                            vy = bTy + bVnNew * ny
                        )
                        val ac = ShapeVelocity.clamp(newA.vx, newA.vy, settings.maxVelocityPxPerSec.toFloat())
                        val bc = ShapeVelocity.clamp(newB.vx, newB.vy, settings.maxVelocityPxPerSec.toFloat())
                        newA = newA.copy(vx = ac.first, vy = ac.second)
                        newB = newB.copy(vx = bc.first, vy = bc.second)
                        shapes[i] = keepInside(newA)
                        shapes[j] = keepInside(newB)
                    }
                }
            }
        }
    }

    private fun keepInside(shape: GameShape): GameShape {
        val halfW = shape.width / 2f
        val halfH = shape.height / 2f
        return shape.copy(
            x = min(max(shape.x, halfW), screenSize.x - halfW),
            y = min(max(shape.y, halfH), screenSize.y - halfH)
        )
    }

    private fun trimToMax(maxShapes: Int) {
        _shapes.value = _shapes.value.takeLast(maxShapes.coerceAtLeast(1))
    }

    private fun updateInteractionTime(id: Long) {
        val now = System.currentTimeMillis()
        _shapes.value = _shapes.value.map {
            if (it.id == id) {
                it.copy(lastInteractionMillis = now)
            } else {
                it
            }
        }
    }

    private fun chooseType(selectedShapes: Set<ShapeType>, selectionMode: ShapeSelectionMode): ShapeType {
        val shapesList = selectedShapes.toList()
        if (shapesList.isEmpty()) return ShapeType.CIRCLE // fallback
        return when (selectionMode) {
            ShapeSelectionMode.ALTERNATE -> {
                val type = shapesList[lastTypeIndex % shapesList.size]
                lastTypeIndex = (lastTypeIndex + 1) % shapesList.size
                type
            }
            ShapeSelectionMode.RANDOM -> shapesList.random()
        }
    }

    private fun pointInShape(point: Offset, shape: GameShape): Boolean {
        val dx = point.x - shape.x
        val dy = point.y - shape.y
        return kotlin.math.abs(dx) <= shape.width / 2f && kotlin.math.abs(dy) <= shape.height / 2f
    }

    private fun calmSaturation(): Float = (0.55f + Random.nextFloat() * 0.18f).coerceIn(0f, 1f)

    private fun calmValue(): Float = (0.78f + Random.nextFloat() * 0.14f).coerceIn(0f, 1f)
}
