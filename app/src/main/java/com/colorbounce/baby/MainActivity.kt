package com.colorbounce.baby

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

private const val GAME_SURFACE_TAG = "game_surface"
private const val EXIT_BUTTON_TAG = "exit_button"

class MainActivity : ComponentActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    }

    override fun onResume() {
        super.onResume()
    }

    private fun applyWindowMode(inGame: Boolean, settings: AppSettings) {
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
                    val view = window.decorView
                    ViewCompat.setSystemGestureExclusionRects(
                        view,
                        listOf(android.graphics.Rect(0, 0, view.width, view.height))
                    )
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
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun bestEffortDisableNotifications(enabled: Boolean) {
        if (!enabled) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager != null && manager.isNotificationPolicyAccessGranted) {
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
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
                onPlay = { navController.navigate("game") },
                onSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                settings = settings,
                repository = settingsRepository,
                onBack = { navController.popBackStack() }
            )
        }
        composable("game") {
            GameScreen(
                settings = settings,
                viewModel = gameViewModel,
                onExit = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun MainMenuScreen(onPlay: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
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
                    .padding(24.dp),
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
                text = "Enjoying the app? Buy me a coffee \u2615",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
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
    var startPoint by remember { mutableStateOf(Offset.Zero) }
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
            .onSizeChanged { viewModel.setScreenSize(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(settings.shapeMode, settings.maxShapes) {
                detectDragGestures(
                    onDragStart = { start ->
                        startPoint = start
                        viewModel.startInteraction(start, settings)
                    },
                    onDragEnd = { viewModel.endInteraction() },
                    onDragCancel = { viewModel.endInteraction() }
                ) { change, dragAmount ->
                    viewModel.onDrag(
                        point = change.position,
                        dragAmount = dragAmount,
                        startPoint = startPoint,
                        settings = settings
                    )
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
                .clickable { onExit() },
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = scheme.background,
        contentColor = scheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground
            )

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

            SettingsSectionLabel("Shape mode")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShapeMode.entries.forEach { mode ->
                    val selected = settings.shapeMode == mode
                    Button(
                        onClick = { scope.launch { repository.updateShapeMode(mode) } },
                        colors = modeToggleColors(selected)
                    ) {
                        Text(mode.name.lowercase().replace('_', ' '))
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

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                )
            ) { Text("Back") }
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
