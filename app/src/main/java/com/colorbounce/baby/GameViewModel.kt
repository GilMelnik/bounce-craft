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

/**
 * Wraps the body list with a monotonically-increasing [tick] so [MutableStateFlow] emits every
 * frame even though the underlying [GameShape] instances are reused.
 */
data class ShapesFrame(val tick: Long, val shapes: List<GameShape>)

class GameViewModel : ViewModel() {

    private val bodies: MutableList<GameShape> = mutableListOf()
    private var frameTick: Long = 0L

    private val _shapes = MutableStateFlow(ShapesFrame(0L, emptyList()))
    val shapes: StateFlow<ShapesFrame> = _shapes.asStateFlow()

    private val spatialGrid = SpatialGrid()
    private val collisionDispatcher = CollisionDispatcher()
    private val collisionResolver = CollisionResolver()

    private var nextId = 1L
    private val lastTypeIndex = AtomicInteger(0)
    private val activeShapes = mutableMapOf<Long, Long>() // pointerId to shapeId
    private val startPoints = mutableMapOf<Long, Offset>()
    private val lastDragDeltas = mutableMapOf<Long, Offset>()
    /**
     * Shape ids still in the finger-down "draw" gesture (before [endInteraction]).
     * Resize-on-drag applies only while id is in this set — not after creation ends, even if vx/vy are zero.
     * During this window, ruler "pin all" does not block resizing; pin takes effect when the gesture ends.
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

    fun shapeAt(point: Offset): GameShape? = bodies.lastOrNull { pointInShape(point, it) }

    fun activeShapeIdFor(pointerId: Long): Long? = activeShapes[pointerId]

    fun removeShape(id: Long) {
        bodies.removeAll { it.id == id }
        val toRemove = activeShapes.filter { it.value == id }.keys
        toRemove.forEach { cleanupPointer(it) }
        fingerCreatedShapeIds.remove(id)
        publish()
    }

    /** Drops stale pointer capture for [shapeId] (e.g. shape menu overlay gesture cancelled mid-drag). */
    fun clearPointersTargetingShape(shapeId: Long) {
        val pointerIds = activeShapes.filter { it.value == shapeId }.keys.toList()
        pointerIds.forEach { cleanupPointer(it) }
        fingerCreatedShapeIds.remove(shapeId)
    }

    fun setShapePinned(id: Long, pinned: Boolean) {
        findById(id)?.isPinned = pinned
        publish()
    }

    fun setShapeImmortal(id: Long, immortal: Boolean) {
        findById(id)?.isImmortal = immortal
        publish()
    }

    fun setShapeExemptFromGlobalPin(id: Long, exempt: Boolean) {
        findById(id)?.exemptFromGlobalPin = exempt
        publish()
    }

    fun setShapeExemptFromGlobalImmortal(id: Long, exempt: Boolean) {
        findById(id)?.exemptFromGlobalImmortal = exempt
        publish()
    }

    /** Ruler pin toggle: applies to every shape; clears per-shape exemptions. */
    fun applyGlobalPinFromRuler(allPinned: Boolean) {
        for (b in bodies) {
            b.exemptFromGlobalPin = false
            b.isPinned = allPinned
        }
        publish()
    }

    /** Ruler lifetime toggle: applies to every shape; clears per-shape exemptions. */
    fun applyGlobalImmortalFromRuler(allImmortal: Boolean) {
        for (b in bodies) {
            b.exemptFromGlobalImmortal = false
            b.isImmortal = allImmortal
        }
        publish()
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
        findById(id)?.freezeHueWhileDragging = freeze
        publish()
    }

