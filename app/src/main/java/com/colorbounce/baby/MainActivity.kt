package com.colorbounce.baby

import android.app.NotificationManager
import android.content.Intent
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val EXIT_BUTTON_TAG = "exit_button"
private const val TUTORIAL_BUTTON_TAG = "tutorial_button"
private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "onCreate called")
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
    var tutorialExitTarget by rememberSaveable { mutableStateOf("game") }
    var previousInGame by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(inGame) {
        val previous = previousInGame
        when {
            previous == null && inGame -> gameViewModel.onGameEnter()
            previous == true && !inGame -> gameViewModel.onGameExit()
            previous == false && inGame -> gameViewModel.onGameEnter()
        }
        previousInGame = inGame
    }

    LaunchedEffect(inGame, settings.lockApp, settings.keepScreenOn) {
        onApplyWindowMode(inGame)
        if (inGame) onBestEffortDisableNotifications()
    }

    NavHost(navController = navController, startDestination = "menu") {
        composable("menu") {
            MainMenuScreen(
                onPlay = {
                    Log.d(TAG, "Play clicked")
                    if (settings.tutorialSeen) {
                        navController.navigate("game")
                    } else {
                        tutorialExitTarget = "game"
                        navController.navigate("tutorial")
                    }
                },
                onSettings = {
                    Log.d(TAG, "Navigating to settings screen")
                    navController.navigate("settings")
                },
                onAbout = {
                    Log.d(TAG, "Navigating to about screen")
                    navController.navigate("about")
                },
                onTutorial = {
                    Log.d(TAG, "Tutorial replay requested")
                    tutorialExitTarget = "menu"
                    navController.navigate("tutorial")
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
        composable("about") {
            AboutScreen(
                onBack = {
                    Log.d(TAG, "Navigating back from about")
                    navController.popBackStack()
                }
            )
        }
        composable("tutorial") {
            val scope = rememberCoroutineScope()
            TutorialScreen(
                onDismiss = {
                    Log.d(TAG, "Tutorial dismissed")
                    scope.launch {
                        settingsRepository.markTutorialSeen()
                    }
                    if (tutorialExitTarget == "game") {
                        navController.navigate("game") {
                            popUpTo("tutorial") { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }
        composable("game") {
            GameScreen(
                settings = settings,
                repository = settingsRepository,
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
private fun MainMenuScreen(
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onTutorial: () -> Unit
) {
    val context = LocalContext.current
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    // Surface sets LocalContentColor to onBackground for default text (light + dark).
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = systemBarsPadding.calculateTopPadding() + 12.dp,
                        end = 12.dp
                    )
                    .size(36.dp)
                    .testTag(TUTORIAL_BUTTON_TAG)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        CircleShape
                    )
                    .clickable(onClick = onTutorial),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = systemBarsPadding.calculateTopPadding(),
                        bottom = systemBarsPadding.calculateBottomPadding(),
                        start = 24.dp,
                        end = 24.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(0.25f))
                Text(
                    text = "Bounce Craft",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 72.sp,
                        lineHeight = 80.sp,
                        letterSpacing = 2.sp
                    ),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(0.25f))
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
                        .padding(top = 12.dp),
                    onClick = onSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) { Text("Settings") }
                OutlinedButton(
                    modifier = Modifier
                        .width(220.dp)
                        .padding(top = 12.dp),
                    onClick = onAbout,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("About") }
                Spacer(Modifier.weight(0.5f))
                Text(
                    text = "Enjoying the app? Buy me a coffee ☕",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier
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
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val onSurfaceVariant = scheme.onSurfaceVariant
    val scroll = rememberScrollState()
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
    val appVersion = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        } catch (e: Exception) {
            "—"
        }
    }
    fun openUrl(url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

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
                Text(
                    text = "About",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            scheme.surfaceVariant.copy(alpha = 0.8f),
                            CircleShape
                        )
                        .clickable(onClick = onBack),
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
            }

            val bodyStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onBackground)
            Text(
                "I built this app after becoming a new father. When my daughter was around one year old, " +
                    "she always wanted to play with my phone or my partner's whenever she saw them.",
                style = bodyStyle
            )
            Text(
                "I don't think smartphones are ideal for babies this young, but if a baby is going to play with a phone, " +
                    "I wanted it to be in a way that doesn't lead to accidental calls, deleted notifications, " +
                    "or random messages typed in gibberish.",
                style = bodyStyle
            )
            Text(
                "That's why I made this app: a simple, safe, distraction-free place for little hands to explore shapes and colors.",
                style = bodyStyle
            )
            Text(
                "This is a hobby project—I'm not a professional app developer.",
                style = bodyStyle
            )
            Text(
                "The app is open source, and the code is available on GitHub.",
                style = bodyStyle
            )
            Text(
                "If you enjoy the app, please consider buying me a coffee to help cover the costs of maintaining it.",
                style = bodyStyle
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { openUrl("https://buymeacoffee.com/gilmelnik") },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_coffee),
                        contentDescription = "Buy me a coffee",
                        modifier = Modifier.size(22.dp),
                        tint = scheme.primary
                    )
                }
                IconButton(
                    onClick = { openUrl("https://github.com/GilMelnik/bounce-craft") },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_github),
                        contentDescription = "Open GitHub repository",
                        modifier = Modifier.size(22.dp),
                        tint = scheme.primary
                    )
                }
            }
            Text(
                "Thank you.",
                style = bodyStyle,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Version $appVersion",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun GameScreen(
    settings: AppSettings,
    repository: SettingsRepository,
    viewModel: GameViewModel,
    onExit: () -> Unit
) {
    val shapes by viewModel.shapes.collectAsStateWithLifecycle(emptyList())
    var contextMenuShapeId by remember { mutableStateOf<Long?>(null) }
    var showAtCapacity by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var playRulerSession by remember { mutableStateOf(CreationSession.fromSettings(settings)) }
    var playRulerExpanded by rememberSaveable { mutableStateOf(true) }
    val contextMenuIdRef = rememberUpdatedState(contextMenuShapeId)
    val doubleTap = remember { DoubleTapState() }
    val slopPx = with(LocalDensity.current) { 20.dp.toPx() }
    val dismissShapeMenu by rememberUpdatedState(newValue = {
        contextMenuIdRef.value?.let { viewModel.clearPointersTargetingShape(it) }
        contextMenuShapeId = null
    })

    DisposableEffect(Unit) {
        onDispose { viewModel.setShapeContextMenuOpen(false) }
    }

    SideEffect {
        viewModel.setShapeContextMenuOpen(contextMenuShapeId != null)
    }

    LaunchedEffect(settings.showPlayGameRuler) {
        if (settings.showPlayGameRuler) {
            playRulerSession = CreationSession.fromSettings(settings)
        }
    }

    LaunchedEffect(
        settings.selectedShapes,
        settings.shapeSelectionMode,
        settings.shapeTimeoutImmortal,
        settings.showPlayGameRuler
    ) {
        if (!settings.showPlayGameRuler) return@LaunchedEffect
        playRulerSession = playRulerSession.copy(
            selectedShapes = settings.selectedShapes,
            shapeSelectionMode = settings.shapeSelectionMode,
            newShapesImmortal = settings.shapeTimeoutImmortal
        )
    }

    LaunchedEffect(settings.showPlayGameRuler) {
        if (!settings.showPlayGameRuler) return@LaunchedEffect
        viewModel.creationAtCapacity.collect {
            showAtCapacity = true
        }
    }

    val playGestureCreationRef by rememberUpdatedState(
        if (settings.showPlayGameRuler) playRulerSession else null
    )
    val playPhysicsCreationRef by rememberUpdatedState(
        if (!settings.showPlayGameRuler) {
            null
        } else {
            playRulerSession.copy(
                physicsPaused = playRulerSession.physicsPaused || (contextMenuShapeId != null)
            )
        }
    )

    val globalShapeTimeoutImmortal =
        if (settings.showPlayGameRuler) playRulerSession.newShapesImmortal
        else settings.shapeTimeoutImmortal

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val exitButtonBg = MaterialTheme.colorScheme.surfaceVariant

    LaunchedEffect(
        settings.shapeTimeoutSeconds,
        settings.shapeTimeoutImmortal,
        settings.maxShapes,
        settings.showPlayGameRuler
    ) {
        var last = 0L
        while (isActive) {
            withFrameNanos { frame ->
                if (last == 0L) {
                    last = frame
                    return@withFrameNanos
                }
                val delta = (frame - last) / 1_000_000_000f
                last = frame
                viewModel.updatePhysics(delta, settings, playPhysicsCreationRef)
            }
        }
    }

    val pointerKey = listOf(
        contextMenuShapeId,
        settings.shapeMode,
        settings.maxShapes,
        settings.selectedShapes,
        settings.shapeSelectionMode,
        settings.shapeTimeoutImmortal,
        settings.showPlayGameRuler,
        playRulerSession
    ).toString()

    // Use theme background; avoid hardcoded colors so dark/light stay readable.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged {
                try {
                    viewModel.setScreenSize(it.width.toFloat(), it.height.toFloat())
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onSizeChanged", e)
                }
            }
    ) {
        GamePlayfield(
            shapes = shapes,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (contextMenuShapeId == null) {
                        Modifier.pointerInput(pointerKey) {
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
                                                        creation = playGestureCreationRef
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
                                                        creation = playGestureCreationRef
                                                    )
                                                }

                                                !change.pressed && change.previousPressed -> {
                                                    val start = downPos.remove(pid) ?: run {
                                                        viewModel.endInteraction(
                                                            settings,
                                                            pid,
                                                            creation = playGestureCreationRef
                                                        )
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
                                                    viewModel.endInteraction(
                                                        settings,
                                                        pid,
                                                        creation = playGestureCreationRef
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        )

        contextMenuShapeId?.let { id ->
            val shape = shapes.find { it.id == id }
            if (shape == null) {
                viewModel.clearPointersTargetingShape(id)
                contextMenuShapeId = null
            } else {
                val scheme = MaterialTheme.colorScheme
                val menuSurfaceLum = scheme.surfaceContainerHigh.luminance()
                val menuIconInk = if (menuSurfaceLum < 0.5f) Color.White else Color.Black
                val menuIconInkDim = menuIconInk.copy(alpha = 0.45f)
                val latestShapeForMenu = rememberUpdatedState(shape)
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
                            .pointerInput(id, menuSize, settings.maxShapes, settings.showPlayGameRuler, playRulerSession) {
                                awaitPointerEventScope {
                                    val overlayDownPos = mutableMapOf<Long, Offset>()
                                    val draggingSelectedShape = mutableMapOf<Long, Boolean>()
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            for (change in event.changes) {
                                                val pid = change.id.value
                                                when {
                                                    change.pressed && !change.previousPressed -> {
                                                        val p = change.position
                                                        val menuRect = contextMenuScreenBounds(
                                                            latestShapeForMenu.value,
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
                                                                creation = playGestureCreationRef
                                                            )
                                                            overlayDownPos[pid] = p
                                                            draggingSelectedShape[pid] = true
                                                        } else {
                                                            overlayDownPos[pid] = p
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
                                                                creation = playGestureCreationRef
                                                            )
                                                        }
                                                    }

                                                    !change.pressed && change.previousPressed -> {
                                                        val wasDraggingSelected =
                                                            draggingSelectedShape.remove(pid)
                                                        val start = overlayDownPos.remove(pid)
                                                        when {
                                                            wasDraggingSelected == true -> {
                                                                viewModel.endInteraction(
                                                                    settings,
                                                                    pid,
                                                                    creation = playGestureCreationRef,
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
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
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
                                        if (settings.showPlayGameRuler && playRulerSession.newShapesPinned) {
                                            viewModel.setShapeExemptFromGlobalPin(
                                                id,
                                                !shape.exemptFromGlobalPin
                                            )
                                        } else {
                                            viewModel.setShapePinned(id, !shape.isPinned)
                                        }
                                    },
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = if (settings.showPlayGameRuler && playRulerSession.newShapesPinned) {
                                        "Ruler pins all shapes. Tap to unpin only this shape, or tap again to follow the ruler."
                                    } else {
                                        "Pin this shape. Turn on ruler pin to pin every shape at once."
                                    },
                                    tint = when {
                                        settings.showPlayGameRuler && playRulerSession.newShapesPinned &&
                                            !shape.exemptFromGlobalPin -> menuIconInk
                                        settings.showPlayGameRuler && playRulerSession.newShapesPinned &&
                                            shape.exemptFromGlobalPin -> menuIconInkDim
                                        shape.isPinned -> menuIconInk
                                        else -> menuIconInkDim
                                    },
                                    emphasized =
                                        (settings.showPlayGameRuler && playRulerSession.newShapesPinned && shape.exemptFromGlobalPin) ||
                                            (!settings.showPlayGameRuler || !playRulerSession.newShapesPinned) && shape.isPinned
                                )
                                ShapeContextMenuIconButton(
                                    onClick = {
                                        if (globalShapeTimeoutImmortal) {
                                            viewModel.setShapeExemptFromGlobalImmortal(
                                                id,
                                                !shape.exemptFromGlobalImmortal
                                            )
                                        } else {
                                            viewModel.setShapeImmortal(id, !shape.isImmortal)
                                        }
                                    },
                                    imageVector = if (globalShapeTimeoutImmortal) {
                                        if (!shape.exemptFromGlobalImmortal) {
                                            Icons.Filled.AllInclusive
                                        } else {
                                            Icons.Outlined.Timer
                                        }
                                    } else if (shape.isImmortal) {
                                        Icons.Filled.AllInclusive
                                    } else {
                                        Icons.Outlined.Timer
                                    },
                                    contentDescription = when {
                                        globalShapeTimeoutImmortal && settings.showPlayGameRuler ->
                                            "Ruler keeps all shapes forever. Tap so only this shape can time out, or again to match the ruler."
                                        globalShapeTimeoutImmortal ->
                                            "Immortal timeout is on. Tap so only this shape can time out, or again to match."
                                        else ->
                                            "Keep this shape from timing out. Turn on ruler infinity or Immortal in settings to apply to every shape."
                                    },
                                    tint = when {
                                        globalShapeTimeoutImmortal && !shape.exemptFromGlobalImmortal -> menuIconInk
                                        globalShapeTimeoutImmortal && shape.exemptFromGlobalImmortal -> menuIconInkDim
                                        shape.isImmortal -> menuIconInk
                                        else -> menuIconInkDim
                                    },
                                    emphasized =
                                        (globalShapeTimeoutImmortal && shape.exemptFromGlobalImmortal) ||
                                            (!globalShapeTimeoutImmortal && shape.isImmortal)
                                )
                                ShapeContextMenuHueLockButton(
                                    shape = shape,
                                    rulerHueGloballyLocked = settings.showPlayGameRuler &&
                                        playRulerSession.disableHueWhileDragging,
                                    onClick = {
                                        if (settings.showPlayGameRuler && playRulerSession.disableHueWhileDragging) {
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

        if (settings.showPlayGameRuler) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .zIndex(8f)
            ) {
                val scheme = MaterialTheme.colorScheme
                val maxH = (maxHeight * 0.38f).coerceIn(200.dp, 400.dp)
                if (playRulerExpanded) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .wrapContentWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .heightIn(max = maxH),
                        shape = RoundedCornerShape(22.dp),
                        color = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                        shadowElevation = 4.dp,
                        tonalElevation = 2.dp
                    ) {
                        CreationModeRuler(
                            session = playRulerSession,
                            onSessionChange = { newSession ->
                                if (newSession.newShapesPinned != playRulerSession.newShapesPinned) {
                                    viewModel.applyGlobalPinFromRuler(newSession.newShapesPinned)
                                }
                                if (newSession.newShapesImmortal != playRulerSession.newShapesImmortal) {
                                    viewModel.applyGlobalImmortalFromRuler(newSession.newShapesImmortal)
                                    scope.launch {
                                        repository.updateShapeTimeoutImmortal(newSession.newShapesImmortal)
                                    }
                                }
                                if (newSession.selectedShapes != playRulerSession.selectedShapes) {
                                    scope.launch { repository.updateSelectedShapes(newSession.selectedShapes) }
                                }
                                if (newSession.shapeSelectionMode != playRulerSession.shapeSelectionMode) {
                                    scope.launch {
                                        repository.updateShapeSelectionMode(newSession.shapeSelectionMode)
                                    }
                                }
                                playRulerSession = newSession
                            },
                            onCollapse = { playRulerExpanded = false },
                            isSideBar = false,
                            maxHeight = maxH
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                            .size(56.dp),
                        shape = CircleShape,
                        color = scheme.secondaryContainer,
                        shadowElevation = 4.dp,
                        tonalElevation = 2.dp
                    ) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = { playRulerExpanded = true }) {
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
        }

        if (showAtCapacity) {
            AlertDialog(
                onDismissRequest = { showAtCapacity = false },
                title = { Text("Limit reached") },
                text = {
                    Text(
                        "Maximum of $CREATION_MAX_SHAPES shapes. Remove a shape to add more."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showAtCapacity = false }) { Text("OK") }
                }
            )
        }

        // Small always-visible exit affordance.
        Box(
            modifier = Modifier
                .padding(top = 24.dp, end = 16.dp)
                .size(34.dp)
                .align(Alignment.TopEnd)
                .zIndex(10f)
                .testTag(EXIT_BUTTON_TAG)
                .background(exitButtonBg.copy(alpha = 0.8f), CircleShape)
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
    }
}

@Composable
private fun SettingsScreen(settings: AppSettings, repository: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val onSurfaceVariant = scheme.onSurfaceVariant
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
                Text(
                    text = "Settings",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            scheme.surfaceVariant.copy(alpha = 0.8f),
                            CircleShape
                        )
                        .clickable(onClick = onBack),
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

            SettingsSectionLabel("Shapes")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShapeSelectionIconChip(
                    selected = true,
                    onClick = {
                        val next = when (settings.shapeSelectionMode) {
                            ShapeSelectionMode.ALTERNATE -> ShapeSelectionMode.RANDOM
                            ShapeSelectionMode.RANDOM -> ShapeSelectionMode.ALTERNATE
                        }
                        scope.launch { repository.updateShapeSelectionMode(next) }
                    },
                    contentDescription = when (settings.shapeSelectionMode) {
                        ShapeSelectionMode.ALTERNATE ->
                            "Shapes alternate in order. Tap to switch to random."
                        ShapeSelectionMode.RANDOM ->
                            "Shapes spawn randomly. Tap to switch to alternating."
                    }
                ) { tint ->
                    val icon = when (settings.shapeSelectionMode) {
                        ShapeSelectionMode.ALTERNATE -> Icons.Filled.Repeat
                        ShapeSelectionMode.RANDOM -> Icons.Filled.Shuffle
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = tint
                    )
                }
                for (shapeType in ShapeType.entries) {
                    val included = settings.selectedShapes.contains(shapeType)
                    ShapeSelectionIconChip(
                        selected = included,
                        onClick = {
                            val newSet = if (included) {
                                (settings.selectedShapes - shapeType).takeIf { it.isNotEmpty() }
                                    ?: settings.selectedShapes
                            } else {
                                settings.selectedShapes + shapeType
                            }
                            scope.launch { repository.updateSelectedShapes(newSet) }
                        },
                        contentDescription = shapePoolChipDescription(shapeType, included)
                    ) { tint ->
                        ShapeOutlineGlyph(shapeType, tint = tint)
                    }
                }
            }

            Text(
                text = if (settings.shapeTimeoutImmortal) {
                    "Shape timeout: Immortal"
                } else {
                    "Shape timeout: ${settings.shapeTimeoutSeconds}s"
                },
                color = scheme.onBackground,
                style = MaterialTheme.typography.bodyLarge
            )
            if (!settings.shapeTimeoutImmortal) {
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
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        repository.updateShapeTimeoutImmortal(!settings.shapeTimeoutImmortal)
                    }
                },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(if (settings.shapeTimeoutImmortal) "Use timed timeout" else "Immortal")
            }

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

            ToggleRow("Show ruler on play screen", settings.showPlayGameRuler) {
                scope.launch { repository.updateShowPlayGameRuler(it) }
            }
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
