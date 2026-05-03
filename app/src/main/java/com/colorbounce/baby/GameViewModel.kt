package com.colorbounce.baby

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "GameViewModel"

class GameViewModel : ViewModel() {
    private val _shapes = MutableStateFlow<List<GameShape>>(emptyList())
    val shapes: StateFlow<List<GameShape>> = _shapes.asStateFlow()

    private var nextId = 1L
    private val lastTypeIndex = AtomicInteger(0)
    private val activeShapes = mutableMapOf<Long, Long>() // pointerId to shapeId
    private val startPoints = mutableMapOf<Long, Offset>()
    private val lastDragDeltas = mutableMapOf<Long, Offset>()
    /**
     * Shape ids still in the finger-down “draw” gesture (before [endInteraction]).
     * Resize-on-drag applies only while id is in this set — not after creation ends, even if vx/vy are zero.
     * During this window, ruler “pin all” does not block resizing; pin takes effect when the gesture ends.
     */
    private val fingerCreatedShapeIds = mutableSetOf<Long>()
    private var screenSize = Offset(1f, 1f)

    private var gameTimeMillis = 0L
    private var gameTimeRemainderMillis = 0f
    private var lastUserInteractionGameMillis = 0L
    private var lastAutoSpawnGameMillis = 0L

    /** Normal play: freezes physics while the shape context menu is open (double-tap). */
    private var physicsPausedForShapeContextMenu = false

    fun setShapeContextMenuOpen(open: Boolean) {
        physicsPausedForShapeContextMenu = open
    }

    private val _creationAtCapacity = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val creationAtCapacity: SharedFlow<Unit> = _creationAtCapacity.asSharedFlow()

    fun onGameExit() {
        try {
            physicsPausedForShapeContextMenu = false
            clearInteractionState()
            resetAutoSpawnTimers()
            Log.d(TAG, "Game exited at gameTime=$gameTimeMillis")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onGameExit", e)
        }
    }

    fun onGameEnter() {
        try {
            resetAutoSpawnTimers()
            Log.d(TAG, "Game entered at gameTime=$gameTimeMillis")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onGameEnter", e)
        }
    }

