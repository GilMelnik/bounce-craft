package com.colorbounce.baby

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.isActive
import androidx.compose.runtime.saveable.rememberSaveable

private const val TAG = "CreationMode"
private const val EXIT_BUTTON_TAG = "exit_button"

private enum class RulerScreenEdge { Top, Bottom, Start, End }

/** Minimized ruler: touch target and layout (center math uses half of this). */
private val RulerMinimizeHandleSize = 80.dp
/** Visible disc inside the handle box (Material ~48dp minimum; this matches a generous tap target). */
private val RulerMinimizedVisibleSize = 80.dp
/** After touch slop, movement below this still restores the ruler instead of repositioning the handle. */
private val RulerMinimizedExpandMaxDrag = 48.dp

@Composable
fun CreationModeScreen(
    settings: AppSettings,
    viewModel: GameViewModel,
    onExit: () -> Unit
) {
    var session by remember { mutableStateOf(CreationSession()) }
    var rulerExpanded by rememberSaveable { mutableStateOf(true) }
    var rulerEdgeName by rememberSaveable { mutableStateOf("Bottom") }
    var rulerAlongFraction by rememberSaveable { mutableStateOf(0.5f) }
    val rulerScreenEdge = runCatching { RulerScreenEdge.valueOf(rulerEdgeName) }
        .getOrDefault(RulerScreenEdge.Bottom)
    var contextMenuShapeId by remember { mutableStateOf<Long?>(null) }
    var showAtCapacity by remember { mutableStateOf(false) }

    val shapes by viewModel.shapes.collectAsStateWithLifecycle(emptyList())
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val exitButtonBg = MaterialTheme.colorScheme.surfaceVariant
    val slopPx = with(LocalDensity.current) { 20.dp.toPx() }
    val doubleTap = remember { DoubleTapState() }

    val effectiveSession = remember(session, contextMenuShapeId) {
        session.copy(physicsPaused = session.physicsPaused || (contextMenuShapeId != null))
    }
    val sessionRef by rememberUpdatedState(effectiveSession)

    LaunchedEffect(Unit) {
        viewModel.enterCreationMode()
    }

    LaunchedEffect(Unit) {
        viewModel.creationAtCapacity.collect {
            showAtCapacity = true
        }
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            withFrameNanos { frame ->
                if (last == 0L) {
                    last = frame
                    return@withFrameNanos
                }
                val delta = (frame - last) / 1_000_000_000f
                last = frame
                viewModel.updatePhysics(delta, settings, sessionRef)
            }
        }
    }

    BackHandler {
        viewModel.exitCreationMode()
        onExit()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged {
                try {
                    viewModel.setScreenSize(it.width.toFloat(), it.height.toFloat())
                } catch (e: Exception) {
                    Log.e(TAG, "onSizeChanged", e)
                }
            }
    ) {
        val pointerKey = listOf(
            session.spawnType,
            session.spawnColor,
            session.newShapesPinned,
            session.newShapesImmortal,
            session.disableHueWhileDragging,
            session.physicsPaused,
            contextMenuShapeId,
            settings.maxShapes
        ).toString()

        GamePlayfield(
            shapes = shapes,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pointerKey) {
                    awaitPointerEventScope {
                        val downPos = mutableMapOf<Long, Offset>()
                        while (true) {
                            try {
                                val event = awaitPointerEvent()
                                eventLoop@ for (change in event.changes) {
                                    val pid = change.id.value
                                    when {
                                        change.pressed && !change.previousPressed -> {
                                            val hit = viewModel.shapeAt(change.position)
                                            if (hit != null && doubleTap.isSecondTapOnShape(hit.id)) {
                                                contextMenuShapeId = hit.id
                                                downPos[pid] = change.position
                                                continue@eventLoop
                                            }
                                            if (hit == null) {
                                                doubleTap.clear()
                                            }
                                            viewModel.startInteraction(
                                                change.position,
                                                settings,
                                                pid,
                                                creation = sessionRef
                                            )
                                            downPos[pid] = change.position
                                        }

                                        change.pressed && change.previousPressed -> {
                                            val drag = change.position - change.previousPosition
                                            viewModel.onDrag(
                                                change.position,
                                                drag,
                                                settings,
                                                pid,
                                                creation = sessionRef
                                            )
                                        }

                                        !change.pressed && change.previousPressed -> {
                                            val start = downPos.remove(pid) ?: run {
                                                viewModel.endInteraction(settings, pid, sessionRef)
                                                continue@eventLoop
                                            }
                                            val moved =
                                                (change.position - start).getDistance() > slopPx
                                            val activeId = viewModel.activeShapeIdFor(pid)
                                            if (activeId != null) {
                                                if (!moved) {
                                                    doubleTap.recordShapeTap(activeId)
                                                } else {
                                                    doubleTap.clear()
                                                }
                                            } else if (!moved) {
                                                viewModel.shapeAt(change.position)
                                                    ?.id
                                                    ?.let { doubleTap.recordShapeTap(it) }
                                            } else {
                                                doubleTap.clear()
                                            }
                                            viewModel.endInteraction(settings, pid, sessionRef)
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
        )

        // Exit (match GameScreen)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 16.dp)
                .size(34.dp)
                .zIndex(10f)
                .testTag(EXIT_BUTTON_TAG)
                .background(exitButtonBg.copy(alpha = 0.8f), CircleShape)
                .clickable {
                    try {
                        viewModel.exitCreationMode()
                        onExit()
                    } catch (e: Exception) {
                        Log.e(TAG, "Exit", e)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(20.dp)) {
                drawLine(
                    color = onSurfaceVariant,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 4f
                )
                drawLine(
                    color = onSurfaceVariant,
                    start = Offset(size.width, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 4f
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .zIndex(4f)
        ) {
            if (rulerExpanded) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val maxH = (maxHeight * 0.45f).coerceIn(220.dp, 480.dp)
                    val surfaceMod = when (rulerScreenEdge) {
                        RulerScreenEdge.Top -> Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .heightIn(max = maxH)
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp)

                        RulerScreenEdge.Bottom -> Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .heightIn(max = maxH)
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp)

                        RulerScreenEdge.Start -> Modifier
                            .align(Alignment.CenterStart)
                            .width(300.dp)
                            .heightIn(min = 220.dp, max = maxH)
                            .statusBarsPadding()
                            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)

                        RulerScreenEdge.End -> Modifier
                            .align(Alignment.CenterEnd)
                            .width(300.dp)
                            .heightIn(min = 220.dp, max = maxH)
                            .statusBarsPadding()
                            .padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
                    }
                    val isSide =
                        rulerScreenEdge == RulerScreenEdge.Start || rulerScreenEdge == RulerScreenEdge.End
                    Surface(
                        modifier = surfaceMod,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = 2.dp
                    ) {
                        CreationModeRuler(
                            session = session,
                            onSessionChange = { session = it },
                            onCollapse = { rulerExpanded = false },
                            isSideBar = isSide,
                            maxHeight = maxH
                        )
                    }
                }
            } else {
                CreationRulerMinimizedControl(
                    rulerScreenEdge = rulerScreenEdge,
                    alongFraction = rulerAlongFraction,
                    onPlacementChange = { edge, along ->
                        rulerEdgeName = edge.name
                        rulerAlongFraction = along
                    },
                    onExpand = { rulerExpanded = true }
                )
            }
        }

        contextMenuShapeId?.let { id ->
            val shape = shapes.find { it.id == id }
            if (shape == null) {
                contextMenuShapeId = null
            } else {
                val scheme = MaterialTheme.colorScheme
                val dimAction = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = scheme.outline
                )
                val pinColors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (shape.isPinned) scheme.primaryContainer else Color.Transparent,
                    contentColor = if (shape.isPinned) scheme.onPrimaryContainer else scheme.outline
                )
                val immortalColors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (shape.isImmortal) scheme.tertiaryContainer else Color.Transparent,
                    contentColor = if (shape.isImmortal) scheme.onTertiaryContainer else scheme.outline
                )
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .zIndex(5f)
                ) {
                    val density = LocalDensity.current
                    var menuSize by remember(id) { mutableStateOf(IntSize.Zero) }
                    val marginPx = with(density) { 8.dp.toPx() }
                    val gapPx = with(density) { 10.dp.toPx() }
                    val estMenuW = with(density) { 168.dp.toPx() }
                    val estMenuH = with(density) { 56.dp.toPx() }
                    val screenW = with(density) { maxWidth.toPx() }
                    val screenH = with(density) { maxHeight.toPx() }

                    val scrimInteraction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .align(Alignment.TopStart)
                            .clickable(
                                indication = null,
                                interactionSource = scrimInteraction
                            ) { contextMenuShapeId = null }
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                val menuW =
                                    if (menuSize.width > 0) menuSize.width.toFloat() else estMenuW
                                val menuH =
                                    if (menuSize.height > 0) menuSize.height.toFloat() else estMenuH
                                var x = shape.x - menuW / 2f
                                var y = shape.y + shape.height / 2f + gapPx
                                if (y + menuH > screenH - marginPx) {
                                    y = shape.y - shape.height / 2f - gapPx - menuH
                                }
                                x = x.coerceIn(marginPx, screenW - menuW - marginPx)
                                y = y.coerceIn(marginPx, screenH - menuH - marginPx)
                                IntOffset(x.roundToInt(), y.roundToInt())
                            }
                            .onSizeChanged { menuSize = it }
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = scheme.surfaceContainerHigh,
                            tonalElevation = 3.dp,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.removeShape(id)
                                        contextMenuShapeId = null
                                    },
                                    colors = dimAction
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete shape",
                                        tint = Color.Unspecified
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.setShapePinned(id, !shape.isPinned) },
                                    colors = pinColors
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PushPin,
                                        contentDescription = "Pin shape",
                                        tint = Color.Unspecified
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.setShapeImmortal(id, !shape.isImmortal) },
                                    colors = immortalColors
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Shield,
                                        contentDescription = "Immortal shape",
                                        tint = Color.Unspecified
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAtCapacity) {
            AlertDialog(
                onDismissRequest = { showAtCapacity = false },
                title = { Text("Limit reached") },
                text = { Text("Maximum of $CREATION_MAX_SHAPES shapes. Remove a shape to add more.") },
                confirmButton = {
                    TextButton(onClick = { showAtCapacity = false }) { Text("OK") }
                }
            )
        }
    }
}

