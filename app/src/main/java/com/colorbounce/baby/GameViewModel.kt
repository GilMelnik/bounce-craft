package com.colorbounce.baby

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
    private var lastType = ShapeType.RECTANGLE
    private var activeShapeId: Long? = null
    private var lastDragDelta = Offset.Zero
    private var screenSize = Offset(1f, 1f)

    fun setScreenSize(width: Float, height: Float) {
        if (width > 0f && height > 0f) {
            screenSize = Offset(width, height)
        }
    }

    // Touch handling:
    // startInteraction either grabs an existing shape or creates a new one at the press point.
    fun startInteraction(point: Offset, settings: AppSettings) {
        val current = _shapes.value
        val hit = current.lastOrNull { pointInShape(point, it) }
        if (hit != null) {
            activeShapeId = hit.id
            touchShape(hit.id)
            return
        }

        val newType = chooseType(settings.shapeMode)
        val size = 70f
        val now = System.currentTimeMillis()
        val newShape = GameShape(
            id = nextId++,
            type = newType,
            x = point.x,
            y = point.y,
            width = size,
            height = size,
            vx = 0f,
            vy = 0f,
            color = randomBrightColor(),
            lastInteractionMillis = now
        )
        activeShapeId = newShape.id

        _shapes.value = (current + newShape).takeLast(settings.maxShapes)
    }

    // During drag, shape follows the pointer and updates size/velocity hints + color.
    fun onDrag(point: Offset, dragAmount: Offset, startPoint: Offset, settings: AppSettings) {
        val shapeId = activeShapeId ?: return
        lastDragDelta = dragAmount
        val dragDistance = (point - startPoint).getDistance()
        val computedSize = dragDistance.coerceIn(40f, 220f)
        val now = System.currentTimeMillis()
        _shapes.value = _shapes.value.map { shape ->
            if (shape.id != shapeId) return@map shape
            val isNewish = shape.vx == 0f && shape.vy == 0f
            shape.copy(
                x = point.x,
                y = point.y,
                width = if (isNewish) computedSize else shape.width,
                height = if (isNewish) computedSize else shape.height,
                color = randomBrightColor(),
                lastInteractionMillis = now
            )
        }
        trimToMax(settings.maxShapes)
    }

    fun endInteraction() {
        val shapeId = activeShapeId ?: return
        val launchFactor = 28f
        _shapes.value = _shapes.value.map {
            if (it.id == shapeId) {
                it.copy(
                    vx = lastDragDelta.x * launchFactor,
                    vy = lastDragDelta.y * launchFactor,
                    lastInteractionMillis = System.currentTimeMillis()
                )
            } else {
                it
            }
        }
        activeShapeId = null
        lastDragDelta = Offset.Zero
    }

    // Physics update:
    // moves shapes, handles edge bounces, then resolves basic pair collisions.
    fun updatePhysics(deltaSeconds: Float, settings: AppSettings) {
        if (deltaSeconds <= 0f) return
        val width = screenSize.x
        val height = screenSize.y
        val timeoutMs = settings.shapeTimeoutSeconds * 1000L
        val now = System.currentTimeMillis()

        val moved = _shapes.value.mapNotNull { shape ->
            if (now - shape.lastInteractionMillis > timeoutMs) return@mapNotNull null

            var x = shape.x + shape.vx * deltaSeconds
            var y = shape.y + shape.vy * deltaSeconds
            var vx = shape.vx
            var vy = shape.vy

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

            shape.copy(x = x, y = y, vx = vx, vy = vy)
        }.toMutableList()

        resolvePairCollisions(moved)
        _shapes.value = moved.takeLast(settings.maxShapes)
    }

    private fun resolvePairCollisions(shapes: MutableList<GameShape>) {
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

                val aX = a.x - nx * overlap * 0.5f
                val aY = a.y - ny * overlap * 0.5f
                val bX = b.x + nx * overlap * 0.5f
                val bY = b.y + ny * overlap * 0.5f

                // Simple elastic approximation by swapping normal velocity components.
                val aVn = a.vx * nx + a.vy * ny
                val bVn = b.vx * nx + b.vy * ny
                val aTx = a.vx - aVn * nx
                val aTy = a.vy - aVn * ny
                val bTx = b.vx - bVn * nx
                val bTy = b.vy - bVn * ny

                val newA = a.copy(
                    x = aX,
                    y = aY,
                    vx = aTx + bVn * nx,
                    vy = aTy + bVn * ny
                )
                val newB = b.copy(
                    x = bX,
                    y = bY,
                    vx = bTx + aVn * nx,
                    vy = bTy + aVn * ny
                )
                shapes[i] = keepInside(newA)
                shapes[j] = keepInside(newB)
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

    private fun touchShape(id: Long) {
        val now = System.currentTimeMillis()
        _shapes.value = _shapes.value.map {
            if (it.id == id) it.copy(color = randomBrightColor(), lastInteractionMillis = now) else it
        }
    }

    private fun chooseType(mode: ShapeMode): ShapeType {
        return when (mode) {
            ShapeMode.CIRCLE_ONLY -> ShapeType.CIRCLE
            ShapeMode.RECTANGLE_ONLY -> ShapeType.RECTANGLE
            ShapeMode.ALTERNATING -> {
                lastType = if (lastType == ShapeType.CIRCLE) ShapeType.RECTANGLE else ShapeType.CIRCLE
                lastType
            }
            ShapeMode.RANDOM -> if (Random.nextBoolean()) ShapeType.CIRCLE else ShapeType.RECTANGLE
        }
    }

    private fun pointInShape(point: Offset, shape: GameShape): Boolean {
        val dx = point.x - shape.x
        val dy = point.y - shape.y
        return if (shape.type == ShapeType.CIRCLE) {
            hypot(dx, dy) <= shape.width / 2f
        } else {
            kotlin.math.abs(dx) <= shape.width / 2f && kotlin.math.abs(dy) <= shape.height / 2f
        }
    }

    private fun randomBrightColor(): Color {
        val hue = Random.nextFloat() * 360f
        val sat = 0.65f + Random.nextFloat() * 0.35f
        val value = 0.82f + Random.nextFloat() * 0.18f
        return Color.hsv(hue, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    }
}