    fun setShapeExemptFromGlobalHueLock(id: Long, exempt: Boolean) {
        findById(id)?.exemptFromGlobalHueLock = exempt
        publish()
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
            val hit = bodies.lastOrNull { pointInShape(point, it) }
            if (hit != null) {
                Log.d(TAG, "Interaction: Tapped existing shape id=${hit.id}")
                activeShapes[pointerId] = hit.id
                startPoints[pointerId] = point
                lastDragDeltas[pointerId] = Offset.Zero
                resetShapeLifetimeTimer(hit.id)
                return
            }

            if (creation != null && bodies.size >= effectiveMaxShapes(creation, settings)) {
                _creationAtCapacity.tryEmit(Unit)
                return
            }

            val newType = chooseType(
                creation?.selectedShapes ?: settings.selectedShapes,
                creation?.shapeSelectionMode ?: settings.shapeSelectionMode
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
            val newShape = GameShape.create(
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
            bodies.add(newShape)
            trimToMax(em)
            Log.d(
                TAG,
                "Interaction: Created new shape id=${newShape.id}, type=$newType, pointerId=$pointerId, totalShapes=${bodies.size}"
            )
            publish()
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
            val shape = findById(shapeId) ?: return
            // While the finger is still down on a newly spawned shape, allow resize even if
            // the ruler pins all shapes — pin semantics apply after [endInteraction] only.
            val resizingSpawnGesture = resizeOnDrag && fingerCreatedShapeIds.contains(shape.id)
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
            shape.x = boundedPoint.x
            shape.y = boundedPoint.y
            if (resizingSpawnGesture) {
                shape.width = boundedSize
                shape.height = boundedSize
            }
            shape.lastInteractionMillis = now
            trimToMax(effectiveMaxShapes(creation, settings))
            publish()
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
            val shapeBeforeEnd = findById(shapeId)
            if (shapeBeforeEnd != null && effectiveIsPinned(shapeBeforeEnd, creation)) {
                shapeBeforeEnd.vx = 0f
                shapeBeforeEnd.vy = 0f
                shapeBeforeEnd.lastInteractionMillis = currentGameTimeMillis()
                cleanupPointer(pointerId)
                publish()
                return
            }
            if (!applyLaunchVelocity) {
                shapeBeforeEnd?.let {
                    it.vx = 0f
                    it.vy = 0f
                    it.lastInteractionMillis = currentGameTimeMillis()
                }
                cleanupPointer(pointerId)
                publish()
                return
            }
            if (dragDelta == Offset.Zero) {
                shapeBeforeEnd?.let {
                    it.lastInteractionMillis = currentGameTimeMillis()
                }
                cleanupPointer(pointerId)
                publish()
                return
            }
            val rawVx = dragDelta.x * ShapeVelocity.LAUNCH_DRAG_FACTOR
            val rawVy = dragDelta.y * ShapeVelocity.LAUNCH_DRAG_FACTOR
            val (vx, vy) = ShapeVelocity.clamp(rawVx, rawVy, settings.maxVelocityPxPerSec.toFloat())
            shapeBeforeEnd?.let {
                it.vx = vx
                it.vy = vy
                it.lastInteractionMillis = currentGameTimeMillis()
            }
            cleanupPointer(pointerId)
            publish()
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
                for (shape in bodies) {
                    if (!activeIds.contains(shape.id)) continue
                    val freezeHue = hueFrozenWhileDragging(c, shape, isHeld = true)
                    if (!freezeHue) {
                        shape.hue = ShapeColorAnimator.stepHue(shape.hue, safeDelta)
                    }
                }
                publish()
                return
            }

            val timeoutMs = settings.shapeTimeoutSeconds.coerceIn(1, 120) * 1000L
            val now = advanceGameClock(safeDelta)
            val em = effectiveMaxShapes(creation, settings)

            checkAutoSpawn(now, settings, c)

            val activeIds = activeShapes.values.toSet()
            val maxSpeed = settings.maxVelocityPxPerSec.coerceIn(100, 3000).toFloat()

            // Integrate + cull expired shapes.
            val it = bodies.iterator()
            while (it.hasNext()) {
                val shape = it.next()
                if (!effectiveIsImmortal(shape, c, settings)) {
                    if (now - shape.lastInteractionMillis > timeoutMs) {
                        Log.d(
                            TAG,
                            "Shape id=${shape.id} expired (timeout=${settings.shapeTimeoutSeconds}s, immortal=${settings.shapeTimeoutImmortal})"
                        )
                        it.remove()
                        continue
                    }
                }

                val isHeld = activeIds.contains(shape.id)
                if (!isHeld) {
                    if (effectiveIsPinned(shape, c)) {
                        shape.vx = 0f
                        shape.vy = 0f
                    } else {
                        shape.x += shape.vx * safeDelta
                        shape.y += shape.vy * safeDelta
                        val halfW = shape.width / 2f
                        val halfH = shape.height / 2f
                        if (shape.x - halfW < 0f) {
                            shape.x = halfW
                            shape.vx = abs(shape.vx)
                        } else if (shape.x + halfW > width) {
                            shape.x = width - halfW
                            shape.vx = -abs(shape.vx)
                        }
                        if (shape.y - halfH < 0f) {
                            shape.y = halfH
                            shape.vy = abs(shape.vy)
                        } else if (shape.y + halfH > height) {
                            shape.y = height - halfH
                            shape.vy = -abs(shape.vy)
                        }
                    }
                } else if (effectiveIsPinned(shape, c)) {
                    shape.vx = 0f
                    shape.vy = 0f
                }

                val disableHue = hueFrozenWhileDragging(c, shape, isHeld)
                if (isHeld && !disableHue) {
                    shape.hue = ShapeColorAnimator.stepHue(shape.hue, safeDelta)
                }

                val (cvx, cvy) = ShapeVelocity.clamp(shape.vx, shape.vy, maxSpeed)
                shape.vx = cvx
                shape.vy = cvy
            }

            // Multi-pass narrow-phase + Baumgarte slop: pair order and compound MTV approximation leave
            // residual overlap in one pass (circles vs stars/diamonds especially).
            repeat(COLLISION_SOLVER_PASSES) {
                for (shape in bodies) {
                    shape.refreshAabb()
                    shape.refreshHulls()
                }
                spatialGrid.rebuild(bodies, width, height)
                spatialGrid.forEachCandidatePair { a, b ->
                    val manifold = collisionDispatcher.evaluate(a, b) ?: return@forEachCandidatePair

                    val aPin = effectiveIsPinned(a, c)
                    val bPin = effectiveIsPinned(b, c)
                    val aHeld = a.id in activeIds
                    val bHeld = b.id in activeIds
                    val aImmovable = aHeld || (aPin && !aHeld)
                    val bImmovable = bHeld || (bPin && !bHeld)

                    collisionResolver.resolve(
                        a, b, manifold,
                        aImmovable = aImmovable,
                        bImmovable = bImmovable,
                        maxSpeed = maxSpeed,
                        screenW = width,
                        screenH = height
                    )
                }
            }

            trimToMax(em)
            publish()
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
            if (bodies.size >= em) return

            val w = screenSize.x
            val h = screenSize.y
            if (w <= 0f || h <= 0f) {
                Log.w(TAG, "Cannot spawn shape: invalid screen dimensions ${w}x${h}")
                return
            }

            val newType = chooseType(
                creation?.selectedShapes ?: settings.selectedShapes,
                creation?.shapeSelectionMode ?: settings.shapeSelectionMode
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
            val newShape = GameShape.create(
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
            bodies.add(newShape)
            trimToMax(em)
            Log.d(TAG, "Auto-spawned shape: id=${newShape.id}, type=${newShape.type}, totalShapes=${bodies.size}")
            // The physics tick already publishes after every step; explicit publish here keeps
            // capture-mode insertions visible if they happen between ticks.
            publish()
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
        val cap = maxShapes.coerceAtLeast(1)
        while (bodies.size > cap) {
            // Drop the oldest entries to preserve takeLast semantics.
            bodies.removeAt(0)
        }
    }

    /** Refreshes per-shape timeout ([GameShape.lastInteractionMillis]), e.g. after interaction or selection. */
    fun resetShapeLifetimeTimer(id: Long) {
        val now = currentGameTimeMillis()
        findById(id)?.lastInteractionMillis = now
        publish()
    }

    private fun chooseType(selectedShapes: Set<ShapeType>, selectionMode: ShapeSelectionMode): ShapeType {
        val shapesList = selectedShapes.toList()
        if (shapesList.isEmpty()) {
            Log.w(TAG, "No selected shapes available, defaulting to CIRCLE")
            return ShapeType.CIRCLE
        }
        return when (selectionMode) {
            ShapeSelectionMode.ALTERNATE -> {
                val raw = lastTypeIndex.getAndIncrement()
                val index = ((raw % shapesList.size) + shapesList.size) % shapesList.size
                shapesList[index]
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

    private fun findById(id: Long): GameShape? = bodies.firstOrNull { it.id == id }

    private fun publish() {
        frameTick++
        _shapes.value = ShapesFrame(frameTick, ArrayList(bodies))
    }

    private fun calmSaturation(): Float = (0.55f + Random.nextFloat() * 0.18f).coerceIn(0f, 1f)
    private fun calmValue(): Float = (0.78f + Random.nextFloat() * 0.14f).coerceIn(0f, 1f)
}