@Composable
private fun CreationRulerMinimizedControl(
    rulerScreenEdge: RulerScreenEdge,
    alongFraction: Float,
    onPlacementChange: (RulerScreenEdge, Float) -> Unit,
    onExpand: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var drag by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pad = with(density) { 8.dp.toPx() }
        val halfB = with(density) { (RulerMinimizeHandleSize / 2).toPx() }
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val expandMaxDragPx = with(density) { RulerMinimizedExpandMaxDrag.toPx() }
        val along = alongFraction.coerceIn(0f, 1f)
        val spanX = (w - 2f * pad - 2f * halfB).coerceAtLeast(1e-3f)
        val spanY = (h - 2f * pad - 2f * halfB).coerceAtLeast(1e-3f)
        fun restCenter(e: RulerScreenEdge, t: Float): Offset {
            val u = t.coerceIn(0f, 1f)
            return when (e) {
                RulerScreenEdge.Top -> Offset(pad + halfB + u * spanX, pad + halfB)
                RulerScreenEdge.Bottom -> Offset(pad + halfB + u * spanX, h - pad - halfB)
                RulerScreenEdge.Start -> Offset(pad + halfB, pad + halfB + u * spanY)
                RulerScreenEdge.End -> Offset(w - pad - halfB, pad + halfB + u * spanY)
            }
        }
        fun projectAlong(p: Offset, e: RulerScreenEdge): Float = when (e) {
            RulerScreenEdge.Top, RulerScreenEdge.Bottom -> ((p.x - pad - halfB) / spanX).coerceIn(0f, 1f)
            RulerScreenEdge.Start, RulerScreenEdge.End -> ((p.y - pad - halfB) / spanY).coerceIn(0f, 1f)
        }
        fun nearestEdge(p: Offset): RulerScreenEdge = listOf(
            p.y to RulerScreenEdge.Top,
            h - p.y to RulerScreenEdge.Bottom,
            p.x to RulerScreenEdge.Start,
            w - p.x to RulerScreenEdge.End
        ).minBy { (d, _) -> d }.second

        val c = restCenter(rulerScreenEdge, along) + drag
        val maxX = (w - 2f * halfB).roundToInt().coerceAtLeast(0)
        val maxY = (h - 2f * halfB).roundToInt().coerceAtLeast(0)
        val tlX = (c.x - halfB).roundToInt().coerceIn(0, maxX)
        val tlY = (c.y - halfB).roundToInt().coerceIn(0, maxY)

        Box(
            modifier = Modifier
                .offset { IntOffset(tlX, tlY) }
                .size(RulerMinimizeHandleSize)
                .semantics { contentDescription = "Ruler" }
                .pointerInput(rulerScreenEdge, w, h, along, expandMaxDragPx) {
                    // detectDragGestures only invokes onDragEnd after touch slop, so a plain tap never
                    // expanded the ruler. Track slop explicitly and treat short drags as expand.
                    val touchSlopPx = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var crossedSlop = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.find { it.id == down.id } ?: continue
                            if (change.pressed) {
                                val total = change.position - down.position
                                if (!crossedSlop && total.getDistance() >= touchSlopPx) {
                                    crossedSlop = true
                                }
                                if (crossedSlop) {
                                    drag = total
                                }
                            }
                            if (!change.pressed && change.previousPressed) {
                                change.consume()
                                val total =
                                    if (crossedSlop) change.position - down.position else Offset.Zero
                                if (!crossedSlop || total.getDistance() < expandMaxDragPx) {
                                    onExpand()
                                } else {
                                    val p = restCenter(rulerScreenEdge, along) + total
                                    val e2 = nearestEdge(p)
                                    onPlacementChange(e2, projectAlong(p, e2))
                                }
                                drag = Offset.Zero
                                break
                            }
                            change.consume()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = scheme.secondaryContainer,
                shadowElevation = 1.dp,
                modifier = Modifier.size(RulerMinimizedVisibleSize)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RulerLineIcon(
                        color = scheme.primary,
                        size = DpSize(36.dp, 22.dp)
                    )
                }
            }
        }
    }
}

/** Pairs a quick down after a short tap on the same shape to open the shape menu (double-tap). */
private class DoubleTapState {
    private var lastTapUptime = 0L
    private var lastTapShapeId: Long? = null

    fun clear() {
        lastTapUptime = 0L
        lastTapShapeId = null
    }

    fun recordShapeTap(shapeId: Long) {
        lastTapUptime = SystemClock.uptimeMillis()
        lastTapShapeId = shapeId
    }

    fun isSecondTapOnShape(shapeId: Long): Boolean {
        val t = SystemClock.uptimeMillis()
        val v = lastTapShapeId == shapeId && t - lastTapUptime in 1L..400L
        if (v) clear()
        return v
    }
}
