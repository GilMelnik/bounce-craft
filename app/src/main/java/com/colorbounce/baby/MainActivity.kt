package com.colorbounce.baby

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

private const val GAME_SURFACE_TAG = "game_surface"
private const val EXIT_BUTTON_TAG = "exit_button"
private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "onCreate called")
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            settingsRepository = SettingsRepository(this)
            enableEdgeToEdge()

            setContent {
                val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(AppSettings())
                ColorBounceTheme(themeMode = settings.themeMode) {
                    val navController = rememberNavController()
                    ColorBounceApp(
                        navController = navController,
                        settings = settings,
                        gameViewModel = gameViewModel,
                        settingsRepository = settingsRepository,
                        onApplyWindowMode = { inGame ->
                            applyWindowMode(inGame = inGame, settings = settings)
                        },
                        onBestEffortDisableNotifications = {
                            bestEffortDisableNotifications(settings.disableNotifications)
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            throw e
        }
    }

    override fun onResume() {
        try {
            super.onResume()
            Log.d(TAG, "onResume called")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume", e)
        }
    }

    override fun onPause() {
        try {
            super.onPause()
            Log.d(TAG, "onPause called")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onPause", e)
        }
    }

    override fun onDestroy() {
        try {
            Log.d(TAG, "onDestroy called")
            super.onDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }

    private fun applyWindowMode(inGame: Boolean, settings: AppSettings) {
        try {
            // Fullscreen/lock implementation:
            // hide system bars in game and use sticky immersive behavior.
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            WindowCompat.setDecorFitsSystemWindows(window, !inGame)
            if (inGame) {
                controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                if (settings.lockApp) {
                    // Gesture exclusion is best-effort and device dependent.
                    window.decorView.post {
                        try {
                            val view = window.decorView
                            ViewCompat.setSystemGestureExclusionRects(
                                view,
                                listOf(android.graphics.Rect(0, 0, view.width, view.height))
                            )
                            Log.d(TAG, "Gesture exclusion set for fullscreen")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error setting gesture exclusion", e)
                        }
                    }
                } else {
                    ViewCompat.setSystemGestureExclusionRects(window.decorView, emptyList())
                }
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                ViewCompat.setSystemGestureExclusionRects(window.decorView, emptyList())
            }

            if (inGame && settings.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Log.d(TAG, "Screen on flag set")
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            Log.d(TAG, "Window mode applied: inGame=$inGame")
        } catch (e: Exception) {
            Log.e(TAG, "Error in applyWindowMode", e)
        }
    }

    private fun bestEffortDisableNotifications(enabled: Boolean) {
        try {
            if (!enabled) return
            val manager = getSystemService(NotificationManager::class.java)
            if (manager != null && manager.isNotificationPolicyAccessGranted) {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                Log.d(TAG, "Notifications disabled")
            } else {
                Log.d(TAG, "Notification policy access not granted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in bestEffortDisableNotifications", e)
        }
    }
}

@Composable
private fun ColorBounceApp(
    navController: NavHostController,
    settings: AppSettings,
    gameViewModel: GameViewModel,
    settingsRepository: SettingsRepository,
    onApplyWindowMode: (Boolean) -> Unit,
    onBestEffortDisableNotifications: () -> Unit
) {
    val currentBackstack by navController.currentBackStackEntryFlow.collectAsState(initial = null)
    val route = currentBackstack?.destination?.route ?: "menu"
    val inGame = route == "game"
    LaunchedEffect(inGame, settings.lockApp, settings.keepScreenOn) {
        onApplyWindowMode(inGame)
        if (inGame) onBestEffortDisableNotifications()
    }

    NavHost(navController = navController, startDestination = "menu") {
        composable("menu") {
            MainMenuScreen(
                onPlay = {
                    Log.d(TAG, "Navigating to game screen")
                    navController.navigate("game")
                },
                onSettings = {
                    Log.d(TAG, "Navigating to settings screen")
                    navController.navigate("settings")
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                settings = settings,
                repository = settingsRepository,
                onBack = {
                    Log.d(TAG, "Navigating back from settings")
                    navController.popBackStack()
                }
            )
        }
        composable("game") {
            GameScreen(
                settings = settings,
                viewModel = gameViewModel,
                onExit = {
                    Log.d(TAG, "Exiting game, navigating back to menu")
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
private fun MainMenuScreen(onPlay: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    // Surface sets LocalContentColor to onBackground for default text (light + dark).
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = systemBarsPadding.calculateTopPadding(),
                        bottom = systemBarsPadding.calculateBottomPadding(),
                        start = 24.dp,
                        end = 24.dp
                    ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    modifier = Modifier.width(220.dp),
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text("Play") }
                Button(
                    modifier = Modifier
                        .width(220.dp)
                        .padding(top = 16.dp),
                    onClick = onSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) { Text("Settings") }
            }

            Text(
                text = "Enjoying the app? Buy me a coffee ☕",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp + systemBarsPadding.calculateBottomPadding())
                    .clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://buymeacoffee.com/gilmelnik".toUri()
                        )
                        context.startActivity(intent)
                    }
            )
        }
    }
}

@Composable
private fun GameScreen(settings: AppSettings, viewModel: GameViewModel, onExit: () -> Unit) {
    val shapes by viewModel.shapes.collectAsStateWithLifecycle(emptyList())
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val exitButtonBg = MaterialTheme.colorScheme.surfaceVariant

    LaunchedEffect(settings.shapeTimeoutSeconds, settings.maxShapes) {
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

    // Use theme background; avoid hardcoded colors so dark/light stay readable.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(GAME_SURFACE_TAG)
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged {
                try {
                    viewModel.setScreenSize(it.width.toFloat(), it.height.toFloat())
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onSizeChanged", e)
                }
            }
            .pointerInput(settings.shapeMode, settings.maxShapes) {
                awaitPointerEventScope {
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                val pointerId = change.id.value
                                when {
                                    change.pressed && !change.previousPressed -> {
                                        // start
                                        viewModel.startInteraction(change.position, settings, pointerId)
                                    }
                                    change.pressed && change.previousPressed -> {
                                        // drag
                                        val dragAmount = change.position - change.previousPosition
                                        viewModel.onDrag(change.position, dragAmount, settings, pointerId)
                                    }
                                    !change.pressed && change.previousPressed -> {
                                        // end
                                        viewModel.endInteraction(settings, pointerId)
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Expected when pointerInput scope is cancelled
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
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
                            useCenter = false, // critical: no filling
                            topLeft = Offset(centerX - radius, centerY - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                    }
                }
            }
        }

        // Small always-visible exit affordance.
        Box(
            modifier = Modifier
                .padding(12.dp)
                .size(34.dp)
                .align(Alignment.TopEnd)
                .zIndex(10f)
                .testTag(EXIT_BUTTON_TAG)
                .background(exitButtonBg, CircleShape)
                .clickable {
                    try {
                        Log.d(TAG, "Exit button clicked")
                        onExit()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onExit", e)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(18.dp)) {
                drawLine(
                    color = onSurfaceVariant,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 3f
                )
                drawLine(
                    color = onSurfaceVariant,
                    start = Offset(size.width, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 3f
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: AppSettings, repository: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = scheme.background,
        contentColor = scheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(
                    top = systemBarsPadding.calculateTopPadding(),
                    bottom = systemBarsPadding.calculateBottomPadding(),
                    start = 16.dp,
                    end = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground
                )
            }

            SettingsSectionLabel("Theme mode")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val selected = settings.themeMode == mode
                    Button(
                        onClick = { scope.launch { repository.updateThemeMode(mode) } },
                        colors = modeToggleColors(selected)
                    ) {
                        Text(
                            mode.name.lowercase().replaceFirstChar { it.titlecase() }
                        )
                    }
                }
            }

            SettingsSectionLabel("Shape selection")
            ShapeType.entries.forEach { shapeType ->
                val checked = settings.selectedShapes.contains(shapeType)
                ToggleRow(shapeType.name.lowercase().replaceFirstChar { it.titlecase() }, checked) { newChecked ->
                    val newSet = if (newChecked) {
                        settings.selectedShapes + shapeType
                    } else {
                        (settings.selectedShapes - shapeType).takeIf { it.isNotEmpty() } ?: settings.selectedShapes
                    }
                    scope.launch { repository.updateSelectedShapes(newSet) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShapeSelectionMode.entries.forEach { mode ->
                    val selected = settings.shapeSelectionMode == mode
                    Button(
                        onClick = { scope.launch { repository.updateShapeSelectionMode(mode) } },
                        colors = modeToggleColors(selected)
                    ) {
                        Text(mode.name.lowercase().replaceFirstChar { it.titlecase() })
                    }
                }
            }

            Text(
                "Shape timeout: ${settings.shapeTimeoutSeconds}s",
                color = scheme.onBackground,
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = settings.shapeTimeoutSeconds.toFloat(),
                onValueChange = { scope.launch { repository.updateTimeoutSeconds(it.toInt()) } },
                valueRange = 3f..60f,
                colors = SliderDefaults.colors(
                    thumbColor = scheme.primary,
                    activeTrackColor = scheme.primary,
                    inactiveTrackColor = scheme.surfaceVariant,
                    activeTickColor = scheme.onPrimary,
                    inactiveTickColor = scheme.onSurfaceVariant
                )
            )

            Text(
                "Max shapes: ${settings.maxShapes}",
                color = scheme.onBackground,
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = settings.maxShapes.toFloat(),
                onValueChange = { scope.launch { repository.updateMaxShapes(it.toInt()) } },
                valueRange = 4f..80f,
                colors = SliderDefaults.colors(
                    thumbColor = scheme.primary,
                    activeTrackColor = scheme.primary,
                    inactiveTrackColor = scheme.surfaceVariant,
                    activeTickColor = scheme.onPrimary,
                    inactiveTickColor = scheme.onSurfaceVariant
                )
            )

            Text(
                "Max velocity: ${settings.maxVelocityPxPerSec}px/s",
                color = scheme.onBackground,
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = settings.maxVelocityPxPerSec.toFloat(),
                onValueChange = { scope.launch { repository.updateMaxVelocity(it.toInt()) } },
                valueRange = 100f..3000f,
                colors = SliderDefaults.colors(
                    thumbColor = scheme.primary,
                    activeTrackColor = scheme.primary,
                    inactiveTrackColor = scheme.surfaceVariant,
                    activeTickColor = scheme.onPrimary,
                    inactiveTickColor = scheme.onSurfaceVariant
                )
            )

            Text(
                "Auto-spawn after: ${if (settings.autoSpawnInactivitySeconds == 0) "Off" else "${settings.autoSpawnInactivitySeconds}s"}",
                color = scheme.onBackground,
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = settings.autoSpawnInactivitySeconds.toFloat(),
                onValueChange = { scope.launch { repository.updateAutoSpawnSeconds(it.toInt()) } },
                valueRange = 0f..30f,
                colors = SliderDefaults.colors(
                    thumbColor = scheme.primary,
                    activeTrackColor = scheme.primary,
                    inactiveTrackColor = scheme.surfaceVariant,
                    activeTickColor = scheme.onPrimary,
                    inactiveTickColor = scheme.onSurfaceVariant
                )
            )

            ToggleRow("Keep screen on", settings.keepScreenOn) {
                scope.launch { repository.updateKeepScreenOn(it) }
            }
            ToggleRow("Lock app (immersive)", settings.lockApp) {
                scope.launch { repository.updateLockApp(it) }
            }
            ToggleRow("Disable notifications (best effort)", settings.disableNotifications) {
                if (it) {
                    val manager = context.getSystemService(NotificationManager::class.java)
                    if (manager != null && !manager.isNotificationPolicyAccessGranted) {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                scope.launch { repository.updateDisableNotifications(it) }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = scheme.onSurfaceVariant
    )
}

@Composable
private fun modeToggleColors(selected: Boolean): ButtonColors {
    val scheme = MaterialTheme.colorScheme
    return ButtonDefaults.buttonColors(
        containerColor = if (selected) scheme.primaryContainer else scheme.surfaceVariant,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = scheme.onBackground,
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
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
