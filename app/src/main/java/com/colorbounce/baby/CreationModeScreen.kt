package com.colorbounce.baby

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Timer
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Spectrum for the open (unlocked) hue-lock icon — reads as “color” in any theme. */
private val rainbowLockOpenGradientColors = listOf(
    Color(0xFFFF1744),
    Color(0xFFFF9100),
    Color(0xFFFFEA00),
    Color(0xFF00E676),
    Color(0xFF00B0FF),
    Color(0xFFD500F9),
    Color(0xFFFF1744)
)

private enum class RulerScreenEdge { Top, Bottom, Start, End }

/** Minimized ruler: FAB-sized bubble; still meets ~48dp effective touch target. */
private val RulerMinimizeHandleSize = 56.dp
private val RulerMinimizedVisibleSize = 56.dp
/** Drag distance under this (after slop) expands the panel instead of moving the bubble — larger = easier to open. */
private val RulerMinimizedExpandMaxDrag = 72.dp

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
    val dismissShapeMenu by rememberUpdatedState(newValue = { contextMenuShapeId = null })

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
            session.defaultShapeSelectionMode,
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
                                                viewModel.resetShapeLifetimeTimer(hit.id)
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
                    val maxH = (maxHeight * 0.38f).coerceIn(200.dp, 400.dp)
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
                            .width(272.dp)
                            .heightIn(min = 200.dp, max = maxH)
                            .statusBarsPadding()
                            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)

                        RulerScreenEdge.End -> Modifier
                            .align(Alignment.CenterEnd)
                            .width(272.dp)
                            .heightIn(min = 200.dp, max = maxH)
                            .statusBarsPadding()
                            .padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
                    }
                    val isSide =
                        rulerScreenEdge == RulerScreenEdge.Start || rulerScreenEdge == RulerScreenEdge.End
                    Surface(
                        modifier = surfaceMod,
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = 4.dp,
                        tonalElevation = 2.dp
                    ) {
                        CreationModeRuler(
                            session = session,
                            onSessionChange = { newSession ->
                                if (newSession.newShapesPinned != session.newShapesPinned) {
                                    viewModel.applyGlobalPinFromRuler(newSession.newShapesPinned)
                                }
                                if (newSession.newShapesImmortal != session.newShapesImmortal) {
                                    viewModel.applyGlobalImmortalFromRuler(newSession.newShapesImmortal)
                                }
                                session = newSession
                            },
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
                val menuSurfaceLum = scheme.surfaceContainerHigh.luminance()
                val menuIconInk = if (menuSurfaceLum < 0.5f) Color.White else Color.Black
                val menuIconInkDim = menuIconInk.copy(alpha = 0.45f)
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .zIndex(5f)
                ) {
                    val density = LocalDensity.current
                    var menuSize by remember(id) { mutableStateOf(IntSize.Zero) }
                    val marginPx = with(density) { 8.dp.toPx() }
                    val gapPx = with(density) { 10.dp.toPx() }
                    val estMenuW = with(density) { 220.dp.toPx() }
                    val estMenuH = with(density) { 56.dp.toPx() }
                    val screenW = with(density) { maxWidth.toPx() }
                    val screenH = with(density) { maxHeight.toPx() }

                    Box(
                        Modifier
                            .fillMaxSize()
                            .align(Alignment.TopStart)
                            .pointerInput(id, menuSize, settings.maxShapes) {
                                awaitPointerEventScope {
                                    val downPos = mutableMapOf<Long, Offset>()
                                    /** True = dragging the selected shape; false = tap-outside-to-dismiss. */
                                    val draggingSelectedShape = mutableMapOf<Long, Boolean>()
                                    while (true) {
                                        try {
                                            val event = awaitPointerEvent()
                                            for (change in event.changes) {
                                                val pid = change.id.value
                                                when {
                                                    change.pressed && !change.previousPressed -> {
                                                        val p = change.position
                                                        val menuRect = contextMenuScreenBounds(
                                                            shape,
                                                            menuSize,
                                                            estMenuW,
                                                            estMenuH,
                                                            marginPx,
                                                            gapPx,
                                                            screenW,
                                                            screenH
                                                        )
                                                        if (menuRect.contains(p)) continue
                                                        val hit = viewModel.shapeAt(p)
                                                        if (hit?.id == id) {
                                                            viewModel.startInteraction(
                                                                p,
                                                                settings,
                                                                pid,
                                                                creation = sessionRef
                                                            )
                                                            downPos[pid] = p
                                                            draggingSelectedShape[pid] = true
                                                        } else {
                                                            downPos[pid] = p
                                                            draggingSelectedShape[pid] = false
                                                        }
                                                    }

                                                    change.pressed && change.previousPressed -> {
                                                        if (draggingSelectedShape[pid] == true) {
                                                            val drag =
                                                                change.position - change.previousPosition
                                                            viewModel.onDrag(
                                                                change.position,
                                                                drag,
                                                                settings,
                                                                pid,
                                                                resizeOnDrag = false,
                                                                constrainInsideScreen = true,
                                                                creation = sessionRef
                                                            )
                                                        }
                                                    }

                                                    !change.pressed && change.previousPressed -> {
                                                        val wasDraggingSelected =
                                                            draggingSelectedShape.remove(pid)
                                                        val start = downPos.remove(pid)
                                                        when {
                                                            wasDraggingSelected == true -> {
                                                                viewModel.endInteraction(
                                                                    settings,
                                                                    pid,
                                                                    sessionRef,
                                                                    applyLaunchVelocity = false
                                                                )
                                                            }

                                                            wasDraggingSelected == false && start != null -> {
                                                                val moved =
                                                                    (change.position - start).getDistance() >
                                                                        slopPx
                                                                if (!moved) dismissShapeMenu()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            }
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
                                ShapeContextMenuIconButton(
                                    onClick = {
                                        viewModel.removeShape(id)
                                        contextMenuShapeId = null
                                    },
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete shape",
                                    tint = menuIconInk
                                )
                                ShapeContextMenuIconButton(
                                    onClick = {
                                        if (session.newShapesPinned) {
                                            viewModel.setShapeExemptFromGlobalPin(
                                                id,
                                                !shape.exemptFromGlobalPin
                                            )
                                        } else {
                                            viewModel.setShapePinned(id, !shape.isPinned)
                                        }
                                    },
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = if (session.newShapesPinned) {
                                        "Ruler pins all shapes. Tap to unpin only this shape, or tap again to follow the ruler."
                                    } else {
                                        "Pin this shape. Turn on ruler pin to pin every shape at once."
                                    },
                                    tint = when {
                                        session.newShapesPinned && !shape.exemptFromGlobalPin ->
                                            menuIconInk
                                        session.newShapesPinned && shape.exemptFromGlobalPin ->
                                            menuIconInkDim
                                        shape.isPinned -> menuIconInk
                                        else -> menuIconInkDim
                                    },
                                    emphasized = (session.newShapesPinned && shape.exemptFromGlobalPin) ||
                                        (!session.newShapesPinned && shape.isPinned)
                                )
                                ShapeContextMenuIconButton(
                                    onClick = {
                                        if (session.newShapesImmortal) {
                                            viewModel.setShapeExemptFromGlobalImmortal(
                                                id,
                                                !shape.exemptFromGlobalImmortal
                                            )
                                        } else {
                                            viewModel.setShapeImmortal(id, !shape.isImmortal)
                                        }
                                    },
                                    imageVector = if (
                                        if (session.newShapesImmortal) {
                                            !shape.exemptFromGlobalImmortal
                                        } else {
                                            shape.isImmortal
                                        }
                                    ) {
                                        Icons.Filled.AllInclusive
                                    } else {
                                        Icons.Outlined.Timer
                                    },
                                    contentDescription = if (session.newShapesImmortal) {
                                        "Ruler keeps all shapes forever. Tap so only this shape can time out, or again to match the ruler."
                                    } else {
                                        "Keep this shape from timing out. Turn on ruler infinity to apply to every shape."
                                    },
                                    tint = when {
                                        session.newShapesImmortal && !shape.exemptFromGlobalImmortal ->
                                            menuIconInk
                                        session.newShapesImmortal && shape.exemptFromGlobalImmortal ->
                                            menuIconInkDim
                                        shape.isImmortal -> menuIconInk
                                        else -> menuIconInkDim
                                    },
                                    emphasized = (session.newShapesImmortal && shape.exemptFromGlobalImmortal) ||
                                        (!session.newShapesImmortal && shape.isImmortal)
                                )
                                ShapeContextMenuHueLockButton(
                                    shape = shape,
                                    rulerHueGloballyLocked = session.disableHueWhileDragging,
                                    onClick = {
                                        if (session.disableHueWhileDragging) {
                                            viewModel.setShapeExemptFromGlobalHueLock(
                                                id,
                                                !shape.exemptFromGlobalHueLock
                                            )
                                        } else {
                                            viewModel.setShapeFreezeHueWhileDragging(
                                                id,
                                                !shape.freezeHueWhileDragging
                                            )
                                        }
                                    }
                                )
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
private fun ShapeContextMenuIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    emphasized: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (emphasized) {
                scheme.primaryContainer.copy(alpha = 0.92f)
            } else {
                Color.Transparent
            },
            contentColor = tint
        )
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

@Composable
private fun ShapeContextMenuHueLockButton(
    shape: GameShape,
    rulerHueGloballyLocked: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shapeBodyColor = shape.color
    val lockedVisual = if (rulerHueGloballyLocked) {
        !shape.exemptFromGlobalHueLock
    } else {
        shape.freezeHueWhileDragging
    }
    val emphasized = if (rulerHueGloballyLocked) {
        shape.exemptFromGlobalHueLock
    } else {
        shape.freezeHueWhileDragging
    }
    val (openDesc, closedDesc) = if (rulerHueGloballyLocked) {
        "This shape can shift hue while dragging (overrides ruler lock). Tap to follow ruler lock like other shapes." to
            "Hue locked while dragging (same as ruler). Tap to allow only this shape to shift hue when dragged."
    } else {
        "Tap to freeze hue while dragging this shape." to
            "Hue frozen while dragging this shape. Tap to allow hue to shift."
    }
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (emphasized) {
                scheme.primaryContainer.copy(alpha = 0.92f)
            } else {
                Color.Transparent
            },
            contentColor = if (lockedVisual) shapeBodyColor else Color.White
        )
    ) {
        if (lockedVisual) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = closedDesc,
                tint = shapeBodyColor
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.LockOpen,
                contentDescription = openDesc,
                tint = Color.White,
                modifier = Modifier.drawWithCache {
                    val brush = Brush.linearGradient(
                        colors = rainbowLockOpenGradientColors,
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(
                            brush = brush,
                            blendMode = BlendMode.SrcIn
                        )
                    }
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
                .graphicsLayer {
                    val s = if (drag == Offset.Zero) 1f else 1.08f
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
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
                shadowElevation = 4.dp,
                tonalElevation = 2.dp,
                modifier = Modifier.size(RulerMinimizedVisibleSize)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RulerLineIcon(
                        majorColor = scheme.onSecondaryContainer,
                        minorColor = scheme.outlineVariant.copy(alpha = 0.75f),
                        size = DpSize(28.dp, 18.dp)
                    )
                }
            }
        }
    }
}

/** Menu bar bounds in playfield coordinates (matches [CreationModeScreen] menu placement). */
private fun contextMenuScreenBounds(
    shape: GameShape,
    menuSize: IntSize,
    estMenuW: Float,
    estMenuH: Float,
    marginPx: Float,
    gapPx: Float,
    screenW: Float,
    screenH: Float
): Rect {
    val menuW = if (menuSize.width > 0) menuSize.width.toFloat() else estMenuW
    val menuH = if (menuSize.height > 0) menuSize.height.toFloat() else estMenuH
    var x = shape.x - menuW / 2f
    var y = shape.y + shape.height / 2f + gapPx
    if (y + menuH > screenH - marginPx) {
        y = shape.y - shape.height / 2f - gapPx - menuH
    }
    x = x.coerceIn(marginPx, screenW - menuW - marginPx)
    y = y.coerceIn(marginPx, screenH - menuH - marginPx)
    return Rect(x, y, x + menuW, y + menuH)
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
