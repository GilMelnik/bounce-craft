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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val STEP_COUNT = 3
private const val SUCCESS_ADVANCE_DELAY_MS = 4000L
private const val FORMATION_ADVANCE_DELAY_MS = 4500L

@Composable
fun TutorialScreen(onDismiss: () -> Unit) {
    var currentStep by rememberSaveable { mutableStateOf(0) }

    fun nextStep() {
        if (currentStep < STEP_COUNT - 1) {
            currentStep += 1
        } else {
            onDismiss()
        }
    }

    when (currentStep) {
        0 -> CreateShapeTutorialStep(onAdvance = ::nextStep)
        1 -> SizeAndSpeedTutorialStep(onAdvance = ::nextStep)
        else -> MoveShapeTutorialStep(onFinish = onDismiss)
    }
}

@Composable
private fun CreateShapeTutorialStep(onAdvance: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val settings = remember { tutorialSettings() }
    val viewModel = remember { GameViewModel() }
    val shapes by viewModel.shapes.collectAsState(emptyList())

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
                        if (stageCompleted) return@detectTapGestures
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
    val shapes by viewModel.shapes.collectAsState(emptyList())

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
                                            val dragAmount = change.position - change.previousPosition
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
    }
}

@Composable
private fun MoveShapeTutorialStep(onFinish: () -> Unit) {
    val settings = remember { tutorialSettings(maxVelocity = 1800) }
    val viewModel = remember { GameViewModel() }
    val shapes by viewModel.shapes.collectAsState(emptyList())

    var playgroundSize by remember { mutableStateOf(IntSize.Zero) }
    var initialCenter by remember { mutableStateOf<Offset?>(null) }
    var trackedShapeId by remember { mutableStateOf<Long?>(null) }
    var initialHue by remember { mutableStateOf<Float?>(null) }
    var movedEnough by remember { mutableStateOf(false) }
    var colorShifted by remember { mutableStateOf(false) }
    var stageCompleted by remember { mutableStateOf(false) }

    val latestShapes by rememberUpdatedState(shapes)
    val latestStageCompleted by rememberUpdatedState(stageCompleted)

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
                initialCenter = start
                val pointerId = 3001L
                viewModel.startInteraction(
                    point = start,
                    settings = settings,
                    pointerId = pointerId,
                    constrainInsideScreen = true
                )
                viewModel.endInteraction(settings, pointerId)
            }
        }
    }

    TutorialPhysicsLoop(viewModel = viewModel, settings = settings)

    LaunchedEffect(stageCompleted) {
        if (stageCompleted) {
            delay(SUCCESS_ADVANCE_DELAY_MS)
            onFinish()
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
        onOutsideTap = onFinish
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { playgroundSize = it }
                .pointerInput(playgroundSize, settings) {
                    awaitPointerEventScope {
                        var activePointerId: Long? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                val pointerId = change.id.value + 4_000L
                                when {
                                    change.pressed && !change.previousPressed -> {
                                        if (!latestStageCompleted && activePointerId == null) {
                                            // Only allow interaction if we hit the existing shape
                                            val hit = latestShapes.find { shape ->
                                                val dx = change.position.x - shape.x
                                                val dy = change.position.y - shape.y
                                                hypot(dx, dy) < (shape.width / 2f) * 1.6f
                                            }
                                            if (hit != null) {
                                                viewModel.startInteraction(
                                                    point = change.position,
                                                    settings = settings,
                                                    pointerId = pointerId,
                                                    constrainInsideScreen = true
                                                )
                                                activePointerId = pointerId
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
                    anchor = shapes.firstOrNull()?.let { Offset(it.x, it.y) } ?: Offset.Unspecified
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
    }
}

@Composable
private fun TutorialStepLayout(
    title: String,
    body: String,
    step: Int,
    onOutsideTap: () -> Unit,
    windowContent: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = scheme.background,
        contentColor = scheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            if (isLandscape) {
                Spacer(Modifier.height(56.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onBackground,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(20.dp))
                        TutorialFooter(step = step)
                    }

                    Spacer(Modifier.width(24.dp))

                    TutorialWindow(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .padding(vertical = 18.dp),
                        onOutsideTap = onOutsideTap,
                        content = windowContent
                    )
                }
                Spacer(Modifier.height(20.dp))
            } else {
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
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(40.dp))

                TutorialWindow(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onOutsideTap = onOutsideTap,
                    content = windowContent
                )

                Spacer(Modifier.height(30.dp))
            }

            if (!isLandscape) {
                TutorialFooter(step = step)
                Spacer(Modifier.height(110.dp))
            }
        }
    }
}

@Composable
private fun TutorialWindow(
    modifier: Modifier,
    onOutsideTap: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    BoxWithConstraints(modifier = modifier) {
        val windowAspectRatio = 1.2f
        val outerMargin = 12.dp
        val maxWindowWidth = maxOf(0.dp, maxWidth - outerMargin * 2)
        val maxWindowHeight = maxOf(0.dp, maxHeight - outerMargin * 2)
        val windowWidth = minOf(maxWindowWidth, maxWindowHeight * windowAspectRatio)
        val windowHeight = if (windowAspectRatio == 0f) 0.dp else windowWidth / windowAspectRatio

        // Transparent layer to catch taps outside the mini-window area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { onOutsideTap() }
                }
        )

        // Adaptive mini window that always fits within the available bounds
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
            // No pointerInput here on the container to avoid blocking the content's input
            content()
        }
    }
}

@Composable
private fun TutorialFooter(step: Int) {
    val scheme = MaterialTheme.colorScheme

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Tap outside the window to skip this part",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Canvas(modifier = Modifier.size(width = 56.dp, height = 10.dp)) {
            val radius = 5.dp.toPx()
            val gap = 18.dp.toPx()
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
    shapeTimeoutSeconds = 60,
    maxShapes = 1,
    autoSpawnInactivitySeconds = 0,
    maxVelocityPxPerSec = maxVelocity
)

private fun hueDistance(a: Float, b: Float): Float {
    val diff = abs(a.normalizeHue() - b.normalizeHue())
    return min(diff, 360f - diff)
}

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
        }
    }
}

private enum class TutorialGesture {
    TAP,
    FORM_DRAG,
    MOVE
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
