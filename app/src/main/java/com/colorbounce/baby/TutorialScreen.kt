package com.colorbounce.baby

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val STEP_COUNT = 4
private const val SUCCESS_ADVANCE_DELAY_MS = 4000L
private const val FORMATION_ADVANCE_DELAY_MS = 4500L

/** Part 4 only: header band inside the tutorial mini-window (switch row). */
private val TutorialWindowInsideHeaderHeight = 64.dp

/**
 * Part 4 portrait: cap instruction + menu explanation height so the mini-window stays aligned with
 * Parts 1–3 (scroll inside this slot). Landscape uses a weighted column instead—no cap.
 */
private val TutorialLongInstructionBodyMaxHeight = 168.dp

/** Portrait: gap between the mini-window and the footer (room for menu explanations under the window). */
private val TutorialPortraitWindowToFooterSpacer = 32.dp

/** Landscape: extra gap below the main row so the footer column clears the screen bottom comfortably. */
private val TutorialLandscapeOuterBottomSpacer = 36.dp

/** Landscape: inset above the bottom of the left pane so the hint + dots sit off the lower edge. */
private val TutorialLandscapeFooterLiftFromPaneBottom = 12.dp

@Composable
fun TutorialScreen(onDismiss: () -> Unit) {
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    /** Prevents double completion (rapid taps / overlapping gestures) from firing [onDismiss] twice. */
    var finishDispatched by remember { mutableStateOf(false) }

    fun nextStep() {
        if (finishDispatched) return
        if (currentStep < STEP_COUNT - 1) {
            currentStep += 1
        } else {
            finishDispatched = true
            onDismiss()
        }
    }

    when (currentStep) {
        0 -> CreateShapeTutorialStep(onAdvance = ::nextStep)
        1 -> SizeAndSpeedTutorialStep(onAdvance = ::nextStep)
        2 -> MoveShapeTutorialStep(onAdvance = ::nextStep)
        else -> SelectShapeTutorialStep(onFinish = ::nextStep)
    }
}

@Composable
private fun CreateShapeTutorialStep(onAdvance: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val settings = remember { tutorialSettings() }
    val viewModel = remember { GameViewModel() }
    val shapeFrame by viewModel.shapes.collectAsState(ShapesFrame(0L, emptyList()))
    val shapes = shapeFrame.shapes

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var stageCompleted by remember { mutableStateOf(false) }

    val infinite = rememberInfiniteTransition(label = "tutorial_create_shape")
    val handProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "create_hand_tap"
    )

    val targetArea = remember(canvasSize) {
        val width = canvasSize.width.toFloat()
        val height = canvasSize.height.toFloat()
        if (width <= 0f || height <= 0f) {
            Rect.Zero
        } else {
            Rect(
                left = width * 0.25f,
                top = height * 0.2f,
                right = width * 0.75f,
                bottom = height * 0.7f
            )
        }
    }

    LaunchedEffect(canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            viewModel.setScreenSize(canvasSize.width.toFloat(), canvasSize.height.toFloat())
        }
    }

    TutorialPhysicsLoop(viewModel = viewModel, settings = settings)

    LaunchedEffect(stageCompleted) {
        if (stageCompleted) {
            delay(FORMATION_ADVANCE_DELAY_MS)
            onAdvance()
        }
    }

    TutorialStepLayout(
        title = "Part 1 - Create a shape",
        body = "Tap inside the highlighted area to create one shape. Watch it appear!",
        step = 0,
        onOutsideTap = onAdvance
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(targetArea, stageCompleted) {
                    detectTapGestures { tapOffset ->
                        if (stageCompleted) {
                            onAdvance()
                            return@detectTapGestures
                        }
                        if (targetArea.contains(tapOffset)) {
                            if (shapes.isEmpty()) {
                                val pointerId = 1001L
                                viewModel.startInteraction(tapOffset, settings, pointerId)
                                viewModel.endInteraction(settings, pointerId)
                                stageCompleted = true
                            }
                        } else {
                            onAdvance()
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = scheme.surfaceVariant.copy(alpha = 0.25f),
                    topLeft = Offset(targetArea.left, targetArea.top),
                    size = targetArea.size
                )
                drawRect(
                    color = scheme.primary,
                    topLeft = Offset(targetArea.left, targetArea.top),
                    size = targetArea.size,
                    style = Stroke(width = 4f)
                )
                drawTutorialShapes(shapes)
            }

            if (!stageCompleted) {
                HandHint(
                    modifier = Modifier.fillMaxSize(),
                    progress = handProgress,
                    gesture = TutorialGesture.TAP,
                    anchor = targetArea.center
                )
            }
        }
    }
}

