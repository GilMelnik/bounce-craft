package com.colorbounce.baby

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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

/** Matches the creation exit [Box] so ruler chrome stays clear and tappable. */
private val CreationExitButtonInsetTop = 24.dp
private val CreationExitButtonInsetEnd = 16.dp
private val CreationExitButtonTapSize = 34.dp
private val CreationExitRulerGap = 8.dp
private val CreationExitRulerEndPadding =
    CreationExitButtonInsetEnd + CreationExitButtonTapSize + CreationExitRulerGap
private val CreationExitRulerTopPadding =
    CreationExitButtonInsetTop + CreationExitButtonTapSize + CreationExitRulerGap

private fun Density.creationExitTrailingInsetPx(): Float = CreationExitRulerEndPadding.toPx()

private fun Density.creationExitTopClearancePx(): Float = CreationExitRulerTopPadding.toPx()

private enum class RulerScreenEdge { Top, Bottom, Start, End }

private fun rulerRestCenter(
    edge: RulerScreenEdge,
    alongFraction: Float,
    w: Float,
    h: Float,
    padPx: Float,
    halfWidthPx: Float,
    halfHeightPx: Float,
    trailingInsetPx: Float = 0f,
    topInsetPx: Float = 0f
): Offset {
    val u = alongFraction.coerceIn(0f, 1f)
    val spanX = (w - 2f * padPx - 2f * halfWidthPx - trailingInsetPx).coerceAtLeast(1e-3f)
    val spanY = (h - 2f * padPx - 2f * halfHeightPx - topInsetPx).coerceAtLeast(1e-3f)
    return when (edge) {
        RulerScreenEdge.Top ->
            Offset(padPx + halfWidthPx + u * spanX, padPx + halfHeightPx)
        RulerScreenEdge.Bottom ->
            Offset(padPx + halfWidthPx + u * spanX, h - padPx - halfHeightPx)
        RulerScreenEdge.Start ->
            Offset(padPx + halfWidthPx, padPx + halfHeightPx + u * spanY)
        RulerScreenEdge.End ->
            Offset(
                w - padPx - halfWidthPx,
                padPx + halfHeightPx + topInsetPx + u * spanY
            )
    }
}

private fun rulerProjectAlong(
    p: Offset,
    edge: RulerScreenEdge,
    w: Float,
    h: Float,
    padPx: Float,
    halfWidthPx: Float,
    halfHeightPx: Float,
    trailingInsetPx: Float = 0f,
    topInsetPx: Float = 0f
): Float {
    val spanX = (w - 2f * padPx - 2f * halfWidthPx - trailingInsetPx).coerceAtLeast(1e-3f)
    val spanY = (h - 2f * padPx - 2f * halfHeightPx - topInsetPx).coerceAtLeast(1e-3f)
    return when (edge) {
        RulerScreenEdge.Top, RulerScreenEdge.Bottom ->
            ((p.x - padPx - halfWidthPx) / spanX).coerceIn(0f, 1f)
        RulerScreenEdge.Start, RulerScreenEdge.End ->
            ((p.y - padPx - halfHeightPx - topInsetPx) / spanY).coerceIn(0f, 1f)
    }
}

