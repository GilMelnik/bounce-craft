package com.colorbounce.baby

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.isActive
import androidx.compose.runtime.saveable.rememberSaveable

private const val TAG = "CreationMode"
private const val EXIT_BUTTON_TAG = "exit_button"

@Composable
fun CreationModeScreen(
    settings: AppSettings,
    viewModel: GameViewModel,
    onExit: () -> Unit
) {
    var session by remember { mutableStateOf(CreationSession()) }
    var rulerExpanded by rememberSaveable { mutableStateOf(true) }
    var contextMenuShapeId by remember { mutableStateOf<Long?>(null) }
    var showAtCapacity by remember { mutableStateOf(false) }

    val shapes by viewModel.shapes.collectAsStateWithLifecycle(emptyList())
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val exitButtonBg = MaterialTheme.colorScheme.surfaceVariant
    val slopPx = with(androidx.compose.ui.platform.LocalDensity.current) { 20.dp.toPx() }
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
                        try {
                            while (true) {
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
                            }
                        } catch (_: Exception) {
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

        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            val maxH = (maxHeight * 0.45f).coerceIn(220.dp, 480.dp)
            if (rulerExpanded) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxH)
                        .navigationBarsPadding()
                        .zIndex(4f),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 2.dp
                ) {
                    CreationModeRuler(
                        session = session,
                        onSessionChange = { session = it },
                        expanded = true,
                        onToggleExpanded = { rulerExpanded = false },
                        maxHeight = maxH
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .zIndex(4f),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    CreationModeRuler(
                        session = session,
                        onSessionChange = { session = it },
                        expanded = false,
                        onToggleExpanded = { rulerExpanded = true },
                        maxHeight = 44.dp
                    )
                }
            }
        }

        contextMenuShapeId?.let { id ->
            val shape = shapes.find { it.id == id }
            if (shape == null) {
                contextMenuShapeId = null
            } else {
                Dialog(onDismissRequest = { contextMenuShapeId = null }) {
                    Surface(shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Shape actions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("Pinned: ${shape.isPinned}  ·  Immortal: ${shape.isImmortal}")
                            TextButton(
                                onClick = {
                                    viewModel.removeShape(id)
                                    contextMenuShapeId = null
                                }
                            ) { Text("Delete") }
                            TextButton(
                                onClick = {
                                    viewModel.setShapePinned(id, !shape.isPinned)
                                    contextMenuShapeId = null
                                }
                            ) {
                                Text(if (shape.isPinned) "Unpin" else "Pin (constant)")
                            }
                            TextButton(
                                onClick = {
                                    viewModel.setShapeImmortal(id, !shape.isImmortal)
                                    contextMenuShapeId = null
                                }
                            ) {
                                Text(if (shape.isImmortal) "Mortal" else "Immortal")
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