@Composable
private fun SizeAndSpeedTutorialStep(onAdvance: () -> Unit) {
    val settings = remember { tutorialSettings(maxVelocity = 1800) }
    val viewModel = remember { GameViewModel() }
    val shapeFrame by viewModel.shapes.collectAsState(ShapesFrame(0L, emptyList()))
    val shapes = shapeFrame.shapes

    var sceneSize by remember { mutableStateOf(IntSize.Zero) }
    var releasedShapeId by remember { mutableStateOf<Long?>(null) }
    var releasePoint by remember { mutableStateOf<Offset?>(null) }
    var stageCompleted by remember { mutableStateOf(false) }

    val infinite = rememberInfiniteTransition(label = "tutorial_size_speed")
    val handProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "size_speed_drag"
    )

    LaunchedEffect(sceneSize) {
        if (sceneSize.width > 0 && sceneSize.height > 0) {
            viewModel.setScreenSize(sceneSize.width.toFloat(), sceneSize.height.toFloat())
        }
    }

    TutorialPhysicsLoop(viewModel = viewModel, settings = settings)

    LaunchedEffect(stageCompleted) {
        if (stageCompleted) {
            delay(SUCCESS_ADVANCE_DELAY_MS)
            onAdvance()
        }
    }

    LaunchedEffect(shapes, releasedShapeId, releasePoint) {
        if (stageCompleted) return@LaunchedEffect
        val shapeId = releasedShapeId ?: return@LaunchedEffect
        val release = releasePoint ?: return@LaunchedEffect
        val shape = shapes.firstOrNull { it.id == shapeId } ?: return@LaunchedEffect

        val movedDistance = (Offset(shape.x, shape.y) - release).getDistance()
        val speed = hypot(shape.vx.toDouble(), shape.vy.toDouble()).toFloat()
        if (movedDistance > 20f && speed > 40f) {
            stageCompleted = true
        }
    }

    TutorialStepLayout(
        title = "Part 2 - Size and speed",
        body = "Drag to form a shape. Longer drag makes it bigger. Release to launch it!",
        step = 1,
        onOutsideTap = onAdvance
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { sceneSize = it }
                    .pointerInput(settings, stageCompleted, shapes.size) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    val pointerId = change.id.value + 2_000L
                                    when {
                                        change.pressed && !change.previousPressed -> {
                                            if (!stageCompleted && shapes.isEmpty()) {
                                                viewModel.startInteraction(
                                                    point = change.position,
                                                    settings = settings,
                                                    pointerId = pointerId,
                                                    constrainInsideScreen = true
                                                )
                                                change.consume()
                                            }
                                        }

                                        change.pressed && change.previousPressed -> {
                                            if (!stageCompleted) {
                                                val dragAmount =
                                                    change.position - change.previousPosition
                                                viewModel.onDrag(
                                                    point = change.position,
                                                    dragAmount = dragAmount,
                                                    settings = settings,
                                                    pointerId = pointerId,
                                                    constrainInsideScreen = true
                                                )
                                                change.consume()
                                            }
                                        }

                                        !change.pressed && change.previousPressed -> {
                                            if (!stageCompleted && shapes.isNotEmpty()) {
                                                val shapeId = shapes.lastOrNull()?.id
                                                if (shapeId != null) {
                                                    releasedShapeId = shapeId
                                                    releasePoint = change.position
                                                }
                                                viewModel.endInteraction(settings, pointerId)
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawTutorialShapes(shapes)
                }

                if (!stageCompleted && shapes.isEmpty()) {
                    HandHint(
                        modifier = Modifier.fillMaxSize(),
                        progress = handProgress,
                        gesture = TutorialGesture.FORM_DRAG,
                        anchor = Offset.Unspecified
                    )
                }

                Text(
                    text = if (stageCompleted) "" else "Drag then release to see trajectory",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (stageCompleted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(stageCompleted) {
                            detectTapGestures { onAdvance() }
                        }
                )
            }
        }
    }
}