private fun rulerNearestEdge(p: Offset, w: Float, h: Float): RulerScreenEdge =
    listOf(
        p.y to RulerScreenEdge.Top,
        h - p.y to RulerScreenEdge.Bottom,
        p.x to RulerScreenEdge.Start,
        w - p.x to RulerScreenEdge.End
    ).minBy { (d, _) -> d }.second

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
    var expandedRulerPanelSize by remember { mutableStateOf(IntSize.Zero) }
    /** True while finishing a drag that started on the expanded ruler header (invisible capture layer). */
    var collapsingExpandedDragInProgress by remember { mutableStateOf(false) }
    /** Offset of the minimized bubble from its rest position during [collapsingExpandedDragInProgress]. */
    var rulerMinimizedDragVisual by remember { mutableStateOf(Offset.Zero) }
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
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val scheme = MaterialTheme.colorScheme
                val maxH = (maxHeight * 0.38f).coerceIn(200.dp, 400.dp)
                val wPx = with(density) { maxWidth.toPx() }
                val hPx = with(density) { maxHeight.toPx() }
                val padPx = with(density) { 8.dp.toPx() }
                val halfB = with(density) { (RulerMinimizeHandleSize / 2).toPx() }
                val headerHitHeight = 56.dp
                val along = rulerAlongFraction.coerceIn(0f, 1f)
                val isSide =
                    rulerScreenEdge == RulerScreenEdge.Start ||
                        rulerScreenEdge == RulerScreenEdge.End
                val measuredPw = expandedRulerPanelSize.width.toFloat()
                val measuredPh = expandedRulerPanelSize.height.toFloat()
                val halfW = when {
                    measuredPw > 0f -> measuredPw / 2f
                    isSide -> with(density) { 272.dp.toPx() / 2f }
                    else ->
                        with(density) {
                            (maxWidth - 16.dp).toPx().coerceAtLeast(120.dp.toPx()) / 2f
                        }
                }
                val halfH = when {
                    measuredPh > 0f -> measuredPh / 2f
                    else ->
                        with(density) { maxH.toPx() * 0.25f }
                            .coerceAtLeast(with(density) { 80.dp.toPx() })
                }
                val fullW = if (measuredPw > 0f) measuredPw else halfW * 2f
                val fullH = if (measuredPh > 0f) measuredPh else halfH * 2f
                val exitTrailingInsetPxTop = with(density) { creationExitTrailingInsetPx() }
                val exitTopInsetPxEnd = with(density) { creationExitTopClearancePx() }
                val exitTrailingInsetPx =
                    if (rulerScreenEdge == RulerScreenEdge.Top) exitTrailingInsetPxTop else 0f
                val exitTopInsetPx =
                    if (rulerScreenEdge == RulerScreenEdge.End) exitTopInsetPxEnd else 0f
                val centerPanel = rulerRestCenter(
                    rulerScreenEdge,
                    along,
                    wPx,
                    hPx,
                    padPx,
                    halfW,
                    halfH,
                    trailingInsetPx = exitTrailingInsetPx,
                    topInsetPx = exitTopInsetPx
                )
                val maxXt = (wPx - fullW).roundToInt().coerceAtLeast(0)
                val maxYt = (hPx - fullH).roundToInt().coerceAtLeast(0)
                val tlX = (centerPanel.x - fullW / 2f).roundToInt().coerceIn(0, maxXt)
                val tlY = (centerPanel.y - fullH / 2f).roundToInt().coerceIn(0, maxYt)
                val edgePaddingMod = when (rulerScreenEdge) {
                    RulerScreenEdge.Top ->
                        Modifier.statusBarsPadding().padding(horizontal = 8.dp)
                    RulerScreenEdge.Bottom ->
                        Modifier.navigationBarsPadding().padding(horizontal = 8.dp)
                    RulerScreenEdge.Start ->
                        Modifier.statusBarsPadding()
                            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                    RulerScreenEdge.End ->
                        Modifier.statusBarsPadding()
                            .padding(
                                end = 4.dp,
                                top = CreationExitRulerTopPadding,
                                bottom = 4.dp
                            )
                }
                val surfaceWidthMod = when {
                    isSide -> Modifier.width(272.dp)
                    rulerScreenEdge == RulerScreenEdge.Top ->
                        Modifier.wrapContentWidth(Alignment.Start)
                            .padding(end = CreationExitRulerEndPadding)
                    else -> Modifier.wrapContentWidth(Alignment.Start)
                }
                val showExpandedChrome = rulerExpanded || collapsingExpandedDragInProgress
                val rulerExpandedState = rememberUpdatedState(rulerExpanded)

                if (rulerExpanded && !collapsingExpandedDragInProgress) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(tlX, tlY) }
                            .then(surfaceWidthMod)
                            .heightIn(min = if (isSide) 200.dp else Dp.Unspecified, max = maxH)
                            .then(edgePaddingMod)
                            .onSizeChanged { expandedRulerPanelSize = it },
                        shape = RoundedCornerShape(22.dp),
                        color = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
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
                            onCollapse = {
                                rulerExpanded = false
                                collapsingExpandedDragInProgress = false
                                rulerMinimizedDragVisual = Offset.Zero
                            },
                            isSideBar = isSide,
                            maxHeight = maxH
                        )
                    }
                }

                if (collapsingExpandedDragInProgress) {
                    val cBubble =
                        rulerRestCenter(
                            rulerScreenEdge,
                            along,
                            wPx,
                            hPx,
                            padPx,
                            halfB,
                            halfB,
                            trailingInsetPx = exitTrailingInsetPx,
                            topInsetPx = exitTopInsetPx
                        ) + rulerMinimizedDragVisual
                    val maxXB = (wPx - 2f * halfB).roundToInt().coerceAtLeast(0)
                    val maxYB = (hPx - 2f * halfB).roundToInt().coerceAtLeast(0)
                    val btlX = (cBubble.x - halfB).roundToInt().coerceIn(0, maxXB)
                    val btlY = (cBubble.y - halfB).roundToInt().coerceIn(0, maxYB)
                    Box(
                        Modifier
                            .offset { IntOffset(btlX, btlY) }
                            .size(RulerMinimizeHandleSize)
                            .graphicsLayer {
                                scaleX = 1.08f
                                scaleY = 1.08f
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            }
                            .zIndex(1f)
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

                if (showExpandedChrome) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .then(
                                if (collapsingExpandedDragInProgress) {
                                    Modifier.fillMaxSize()
                                } else {
                                    Modifier
                                        .offset { IntOffset(tlX, tlY) }
                                        .then(surfaceWidthMod)
                                        .height(headerHitHeight)
                                }
                            )
                            .zIndex(3f)
                            .pointerInput(
                                rulerScreenEdge,
                                along,
                                wPx,
                                hPx,
                                padPx,
                                halfW,
                                halfH,
                                halfB,
                                exitTrailingInsetPx,
                                exitTopInsetPx
                            ) {
                                val touchSlopPx = viewConfiguration.touchSlop
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var crossedSlop = false
                                    var accumulatedPanelDrag = Offset.Zero
                                    var minimizedContinuation = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change =
                                            event.changes.find { it.id == down.id } ?: continue
                                        if (change.pressed) {
                                            val fromDown = change.position - down.position
                                            val justCrossedSlop =
                                                !crossedSlop &&
                                                    fromDown.getDistance() >= touchSlopPx
                                            if (justCrossedSlop) crossedSlop = true
                                            if (crossedSlop) {
                                                val delta = change.positionChange()
                                                if (!minimizedContinuation) {
                                                    accumulatedPanelDrag += delta
                                                    if (justCrossedSlop &&
                                                        rulerExpandedState.value
                                                    ) {
                                                        minimizedContinuation = true
                                                        rulerExpanded = false
                                                        collapsingExpandedDragInProgress = true
                                                        val fingerWorld =
                                                            rulerRestCenter(
                                                                rulerScreenEdge,
                                                                along,
                                                                wPx,
                                                                hPx,
                                                                padPx,
                                                                halfW,
                                                                halfH,
                                                                trailingInsetPx = exitTrailingInsetPx,
                                                                topInsetPx = exitTopInsetPx
                                                            ) + accumulatedPanelDrag
                                                        rulerMinimizedDragVisual =
                                                            fingerWorld -
                                                                rulerRestCenter(
                                                                    rulerScreenEdge,
                                                                    along,
                                                                    wPx,
                                                                    hPx,
                                                                    padPx,
                                                                    halfB,
                                                                    halfB,
                                                                    trailingInsetPx = exitTrailingInsetPx,
                                                                    topInsetPx = exitTopInsetPx
                                                                )
                                                    }
                                                } else {
                                                    rulerMinimizedDragVisual += delta
                                                }
                                            }
                                        }
                                        if (!change.pressed && change.previousPressed) {
                                            change.consume()
                                            if (!minimizedContinuation) {
                                                rulerExpanded = false
                                                collapsingExpandedDragInProgress = false
                                                rulerMinimizedDragVisual = Offset.Zero
                                            } else {
                                                val p =
                                                    rulerRestCenter(
                                                        rulerScreenEdge,
                                                        along,
                                                        wPx,
                                                        hPx,
                                                        padPx,
                                                        halfB,
                                                        halfB,
                                                        trailingInsetPx = exitTrailingInsetPx,
                                                        topInsetPx = exitTopInsetPx
                                                    ) + rulerMinimizedDragVisual
                                                val e2 = rulerNearestEdge(p, wPx, hPx)
                                                rulerEdgeName = e2.name
                                                val projectTrailing =
                                                    if (e2 == RulerScreenEdge.Top) exitTrailingInsetPxTop else 0f
                                                val projectTop =
                                                    if (e2 == RulerScreenEdge.End) exitTopInsetPxEnd else 0f
                                                rulerAlongFraction =
                                                    rulerProjectAlong(
                                                        p,
                                                        e2,
                                                        wPx,
                                                        hPx,
                                                        padPx,
                                                        halfB,
                                                        halfB,
                                                        trailingInsetPx = projectTrailing,
                                                        topInsetPx = projectTop
                                                    )
                                                collapsingExpandedDragInProgress = false
                                                rulerMinimizedDragVisual = Offset.Zero
                                            }
                                            break
                                        }
                                        change.consume()
                                    }
                                }
                            }
                    ) {}
                }

                if (!rulerExpanded && !collapsingExpandedDragInProgress) {
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
        val exitTrailingInsetPxTop = with(density) { creationExitTrailingInsetPx() }
        val exitTopInsetPxEnd = with(density) { creationExitTopClearancePx() }
        val trailingInsetPx =
            if (rulerScreenEdge == RulerScreenEdge.Top) exitTrailingInsetPxTop else 0f
        val topInsetPx =
            if (rulerScreenEdge == RulerScreenEdge.End) exitTopInsetPxEnd else 0f
        val c = rulerRestCenter(
            rulerScreenEdge,
            along,
            w,
            h,
            pad,
            halfB,
            halfB,
            trailingInsetPx = trailingInsetPx,
            topInsetPx = topInsetPx
        ) + drag
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
                .pointerInput(
                    rulerScreenEdge,
                    w,
                    h,
                    along,
                    expandMaxDragPx,
                    trailingInsetPx,
                    topInsetPx,
                    exitTrailingInsetPxTop,
                    exitTopInsetPxEnd
                ) {
                    // detectDragGestures only invokes onDragEnd after touch slop, so a plain tap never
                    // expanded the ruler. Track slop explicitly and treat short drags as expand.
                    val touchSlopPx = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var crossedSlop = false
                        var accumulatedDrag = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.find { it.id == down.id } ?: continue
                            if (change.pressed) {
                                val fromDown = change.position - down.position
                                if (!crossedSlop && fromDown.getDistance() >= touchSlopPx) {
                                    crossedSlop = true
                                }
                                if (crossedSlop) {
                                    accumulatedDrag += change.positionChange()
                                    drag = accumulatedDrag
                                }
                            }
                            if (!change.pressed && change.previousPressed) {
                                change.consume()
                                val finalDrag =
                                    if (crossedSlop) accumulatedDrag else Offset.Zero
                                if (!crossedSlop || finalDrag.getDistance() < expandMaxDragPx) {
                                    onExpand()
                                } else {
                                    val p = rulerRestCenter(
                                        rulerScreenEdge,
                                        along,
                                        w,
                                        h,
                                        pad,
                                        halfB,
                                        halfB,
                                        trailingInsetPx = trailingInsetPx,
                                        topInsetPx = topInsetPx
                                    ) + finalDrag
                                    val e2 = rulerNearestEdge(p, w, h)
                                    val projectTrailing =
                                        if (e2 == RulerScreenEdge.Top) exitTrailingInsetPxTop else 0f
                                    val projectTop =
                                        if (e2 == RulerScreenEdge.End) exitTopInsetPxEnd else 0f
                                    onPlacementChange(
                                        e2,
                                        rulerProjectAlong(
                                            p,
                                            e2,
                                            w,
                                            h,
                                            pad,
                                            halfB,
                                            halfB,
                                            trailingInsetPx = projectTrailing,
                                            topInsetPx = projectTop
                                        )
                                    )
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