    fun setScreenSize(width: Float, height: Float) {
        try {
            if (width > 0f && height > 0f) {
                screenSize = Offset(width, height)
                Log.d(TAG, "Screen size set to: ${width}x${height}")
            } else {
                Log.w(TAG, "Invalid screen size: width=$width, height=$height")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting screen size", e)
        }
    }

    private fun effectiveMaxShapes(creation: CreationSession?, settings: AppSettings): Int =
        if (creation != null) CREATION_MAX_SHAPES.coerceIn(1, 10_000)
        else settings.maxShapes.coerceIn(1, 100)

    fun shapeAt(point: Offset): GameShape? = _shapes.value.lastOrNull { pointInShape(point, it) }

    fun activeShapeIdFor(pointerId: Long): Long? = activeShapes[pointerId]

    fun removeShape(id: Long) {
        _shapes.value = _shapes.value.filter { it.id != id }
        val toRemove = activeShapes.filter { it.value == id }.keys
        toRemove.forEach { cleanupPointer(it) }
        fingerCreatedShapeIds.remove(id)
    }

    /** Drops stale pointer capture for [shapeId] (e.g. shape menu overlay gesture cancelled mid-drag). */
    fun clearPointersTargetingShape(shapeId: Long) {
        val pointerIds = activeShapes.filter { it.value == shapeId }.keys.toList()
        pointerIds.forEach { cleanupPointer(it) }
        fingerCreatedShapeIds.remove(shapeId)
    }

    fun setShapePinned(id: Long, pinned: Boolean) {
        _shapes.value = _shapes.value.map { if (it.id == id) it.copy(isPinned = pinned) else it }
    }

    fun setShapeImmortal(id: Long, immortal: Boolean) {
        _shapes.value = _shapes.value.map { if (it.id == id) it.copy(isImmortal = immortal) else it }
    }

    fun setShapeExemptFromGlobalPin(id: Long, exempt: Boolean) {
        _shapes.value = _shapes.value.map {
            if (it.id == id) it.copy(exemptFromGlobalPin = exempt) else it
        }
    }

    fun setShapeExemptFromGlobalImmortal(id: Long, exempt: Boolean) {
        _shapes.value = _shapes.value.map {
            if (it.id == id) it.copy(exemptFromGlobalImmortal = exempt) else it
        }
    }

    /** Ruler pin toggle: applies to every shape; clears per-shape exemptions. */
    fun applyGlobalPinFromRuler(allPinned: Boolean) {
        _shapes.value = _shapes.value.map {
            it.copy(
                exemptFromGlobalPin = false,
                isPinned = allPinned
            )
        }
    }

    /** Ruler lifetime toggle: applies to every shape; clears per-shape exemptions. */
    fun applyGlobalImmortalFromRuler(allImmortal: Boolean) {
        _shapes.value = _shapes.value.map {
            it.copy(
                exemptFromGlobalImmortal = false,
                isImmortal = allImmortal
            )
        }
    }

    private fun effectiveIsPinned(shape: GameShape, creation: CreationSession?): Boolean {
        if (creation == null) return shape.isPinned
        return if (creation.newShapesPinned) {
            !shape.exemptFromGlobalPin
        } else {
            shape.isPinned
        }
    }

    private fun effectiveIsImmortal(shape: GameShape, creation: CreationSession?, settings: AppSettings): Boolean {
        val globalImmortal = settings.shapeTimeoutImmortal || (creation?.newShapesImmortal == true)
        return if (globalImmortal) {
            !shape.exemptFromGlobalImmortal
        } else {
            shape.isImmortal
        }
    }

    fun setShapeFreezeHueWhileDragging(id: Long, freeze: Boolean) {
        _shapes.value = _shapes.value.map {
            if (it.id == id) it.copy(freezeHueWhileDragging = freeze) else it
        }
    }

    fun setShapeExemptFromGlobalHueLock(id: Long, exempt: Boolean) {
        _shapes.value = _shapes.value.map {
            if (it.id == id) it.copy(exemptFromGlobalHueLock = exempt) else it
        }
    }

    private fun hueFrozenWhileDragging(
        creation: CreationSession?,
        shape: GameShape,
        isHeld: Boolean
    ): Boolean {
        val global = creation?.disableHueWhileDragging == true
        return if (global) {
            !shape.exemptFromGlobalHueLock
        } else {
            isHeld && shape.freezeHueWhileDragging
        }
    }

    fun startInteraction(
        point: Offset,
        settings: AppSettings,
        pointerId: Long,
        constrainInsideScreen: Boolean = false,
        creation: CreationSession? = null
    ) {
        try {
            recordInteraction()
            val current = _shapes.value
            val hit = current.lastOrNull { pointInShape(point, it) }
            if (hit != null) {
                Log.d(TAG, "Interaction: Tapped existing shape id=${hit.id}")
                activeShapes[pointerId] = hit.id
                startPoints[pointerId] = point
                lastDragDeltas[pointerId] = Offset.Zero
                resetShapeLifetimeTimer(hit.id)
                return
            }

            if (creation != null && current.size >= effectiveMaxShapes(creation, settings)) {
                _creationAtCapacity.tryEmit(Unit)
                return
            }

            val newType = chooseType(
                if (creation != null) creation.selectedShapes else settings.selectedShapes,
                if (creation != null) creation.shapeSelectionMode else settings.shapeSelectionMode
            )
            val size = 70f
            val spawnPoint = if (constrainInsideScreen) {
                clampPointInsideScreen(point, size / 2f, size / 2f)
            } else {
                point
            }
            val now = currentGameTimeMillis()
            val (hue, sat, v) = when (val c = creation?.spawnColor) {
                null -> Triple(Random.nextFloat() * 360f, calmSaturation(), calmValue())
                else -> Triple(
                    c.first,
                    c.second.coerceIn(0f, 1f),
                    c.third.coerceIn(0f, 1f)
                )
            }
            val imm = settings.shapeTimeoutImmortal || (creation?.newShapesImmortal == true)
            val newShape = GameShape(
                id = nextId++,
                type = newType,
                x = spawnPoint.x,
                y = spawnPoint.y,
                width = size,
                height = size,
                vx = 0f,
                vy = 0f,
                hue = hue,
                saturation = sat,
                value = v,
                lastInteractionMillis = now,
                isPinned = false,
                isImmortal = imm
            )
            fingerCreatedShapeIds.add(newShape.id)
            activeShapes[pointerId] = newShape.id
            startPoints[pointerId] = point
            lastDragDeltas[pointerId] = Offset.Zero

            val em = effectiveMaxShapes(creation, settings)
            _shapes.value = (current + newShape).takeLast(em)
            Log.d(
                TAG,
                "Interaction: Created new shape id=${newShape.id}, type=$newType, pointerId=$pointerId, totalShapes=${_shapes.value.size}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in startInteraction", e)
        }
    }

    fun onDrag(
        point: Offset,
        dragAmount: Offset,
        settings: AppSettings,
        pointerId: Long,
        resizeOnDrag: Boolean = true,
        constrainInsideScreen: Boolean = false,
        creation: CreationSession? = null
    ) {
        try {
            recordInteraction()
            val shapeId = activeShapes[pointerId] ?: return
            lastDragDeltas[pointerId] = dragAmount
            val startPoint = startPoints[pointerId] ?: return
            val dragDistance = (point - startPoint).getDistance()
            val computedSize = dragDistance.coerceIn(40f, 500f)
            val now = currentGameTimeMillis()
            _shapes.value = _shapes.value.map { shape ->
                if (shape.id != shapeId) return@map shape
                // While the finger is still down on a newly spawned shape, allow resize even if
                // the ruler pins all shapes — pin semantics apply after [endInteraction] only.
                val resizingSpawnGesture =
                    resizeOnDrag && fingerCreatedShapeIds.contains(shape.id)
                val targetSize = if (resizingSpawnGesture) computedSize else shape.width
                val boundedSize = if (constrainInsideScreen) {
                    targetSize.coerceAtMost(min(screenSize.x, screenSize.y))
                } else {
                    targetSize
                }
                val boundedPoint = if (constrainInsideScreen) {
                    clampPointInsideScreen(point, boundedSize / 2f, boundedSize / 2f)
                } else {
                    point
                }
                shape.copy(
                    x = boundedPoint.x,
                    y = boundedPoint.y,
                    width = if (resizingSpawnGesture) boundedSize else shape.width,
                    height = if (resizingSpawnGesture) boundedSize else shape.height,
                    lastInteractionMillis = now
                )
            }
            trimToMax(effectiveMaxShapes(creation, settings))
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDrag for pointerId=$pointerId", e)
        }
    }

    fun endInteraction(
        settings: AppSettings,
        pointerId: Long,
        creation: CreationSession? = null,
        applyLaunchVelocity: Boolean = true
    ) {
        try {
            recordInteraction()
            val shapeId = activeShapes[pointerId] ?: return
            val dragDelta = lastDragDeltas[pointerId] ?: Offset.Zero
            fingerCreatedShapeIds.remove(shapeId)
            val shapeBeforeEnd = _shapes.value.find { it.id == shapeId }
            val wasPinned = shapeBeforeEnd != null && effectiveIsPinned(shapeBeforeEnd, creation)
            if (wasPinned) {
                _shapes.value = _shapes.value.map {
                    if (it.id == shapeId) {
                        it.copy(
                            vx = 0f,
                            vy = 0f,
                            lastInteractionMillis = currentGameTimeMillis()
                        )
                    } else {
                        it
                    }
                }
                cleanupPointer(pointerId)
                return
            }
            if (!applyLaunchVelocity) {
                _shapes.value = _shapes.value.map {
                    if (it.id == shapeId) {
                        it.copy(
                            vx = 0f,
                            vy = 0f,
                            lastInteractionMillis = currentGameTimeMillis()
                        )
                    } else {
                        it
                    }
                }
                cleanupPointer(pointerId)
                return
            }
            if (dragDelta == Offset.Zero) {
                _shapes.value = _shapes.value.map {
                    if (it.id == shapeId) {
                        it.copy(lastInteractionMillis = currentGameTimeMillis())
                    } else {
                        it
                    }
                }
                cleanupPointer(pointerId)
                return
            }
            val rawVx = dragDelta.x * ShapeVelocity.LAUNCH_DRAG_FACTOR
            val rawVy = dragDelta.y * ShapeVelocity.LAUNCH_DRAG_FACTOR
            val (vx, vy) = ShapeVelocity.clamp(rawVx, rawVy, settings.maxVelocityPxPerSec.toFloat())
            _shapes.value = _shapes.value.map {
                if (it.id == shapeId) {
                    it.copy(
                        vx = vx,
                        vy = vy,
                        lastInteractionMillis = currentGameTimeMillis()
                    )
                } else {
                    it
                }
            }
            cleanupPointer(pointerId)
            Log.d(TAG, "Interaction ended: shapeId=$shapeId, velocity=(${vx},${vy})")
        } catch (e: Exception) {
            Log.e(TAG, "Error in endInteraction for pointerId=$pointerId", e)
            cleanupPointer(pointerId)
        }
    }

    private fun cleanupPointer(pointerId: Long) {
        activeShapes.remove(pointerId)
        lastDragDeltas.remove(pointerId)
        startPoints.remove(pointerId)
    }

    fun updatePhysics(deltaSeconds: Float, settings: AppSettings, creation: CreationSession? = null) {
        try {
            if (deltaSeconds <= 0f) return
            if (deltaSeconds > 0.1f) {
                Log.w(TAG, "Large deltaSeconds detected: $deltaSeconds, clamping to 0.1f for stability")
            }

            val safeDelta = deltaSeconds.coerceIn(0.001f, 0.1f)
            val width = screenSize.x
            val height = screenSize.y

            if (width <= 0f || height <= 0f) {
                Log.w(TAG, "Invalid screen dimensions in updatePhysics: ${width}x${height}")
                return
            }

            val c = creation
            if ((c != null && c.physicsPaused) || physicsPausedForShapeContextMenu) {
                val activeIds = activeShapes.values.toSet()
                _shapes.value = _shapes.value.map { shape ->
                    if (!activeIds.contains(shape.id)) return@map shape
                    val freezeHue = hueFrozenWhileDragging(c, shape, isHeld = true)
                    val hue = if (freezeHue) {
                        shape.hue
                    } else {
                        ShapeColorAnimator.stepHue(shape.hue, safeDelta)
                    }
                    shape.copy(hue = hue)
                }
                return
            }

            val timeoutMs = settings.shapeTimeoutSeconds.coerceIn(1, 120) * 1000L
            val now = advanceGameClock(safeDelta)
            val em = effectiveMaxShapes(creation, settings)

            checkAutoSpawn(now, settings, c)

            val activeIds = activeShapes.values.toSet()
            val moved = _shapes.value.mapNotNull { shape ->
                if (!effectiveIsImmortal(shape, c, settings)) {
                    if (now - shape.lastInteractionMillis > timeoutMs) {
                        Log.d(
                            TAG,
                            "Shape id=${shape.id} expired (timeout=${settings.shapeTimeoutSeconds}s, immortal=${settings.shapeTimeoutImmortal})"
                        )
                        return@mapNotNull null
                    }
                }

                val isHeld = activeIds.contains(shape.id)
                var x = shape.x
                var y = shape.y
                var vx = shape.vx
                var vy = shape.vy

                if (!isHeld) {
                    if (effectiveIsPinned(shape, c)) {
                        x = shape.x
                        y = shape.y
                        vx = 0f
                        vy = 0f
                    } else {
                        x = shape.x + shape.vx * safeDelta
                        y = shape.y + shape.vy * safeDelta
                        val halfW = shape.width / 2f
                        val halfH = shape.height / 2f
                        if (x - halfW < 0f) {
                            x = halfW
                            vx = abs(vx)
                        } else if (x + halfW > width) {
                            x = width - halfW
                            vx = -abs(vx)
                        }
                        if (y - halfH < 0f) {
                            y = halfH
                            vy = abs(vy)
                        } else if (y + halfH > height) {
                            y = height - halfH
                            vy = -abs(vy)
                        }
                    }
                } else {
                    if (effectiveIsPinned(shape, c)) {
                        vx = 0f
                        vy = 0f
                    }
                }

                val disableHue = hueFrozenWhileDragging(c, shape, isHeld)
                val hue = if (isHeld && !disableHue) {
                    ShapeColorAnimator.stepHue(shape.hue, safeDelta)
                } else {
                    shape.hue
                }

                val (cvx, cvy) = ShapeVelocity.clamp(vx, vy, settings.maxVelocityPxPerSec.coerceIn(100, 3000).toFloat())
                shape.copy(
                    x = x,
                    y = y,
                    vx = cvx,
                    vy = cvy,
                    hue = hue
                )
            }.toMutableList()

            resolvePairCollisions(moved, settings, c)
            _shapes.value = moved.takeLast(em)
        } catch (e: Exception) {
            Log.e(TAG, "Error in updatePhysics", e)
        }
    }

    private fun checkAutoSpawn(now: Long, settings: AppSettings, creation: CreationSession?) {
        try {
            if (creation?.physicsPaused == true || physicsPausedForShapeContextMenu) return
            val inactivityTimeoutMs = settings.autoSpawnInactivitySeconds * 1000L
            if (inactivityTimeoutMs <= 0) return
            if (now - lastUserInteractionGameMillis > inactivityTimeoutMs) {
                if (now - lastAutoSpawnGameMillis > inactivityTimeoutMs) {
                    spawnRandomShape(settings, creation)
                    lastAutoSpawnGameMillis = now
                }
            } else {
                lastAutoSpawnGameMillis = now
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkAutoSpawn", e)
        }
    }

    private fun spawnRandomShape(settings: AppSettings, creation: CreationSession? = null) {
        try {
            val em = effectiveMaxShapes(creation, settings)
            if (_shapes.value.size >= em) return

            val w = screenSize.x
            val h = screenSize.y
            if (w <= 0f || h <= 0f) {
                Log.w(TAG, "Cannot spawn shape: invalid screen dimensions ${w}x${h}")
                return
            }

            val newType = chooseType(
                if (creation != null) creation.selectedShapes else settings.selectedShapes,
                if (creation != null) creation.shapeSelectionMode else settings.shapeSelectionMode
            )
            val size = Random.nextFloat() * (150f - 60f) + 60f
            val margin = size / 2f
            val rx = Random.nextFloat() * (w - 2 * margin) + margin
            val ry = Random.nextFloat() * (h - 2 * margin) + margin
            val maxSpeed = settings.maxVelocityPxPerSec.toFloat() * 0.5f
            val rvx = (Random.nextFloat() - 0.5f) * 2f * maxSpeed
            val rvy = (Random.nextFloat() - 0.5f) * 2f * maxSpeed
            val (hue, sat, v) = when (val c = creation?.spawnColor) {
                null -> Triple(Random.nextFloat() * 360f, calmSaturation(), calmValue())
                else -> Triple(c.first, c.second.coerceIn(0f, 1f), c.third.coerceIn(0f, 1f))
            }
            val (pin, imm) = if (creation != null) {
                creation.newShapesPinned to (settings.shapeTimeoutImmortal || creation.newShapesImmortal)
            } else {
                false to settings.shapeTimeoutImmortal
            }
            val newShape = GameShape(
                id = nextId++,
                type = newType,
                x = rx,
                y = ry,
                width = size,
                height = size,
                vx = rvx,
                vy = rvy,
                hue = hue,
                saturation = sat,
                value = v,
                lastInteractionMillis = currentGameTimeMillis(),
                isPinned = pin,
                isImmortal = imm
            )
            _shapes.value = (_shapes.value + newShape).takeLast(em)
            Log.d(TAG, "Auto-spawned shape: id=${newShape.id}, type=${newShape.type}, totalShapes=${_shapes.value.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in spawnRandomShape", e)
        }
    }

    private fun recordInteraction() {
        lastUserInteractionGameMillis = currentGameTimeMillis()
    }

    private fun resetAutoSpawnTimers() {
        val now = currentGameTimeMillis()
        lastUserInteractionGameMillis = now
        lastAutoSpawnGameMillis = now
    }

    private fun clearInteractionState() {
        activeShapes.clear()
        startPoints.clear()
        lastDragDeltas.clear()
        fingerCreatedShapeIds.clear()
    }

    private fun currentGameTimeMillis(): Long = gameTimeMillis

    private fun advanceGameClock(deltaSeconds: Float): Long {
        gameTimeRemainderMillis += deltaSeconds * 1000f
        val wholeMillis = gameTimeRemainderMillis.toLong()
        if (wholeMillis > 0L) {
            gameTimeMillis += wholeMillis
            gameTimeRemainderMillis -= wholeMillis.toFloat()
        }
        return gameTimeMillis
    }

    private fun resolvePairCollisions(
        shapes: MutableList<GameShape>,
        settings: AppSettings,
        creation: CreationSession? = null
    ) {
        try {
            val heldIds = activeShapes.values.toSet()
            for (i in 0 until shapes.size) {
                for (j in i + 1 until shapes.size) {
                    val a = shapes[i]
                    val b = shapes[j]

                    val manifold = computePairCollision(a, b) ?: continue

                    val aPin = effectiveIsPinned(a, creation)
                    val bPin = effectiveIsPinned(b, creation)
                    if (aPin && a.id !in heldIds && bPin && b.id !in heldIds) continue

                    val nx = manifold.nx
                    val ny = manifold.ny
                    val overlap = manifold.overlap

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
                        aPin && a.id !in heldIds -> {
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
                        bPin && b.id !in heldIds -> {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error in resolvePairCollisions", e)
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

    private fun clampPointInsideScreen(point: Offset, halfW: Float, halfH: Float): Offset {
        val minX = halfW
        val maxX = (screenSize.x - halfW).coerceAtLeast(minX)
        val minY = halfH
        val maxY = (screenSize.y - halfH).coerceAtLeast(minY)
        return Offset(
            x = point.x.coerceIn(minX, maxX),
            y = point.y.coerceIn(minY, maxY)
        )
    }

    private fun trimToMax(maxShapes: Int) {
        _shapes.value = _shapes.value.takeLast(maxShapes.coerceAtLeast(1))
    }

    /** Refreshes per-shape timeout ([GameShape.lastInteractionMillis]), e.g. after interaction or selection. */
    fun resetShapeLifetimeTimer(id: Long) {
        val now = currentGameTimeMillis()
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
        if (shapesList.isEmpty()) {
            Log.w(TAG, "No selected shapes available, defaulting to CIRCLE")
            return ShapeType.CIRCLE
        }
        return when (selectionMode) {
            ShapeSelectionMode.ALTERNATE -> {
                val index = lastTypeIndex.getAndIncrement() % shapesList.size
                if (index < 0) lastTypeIndex.set(0)
                shapesList[abs(index) % shapesList.size]
            }
            ShapeSelectionMode.RANDOM -> shapesList.random()
        }
    }

    private fun pointInShape(point: Offset, shape: GameShape): Boolean {
        val dx = point.x - shape.x
        val dy = point.y - shape.y
        val halfWidth = shape.width / 2f
        val halfHeight = shape.height / 2f
        return when (shape.type) {
            ShapeType.CIRCLE -> {
                val radius = halfWidth
                hypot(dx, dy) <= radius
            }
            ShapeType.RECTANGLE -> {
                abs(dx) <= halfWidth && abs(dy) <= halfHeight
            }
            ShapeType.TRIANGLE -> {
                abs(dx) <= halfWidth && dy <= halfHeight && dy >= -halfHeight / 2f
            }
            ShapeType.ARCH -> pointInArchStroke(point.x, point.y, shape)
            ShapeType.STAR, ShapeType.HEART, ShapeType.DIAMOND ->
                pointInPolygonShapeStroke(point.x, point.y, shape)
        }
    }

    private fun calmSaturation(): Float = (0.55f + Random.nextFloat() * 0.18f).coerceIn(0f, 1f)
    private fun calmValue(): Float = (0.78f + Random.nextFloat() * 0.14f).coerceIn(0f, 1f)
}