@Composable
private fun MoveShapeTutorialStep(onAdvance: () -> Unit) {
    val settings = remember { tutorialSettings(maxVelocity = 1800) }
    val viewModel = remember { GameViewModel() }
    val shapeFrame by viewModel.shapes.collectAsState(ShapesFrame(0L, emptyList()))
    val shapes = shapeFrame.shapes

    var playgroundSize by remember { mutableStateOf(IntSize.Zero) }
    var initialCenter by remember { mutableStateOf<Offset?>(null) }
    var trackedShapeId by remember { mutableStateOf<Long?>(null) }
    var initialHue by remember { mutableStateOf<Float?>(null) }
    var movedEnough by remember { mutableStateOf(false) }
    var colorShifted by remember { mutableStateOf(false) }
    var stageCompleted by remember { mutableStateOf(false) }
    var userGrabbedShape by remember { mutableStateOf(false) }

    val latestShapes by rememberUpdatedState(shapes)
    val latestStageCompleted by rememberUpdatedState(stageCompleted)
    val latestUserGrabbedShape by rememberUpdatedState(userGrabbedShape)

    val infinite = rememberInfiniteTransition(label = "tutorial_move_shape")
    val handProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "move_shape_drag"
    )

    LaunchedEffect(playgroundSize) {
        if (playgroundSize.width > 0 && playgroundSize.height > 0) {
            viewModel.setScreenSize(playgroundSize.width.toFloat(), playgroundSize.height.toFloat())
            if (shapes.isEmpty()) {
                val start = Offset(playgroundSize.width * 0.5f, playgroundSize.height * 0.5f)
                val pointerId = 3001L
                viewModel.startInteraction(
                    point = start,
                    settings = settings,
                    pointerId = pointerId,
                    constrainInsideScreen = true
                )
                val dragOut = 150f
                val targetPoint = Offset(start.x + dragOut, start.y)
                viewModel.onDrag(
                    point = targetPoint,
                    dragAmount = targetPoint - start,
                    settings = settings,
                    pointerId = pointerId,
                    resizeOnDrag = true,
                    constrainInsideScreen = true
                )
                viewModel.endInteraction(settings, pointerId, applyLaunchVelocity = false)
                val placed = viewModel.shapes.value.shapes.firstOrNull()
                initialCenter = placed?.let { Offset(it.x, it.y) } ?: start
            }
        }
    }

    TutorialPhysicsLoop(viewModel = viewModel, settings = settings)

    LaunchedEffect(stageCompleted) {
        if (stageCompleted) {
            delay(SUCCESS_ADVANCE_DELAY_MS)
            onAdvance()
        }
    }

    LaunchedEffect(shapes, initialCenter) {
        val shape = shapes.firstOrNull() ?: return@LaunchedEffect
        if (trackedShapeId == null) {
            trackedShapeId = shape.id
            initialHue = shape.hue
        }

        val trackedId = trackedShapeId ?: return@LaunchedEffect
        val tracked = shapes.firstOrNull { it.id == trackedId } ?: return@LaunchedEffect
        val start = initialCenter ?: return@LaunchedEffect

        if ((Offset(tracked.x, tracked.y) - start).getDistance() > 60f) {
            movedEnough = true
        }

        val hueStart = initialHue
        if (hueStart != null && hueDistance(hueStart, tracked.hue) > 15f) {
            colorShifted = true
        }

        if (movedEnough && colorShifted) {
            stageCompleted = true
        }
    }

    TutorialStepLayout(
        title = "Part 3 - Move and color change",
        body = "Drag the shape to move it. Notice how its color cycles while you hold it!",
        step = 2,
        onOutsideTap = onAdvance
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { playgroundSize = it }
                    // Stable keys: never restart this block on grab — that would cancel mid-drag while the
                    // ViewModel still marks the shape held (hue cycles, position frozen).
                    .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var activePointerId: Long? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                val pointerId = change.id.value + 4_000L
                                when {
                                    change.pressed && !change.previousPressed -> {
                                        if (!latestStageCompleted && activePointerId == null) {
                                            val hit = latestShapes.find { shape ->
                                                val dx = change.position.x - shape.x
                                                val dy = change.position.y - shape.y
                                                hypot(dx, dy) < (shape.width / 2f) * 1.6f
                                            }
                                            if (hit != null) {
                                                userGrabbedShape = true
                                                viewModel.startInteraction(
                                                    point = change.position,
                                                    settings = settings,
                                                    pointerId = pointerId,
                                                    constrainInsideScreen = true
                                                )
                                                activePointerId = pointerId
                                                change.consume()
                                            } else if (latestUserGrabbedShape) {
                                                onAdvance()
                                                change.consume()
                                            }
                                        }
                                    }

                                    change.pressed && change.previousPressed -> {
                                        if (activePointerId == pointerId) {
                                            val dragAmount = change.position - change.previousPosition
                                            viewModel.onDrag(
                                                point = change.position,
                                                dragAmount = dragAmount,
                                                settings = settings,
                                                pointerId = pointerId,
                                                resizeOnDrag = false,
                                                constrainInsideScreen = true
                                            )
                                            change.consume()
                                        }
                                    }

                                    !change.pressed && change.previousPressed -> {
                                        if (activePointerId == pointerId) {
                                            viewModel.endInteraction(settings, pointerId)
                                            activePointerId = null
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawTutorialShapes(shapes)
                }

                if (!stageCompleted) {
                    HandHint(
                        modifier = Modifier.fillMaxSize(),
                        progress = handProgress,
                        gesture = TutorialGesture.MOVE,
                        anchor = shapes.firstOrNull()?.let { Offset(it.x, it.y) }
                            ?: Offset.Unspecified
                    )
                }

                Text(
                    text = "Hold and move to cycle colors",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (stageCompleted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(stageCompleted) {
                            detectTapGestures { onAdvance() }
                        }
                )
            }
        }
    }
}

@Composable
private fun TutorialDoubleTapToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Double-tap to open shape menu",
            color = scheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = scheme.primary,
                checkedTrackColor = scheme.primaryContainer,
                uncheckedThumbColor = scheme.outline,
                uncheckedTrackColor = scheme.surfaceVariant,
                uncheckedBorderColor = scheme.outline
            )
        )
    }
}

@Composable
private fun SelectShapeTutorialStep(onFinish: () -> Unit) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val settings = remember { tutorialSettings(maxVelocity = 1800) }
    val viewModel = remember { GameViewModel() }
    val shapeFrame by viewModel.shapes.collectAsState(ShapesFrame(0L, emptyList()))
    val shapes = shapeFrame.shapes

    var playgroundSize by remember { mutableStateOf(IntSize.Zero) }
    var menuRevealed by rememberSaveable { mutableStateOf(false) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    var tutorialDoubleTapMenuEnabled by rememberSaveable { mutableStateOf(false) }

    val doubleTap = remember { DoubleTapState() }
    val slopPx = with(LocalDensity.current) { 20.dp.toPx() }

    val latestMenuRevealed by rememberUpdatedState(menuRevealed)
    val latestShapes by rememberUpdatedState(shapes)
    val latestTutorialDoubleTapEnabled by rememberUpdatedState(tutorialDoubleTapMenuEnabled)

    LaunchedEffect(tutorialDoubleTapMenuEnabled) {
        if (!tutorialDoubleTapMenuEnabled) {
            doubleTap.clear()
        }
    }

    val infiniteDoubleTap = rememberInfiniteTransition(label = "tutorial_double_tap_shape_menu")
    val doubleTapHandProgress by infiniteDoubleTap.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "double_tap_hint"
    )

    LaunchedEffect(playgroundSize) {
        if (playgroundSize.width > 0 && playgroundSize.height > 0) {
            viewModel.setScreenSize(playgroundSize.width.toFloat(), playgroundSize.height.toFloat())
            if (shapes.isEmpty()) {
                val start = Offset(playgroundSize.width * 0.5f, playgroundSize.height * 0.42f)
                val pointerId = 5001L
                viewModel.startInteraction(
                    point = start,
                    settings = settings,
                    pointerId = pointerId,
                    constrainInsideScreen = true
                )
                val dragOut = 150f
                val targetPoint = Offset(start.x + dragOut, start.y)
                viewModel.onDrag(
                    point = targetPoint,
                    dragAmount = targetPoint - start,
                    settings = settings,
                    pointerId = pointerId,
                    resizeOnDrag = true,
                    constrainInsideScreen = true
                )
                viewModel.endInteraction(settings, pointerId, applyLaunchVelocity = false)
            }
        }
    }

    TutorialStepLayout(
        title = "Part 4 - Shape menu",
        body = "Switch the setting to allow Double-tap for the shape menu.",
        step = 3,
        instructionBodyMaxHeight = TutorialLongInstructionBodyMaxHeight,
        onOutsideTap = onFinish,
        footerHint = if (menuRevealed) {
            "Tap outside the shape and toolbar to finish"
        } else {
            null
        },
        insideWindowHeader = {
            TutorialDoubleTapToggleRow(
                checked = tutorialDoubleTapMenuEnabled,
                onCheckedChange = { tutorialDoubleTapMenuEnabled = it }
            )
        },
        belowBodyContent = if (isLandscape && menuRevealed) {
            { ShapeMenuExplainSection() }
        } else {
            null
        },
        belowMiniWindowContent = if (!isLandscape && menuRevealed) {
            { ShapeMenuExplainSection() }
        } else {
            null
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { playgroundSize = it }
        ) {
            val density = LocalDensity.current
            val scheme = MaterialTheme.colorScheme
            val marginPx = with(density) { 8.dp.toPx() }
            val gapPx = with(density) { 10.dp.toPx() }
            val estMenuW = with(density) { 220.dp.toPx() }
            val estMenuH = with(density) { 56.dp.toPx() }
            val screenW = with(density) { this@BoxWithConstraints.maxWidth.toPx() }
            val screenH = with(density) { this@BoxWithConstraints.maxHeight.toPx() }

            val menuSurfaceLum = scheme.surfaceContainerHigh.luminance()
            val menuIconInkForBar = if (menuSurfaceLum < 0.5f) Color.White else Color.Black
            val menuIconInkDimForBar = menuIconInkForBar.copy(alpha = 0.45f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTutorialShapes(shapes)
            }

            val shapeForHint = shapes.firstOrNull()
            if (!menuRevealed && tutorialDoubleTapMenuEnabled && shapeForHint != null) {
                HandHint(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0.4f),
                    progress = doubleTapHandProgress,
                    gesture = TutorialGesture.DOUBLE_TAP,
                    anchor = Offset(shapeForHint.x, shapeForHint.y)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0.5f)
                    .pointerInput(
                        menuRevealed,
                        menuSize,
                        playgroundSize,
                        shapes,
                        tutorialDoubleTapMenuEnabled
                    ) {
                        awaitPointerEventScope {
                            val downPos = mutableMapOf<Long, Offset>()
                            while (true) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    val pid = change.id.value + 6_000L
                                    when {
                                        change.pressed && !change.previousPressed -> {
                                            val p = change.position
                                            val shape = latestShapes.firstOrNull()
                                            if (!latestMenuRevealed && shape != null &&
                                                latestTutorialDoubleTapEnabled
                                            ) {
                                                val hit = viewModel.shapeAt(p)
                                                if (hit != null &&
                                                    doubleTap.isSecondTapOnShape(hit.id)
                                                ) {
                                                    menuRevealed = true
                                                    change.consume()
                                                    continue
                                                }
                                            }
                                            if (latestMenuRevealed && shape != null) {
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
                                                val shapeRect = tutorialShapeVisualRect(shape)
                                                val dismissOutsideRect =
                                                    menuRect.unionRect(shapeRect).outset(6f)
                                                if (!dismissOutsideRect.contains(p)) {
                                                    onFinish()
                                                    change.consume()
                                                    continue
                                                }
                                            }
                                            downPos[pid] = p
                                        }

                                        change.pressed && change.previousPressed -> {
                                            // No drag handling needed on this step.
                                        }

                                        !change.pressed && change.previousPressed -> {
                                            val start = downPos.remove(pid) ?: continue
                                            val moved =
                                                (change.position - start).getDistance() > slopPx
                                            val shape = latestShapes.firstOrNull()
                                            if (!latestMenuRevealed && shape != null && !moved &&
                                                latestTutorialDoubleTapEnabled
                                            ) {
                                                val hit = viewModel.shapeAt(change.position)
                                                if (hit != null) {
                                                    doubleTap.recordShapeTap(hit.id)
                                                } else {
                                                    doubleTap.clear()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            )

            shapes.firstOrNull()?.let { shape ->
                if (menuRevealed) {
                    Box(
                        modifier = Modifier
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
                            .zIndex(1f)
                    ) {
                        ShapeContextMenuBar(
                            shape = shape,
                            menuIconInk = menuIconInkForBar,
                            menuIconInkDim = menuIconInkDimForBar,
                            onDelete = {},
                            onTogglePin = {},
                            onToggleImmortal = {},
                            onToggleFreezeHueWhileDragging = {},
                            rulerHueGloballyLocked = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapeMenuExplainSection() {
    val scheme = MaterialTheme.colorScheme
    val menuSurfaceLum = scheme.surfaceContainerHigh.luminance()
    val menuIconInk = if (menuSurfaceLum < 0.5f) Color.White else Color.Black
    val menuIconInkDim = menuIconInk.copy(alpha = 0.45f)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShapeMenuExplainRow(
            icon = Icons.Filled.Delete,
            tint = menuIconInk,
            text = "Delete this shape.",
            textColor = scheme.onBackground
        )
        ShapeMenuExplainRow(
            icon = Icons.Filled.PushPin,
            tint = menuIconInkDim,
            text = "Pin: stays still until you drag it.",
            textColor = scheme.onBackground
        )
        ShapeMenuExplainRow(
            icon = Icons.Outlined.Timer,
            tint = menuIconInkDim,
            text = "Timer: may time out; change to ∞ to keep forever.",
            textColor = scheme.onBackground
        )
        ShapeMenuExplainRow(
            icon = Icons.Outlined.LockOpen,
            tint = Color.White,
            rainbowGradient = true,
            text = "Hue lock off: color shifts while dragging.",
            textColor = scheme.onBackground
        )
    }
}

@Composable
private fun ShapeMenuExplainRow(
    icon: ImageVector,
    tint: Color,
    text: String,
    rainbowGradient: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (rainbowGradient) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .drawWithCache {
                        val brush = Brush.linearGradient(
                            colors = rainbowLockOpenGradientColors,
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush = brush, blendMode = BlendMode.SrcIn)
                        }
                    }
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = 2
        )
    }
}

@Composable
private fun TutorialInstructionBodyColumn(
    modifier: Modifier = Modifier.fillMaxWidth(),
    body: String,
    textAlign: TextAlign,
    scheme: ColorScheme,
    maxHeight: Dp?,
    belowBodyContent: (@Composable () -> Unit)?,
    wrapBelowBodyInFillWidthColumn: Boolean
) {
    if (body.isBlank() && belowBodyContent == null) return
    val scrollState = rememberScrollState()
    @Composable
    fun Inner() {
        if (body.isNotBlank()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onBackground,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (belowBodyContent != null) {
            if (body.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
            }
            if (wrapBelowBodyInFillWidthColumn) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    belowBodyContent()
                }
            } else {
                belowBodyContent()
            }
        }
    }
    val scrollableModifier = modifier
        .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
        .verticalScroll(scrollState)
    Column(modifier = scrollableModifier) {
        Inner()
    }
}

@Composable
private fun TutorialStepLayout(
    title: String,
    body: String,
    step: Int,
    onOutsideTap: () -> Unit,
    footerHint: String? = null,
    instructionBodyMaxHeight: Dp? = null,
    insideWindowHeader: (@Composable () -> Unit)? = null,
    belowBodyContent: (@Composable () -> Unit)? = null,
    belowMiniWindowContent: (@Composable () -> Unit)? = null,
    windowContent: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    var tutorialWindowBounds by remember { mutableStateOf<Rect?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = scheme.background,
        contentColor = scheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp)
        ) {
            if (isLandscape) {
                Spacer(Modifier.height(56.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(tutorialWindowBounds) {
                            detectTapGestures { tapOffset ->
                                val bounds = tutorialWindowBounds
                                if (bounds == null || !bounds.contains(tapOffset)) {
                                    onOutsideTap()
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = scheme.primary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        TutorialInstructionBodyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            body = body,
                            textAlign = TextAlign.Start,
                            scheme = scheme,
                            maxHeight = null,
                            belowBodyContent = belowBodyContent,
                            wrapBelowBodyInFillWidthColumn = false
                        )
                        Spacer(Modifier.height(8.dp))
                        TutorialFooter(step = step, hint = footerHint, compact = true)
                        Spacer(Modifier.height(TutorialLandscapeFooterLiftFromPaneBottom))
                    }

                    Spacer(Modifier.width(24.dp))

                    TutorialWindow(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .padding(vertical = 18.dp),
                        onOutsideTap = onOutsideTap,
                        onBoundsChanged = { tutorialWindowBounds = it },
                        insideWindowHeader = insideWindowHeader,
                        belowMiniWindowContent = belowMiniWindowContent,
                        content = windowContent
                    )
                }
                Spacer(Modifier.height(TutorialLandscapeOuterBottomSpacer))
            } else {
                Column(
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures { onOutsideTap() }
                    }
                ) {
                    Spacer(Modifier.height(130.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    TutorialInstructionBodyColumn(
                        body = body,
                        textAlign = TextAlign.Center,
                        scheme = scheme,
                        maxHeight = instructionBodyMaxHeight,
                        belowBodyContent = belowBodyContent,
                        wrapBelowBodyInFillWidthColumn = true
                    )
                    Spacer(Modifier.height(40.dp))
                }

                TutorialWindow(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onOutsideTap = onOutsideTap,
                    insideWindowHeader = insideWindowHeader,
                    belowMiniWindowContent = belowMiniWindowContent,
                    content = windowContent
                )

                Column(
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures { onOutsideTap() }
                    }
                ) {
                    // Spacer above footer: separation from mini-window / explanations below the window.
                    Spacer(Modifier.height(TutorialPortraitWindowToFooterSpacer))
                    TutorialFooter(step = step, hint = footerHint)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun TutorialWindow(
    modifier: Modifier,
    onOutsideTap: () -> Unit,
    onBoundsChanged: (Rect) -> Unit = {},
    insideWindowHeader: (@Composable () -> Unit)? = null,
    belowMiniWindowContent: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val position = coordinates.positionInParent()
            onBoundsChanged(
                Rect(
                    left = position.x,
                    top = position.y,
                    right = position.x + coordinates.size.width,
                    bottom = position.y + coordinates.size.height
                )
            )
        }
    ) {
        val windowAspectRatio = 1.2f
        val outerMargin = 12.dp
        val maxWindowWidth = maxOf(0.dp, this.maxWidth - outerMargin * 2)
        val maxWindowHeight = maxOf(0.dp, this.maxHeight - outerMargin * 2)
        val windowWidth = minOf(maxWindowWidth, maxWindowHeight * windowAspectRatio)
        val windowHeight = if (windowAspectRatio == 0f) 0.dp else windowWidth / windowAspectRatio
        val headerHeight = if (insideWindowHeader != null) TutorialWindowInsideHeaderHeight else 0.dp

        // Transparent layer to catch taps outside the mini-window area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { onOutsideTap() }
                }
        )

        // Rounded window: same outer size for all steps; part 4 fits the switch header inside (shape area is shorter).
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = windowWidth, height = windowHeight)
                .background(
                    color = scheme.surfaceVariant.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(22.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = scheme.outline.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(12.dp)
        ) {
            if (insideWindowHeader != null) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(headerHeight)
                            .background(
                                color = scheme.surfaceContainerHigh.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        insideWindowHeader()
                    }
                    HorizontalDivider(
                        color = scheme.outline.copy(alpha = 0.45f),
                        thickness = 1.dp
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(scheme.surface.copy(alpha = 0.08f))
                    ) {
                        content()
                    }
                }
            } else {
                content()
            }
        }

        // Drawn in a separate layer so layout never recenters the play area above.
        if (belowMiniWindowContent != null) {
            val explainTop = maxHeight / 2 + windowHeight / 2 + 8.dp
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = explainTop)
                    .fillMaxWidth()
                    .padding(horizontal = outerMargin),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                belowMiniWindowContent()
            }
        }
    }
}

@Composable
private fun TutorialFooter(step: Int, hint: String? = null, compact: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val hintStyle =
        if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    val hintTopMinLines = if (compact) 1 else 2
    val hintTopMaxLines = 2
    val gapHintToDots = if (compact) 6.dp else 12.dp
    val dotGap = if (compact) 14.dp else 18.dp
    val dotRadius = if (compact) 4.dp else 5.dp
    val dotsCanvasHeight = if (compact) 8.dp else 10.dp
    val indicatorWidth = dotGap * (STEP_COUNT - 1) + dotRadius * 2

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = hint ?: "Tap outside the window to skip this part",
            style = hintStyle,
            color = scheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            minLines = hintTopMinLines,
            maxLines = hintTopMaxLines,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(gapHintToDots))
        Canvas(modifier = Modifier.size(width = indicatorWidth, height = dotsCanvasHeight)) {
            val radius = dotRadius.toPx()
            val gap = dotGap.toPx()
            repeat(STEP_COUNT) { index ->
                val x = radius + index * gap
                drawCircle(
                    color = if (index == step) scheme.primary else scheme.surfaceVariant,
                    radius = radius,
                    center = Offset(x, size.height / 2f)
                )
            }
        }
    }
}

@Composable
private fun TutorialPhysicsLoop(viewModel: GameViewModel, settings: AppSettings) {
    LaunchedEffect(viewModel, settings) {
        var last = 0L
        while (isActive) {
            withFrameNanos { frame ->
                if (last == 0L) {
                    last = frame
                    return@withFrameNanos
                }
                val delta = (frame - last) / 1_000_000_000f
                last = frame
                viewModel.updatePhysics(delta, settings)
            }
        }
    }
}

private fun tutorialSettings(maxVelocity: Int = 1600): AppSettings = AppSettings(
    selectedShapes = setOf(ShapeType.CIRCLE),
    shapeSelectionMode = ShapeSelectionMode.ALTERNATE,
    shapeTimeoutImmortal = true,
    shapeTimeoutSeconds = 10,
    maxShapes = 1,
    autoSpawnInactivitySeconds = 0,
    maxVelocityPxPerSec = maxVelocity
)

private fun hueDistance(a: Float, b: Float): Float {
    val diff = abs(a.normalizeHue() - b.normalizeHue())
    return min(diff, 360f - diff)
}

private fun tutorialShapeVisualRect(shape: GameShape): Rect {
    val halfW = shape.width / 2f
    val halfH = shape.height / 2f
    return Rect(
        left = shape.x - halfW,
        top = shape.y - halfH,
        right = shape.x + halfW,
        bottom = shape.y + halfH
    )
}

private fun Rect.unionRect(other: Rect): Rect = Rect(
    left = minOf(left, other.left),
    top = minOf(top, other.top),
    right = maxOf(right, other.right),
    bottom = maxOf(bottom, other.bottom)
)

private fun Rect.outset(padding: Float): Rect = Rect(
    left = left - padding,
    top = top - padding,
    right = right + padding,
    bottom = bottom + padding
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTutorialShapes(shapes: List<GameShape>) {
    shapes.forEach { shape ->
        val topLeft = Offset(shape.x - shape.width / 2f, shape.y - shape.height / 2f)
        when (shape.type) {
            ShapeType.CIRCLE -> drawCircle(
                color = shape.color,
                radius = shape.width / 2f,
                center = Offset(shape.x, shape.y)
            )

            ShapeType.RECTANGLE -> drawRect(
                color = shape.color,
                topLeft = topLeft,
                size = Size(shape.width, shape.height)
            )

            ShapeType.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(shape.x, shape.y - shape.height / 2f)
                    lineTo(shape.x - shape.width / 2f, shape.y + shape.height / 2f)
                    lineTo(shape.x + shape.width / 2f, shape.y + shape.height / 2f)
                    close()
                }
                drawPath(path, color = shape.color)
            }

            ShapeType.ARCH -> {
                val centerX = shape.x
                val radius = shape.width / 2f
                val centerY = shape.y + radius
                val strokeWidth = min(shape.height, radius * 0.6f)
                drawArc(
                    color = shape.color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            }

            ShapeType.STAR, ShapeType.HEART, ShapeType.DIAMOND -> {
                val vx = FloatArray(48)
                val vy = FloatArray(48)
                val n = fillPolygonVertices(shape, vx, vy)
                if (n >= 3) {
                    val path = Path().apply {
                        moveTo(vx[0], vy[0])
                        for (i in 1 until n) lineTo(vx[i], vy[i])
                        close()
                    }
                    drawPath(path, color = shape.color)
                }
            }
        }
    }
}

private enum class TutorialGesture {
    TAP,
    FORM_DRAG,
    MOVE,
    DOUBLE_TAP
}

@Composable
private fun HandHint(
    modifier: Modifier,
    progress: Float,
    gesture: TutorialGesture,
    anchor: Offset
) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier) {
        val point = when (gesture) {
            TutorialGesture.TAP -> {
                val center = if (anchor != Offset.Unspecified) anchor else Offset(size.width * 0.5f, size.height * 0.45f)
                center.copy(y = center.y + sin(progress * 2f * PI).toFloat() * 8f)
            }

            TutorialGesture.FORM_DRAG -> Offset(
                x = size.width * (0.32f + 0.34f * progress),
                y = size.height * (0.35f + 0.28f * progress)
            )

            TutorialGesture.MOVE -> {
                val center = if (anchor != Offset.Unspecified) anchor else Offset(size.width * 0.5f, size.height * 0.55f)
                Offset(
                    x = center.x + cos(progress * 2f * PI).toFloat() * 22f,
                    y = center.y + sin(progress * 2f * PI).toFloat() * 14f
                )
            }

            TutorialGesture.DOUBLE_TAP -> {
                val center = if (anchor != Offset.Unspecified) anchor else Offset(size.width * 0.5f, size.height * 0.45f)
                val bump = abs(sin(progress * 4f * PI.toFloat())) * 14f
                Offset(center.x, center.y + bump)
            }
        }

        drawHand(center = point, color = scheme.primary)

        if (gesture == TutorialGesture.FORM_DRAG) {
            val start = Offset(size.width * 0.32f, size.height * 0.35f)
            val end = Offset(size.width * (0.32f + 0.34f * progress), size.height * (0.35f + 0.28f * progress))
            drawLine(
                color = scheme.primary,
                start = start,
                end = end,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            drawArrowHead(tip = end, from = start, color = scheme.primary)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHand(center: Offset, color: Color) {
    val palm = 14f
    drawCircle(color = color, radius = palm, center = center)
    drawCircle(color = color, radius = 6f, center = center + Offset(-11f, -9f))
    drawCircle(color = color, radius = 6f, center = center + Offset(0f, -14f))
    drawCircle(color = color, radius = 6f, center = center + Offset(11f, -9f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(
    tip: Offset,
    from: Offset,
    color: Color
) {
    val headSize = 11f
    val angle = atan2((from.y - tip.y), (from.x - tip.x))
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(
            tip.x + headSize * cos(angle + 0.5f),
            tip.y + headSize * sin(angle + 0.5f)
        )
        lineTo(
            tip.x + headSize * cos(angle - 0.5f),
            tip.y + headSize * sin(angle - 0.5f)
        )
        close()
    }
    drawPath(path = path, color = color)
}
