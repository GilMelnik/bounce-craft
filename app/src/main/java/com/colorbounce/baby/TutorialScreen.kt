package com.colorbounce.baby

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private const val STEP_COUNT = 4

@Composable
fun TutorialScreen(onDismiss: () -> Unit) {
    var currentStep by remember { mutableStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorScheme.background,
        contentColor = colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    if (currentStep < STEP_COUNT - 1) {
                        currentStep++
                    } else {
                        onDismiss()
                    }
                }
        ) {
            when (currentStep) {
                0 -> TutorialStep1()
                1 -> TutorialStep2()
                2 -> TutorialStep3()
                3 -> TutorialStep4()
            }

            TutorialFooter(currentStep)
        }
    }
}

@Composable
private fun TutorialStep1() {
    val infiniteTransition = rememberInfiniteTransition(label = "tutorial_step1")
    val animationProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "swipe_down_animation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to Bounce Craft!",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Box(
            modifier = Modifier
                .padding(vertical = 32.dp)
                .background(
                    colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.large
                )
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedHandGesture(
                modifier = Modifier.fillMaxSize(0.6f),
                animationProgress = animationProgress.value,
                gestureType = GestureType.SWIPE_DOWN
            )
        }

        Text(
            text = "Swipe your finger to create shapes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TutorialStep2() {
    val infiniteTransition = rememberInfiniteTransition(label = "tutorial_step2")
    val animationProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "swipe_away_animation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Make Shapes Bigger",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Box(
            modifier = Modifier
                .padding(vertical = 32.dp)
                .background(
                    colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.large
                )
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedHandGesture(
                modifier = Modifier.fillMaxSize(0.6f),
                animationProgress = animationProgress.value,
                gestureType = GestureType.SWIPE_AWAY
            )
        }

        Text(
            text = "The farther you swipe, the bigger the shape",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TutorialStep3() {
    val infiniteTransition = rememberInfiniteTransition(label = "tutorial_step3")
    val animationProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drag_animation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Move Shapes Around",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Box(
            modifier = Modifier
                .padding(vertical = 32.dp)
                .background(
                    colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.large
                )
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedHandGesture(
                modifier = Modifier.fillMaxSize(0.6f),
                animationProgress = animationProgress.value,
                gestureType = GestureType.DRAG
            )
        }

        Text(
            text = "Tap a shape and drag it around the screen",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TutorialStep4() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Colors Change",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Box(
            modifier = Modifier
                .padding(vertical = 32.dp)
                .background(
                    colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.large
                )
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedColorCircle()
        }

        Text(
            text = "Shapes change color when you interact with them",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TutorialFooter(currentStep: Int) {
    val primaryColor = colorScheme.primary
    val surfaceVariantColor = colorScheme.surfaceVariant
    val onBackgroundColor = colorScheme.onBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Tap to continue",
                style = MaterialTheme.typography.bodySmall,
                color = onBackgroundColor.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            // Step indicator
            Box(
                modifier = Modifier.padding(top = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                repeat(STEP_COUNT) { index ->
                    if (index > 0) {
                        Canvas(modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .background(Color.Transparent)) {
                            drawCircle(
                                color = if (index == currentStep)
                                    primaryColor
                                else
                                    surfaceVariantColor,
                                radius = 6.dp.toPx()
                            )
                        }
                    } else {
                        Canvas(modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .background(Color.Transparent)) {
                            drawCircle(
                                color = if (index == currentStep)
                                    primaryColor
                                else
                                    surfaceVariantColor,
                                radius = 6.dp.toPx()
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class GestureType {
    SWIPE_DOWN,
    SWIPE_AWAY,
    DRAG
}

@Composable
private fun AnimatedHandGesture(
    modifier: Modifier = Modifier,
    animationProgress: Float,
    gestureType: GestureType
) {
    val color = colorScheme.primary

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        when (gestureType) {
            GestureType.SWIPE_DOWN -> {
                // Hand icon at top, with arrow pointing down
                val handY = centerY - size.height * 0.25f
                val handX = centerX

                drawHand(handX, handY, color)

                // Animated arrow showing swipe down
                val arrowStartY = handY + size.height * 0.15f
                val arrowEndY = arrowStartY + size.height * 0.3f
                val currentY = arrowStartY + (arrowEndY - arrowStartY) * animationProgress

                drawLine(
                    color = color,
                    start = Offset(handX, arrowStartY),
                    end = Offset(handX, currentY),
                    strokeWidth = 4f
                )
                drawArrowHead(Offset(handX, currentY), Offset(handX, currentY - 20f), color)
            }

            GestureType.SWIPE_AWAY -> {
                // Hand icon at left, with arrow pointing right
                val handX = centerX - size.width * 0.2f
                val handY = centerY

                drawHand(handX, handY, color)

                // Animated arrow showing swipe away
                val arrowStartX = handX + size.width * 0.15f
                val arrowEndX = arrowStartX + size.width * 0.3f
                val currentX = arrowStartX + (arrowEndX - arrowStartX) * animationProgress

                drawLine(
                    color = color,
                    start = Offset(arrowStartX, handY),
                    end = Offset(currentX, handY),
                    strokeWidth = 4f
                )
                drawArrowHead(Offset(currentX, handY), Offset(currentX - 20f, handY), color)
            }

            GestureType.DRAG -> {
                // Hand with circular path showing drag motion
                val radius = size.width * 0.15f
                val circleX = centerX
                val circleY = centerY

                val angle = animationProgress * 360f * (Math.PI / 180f)
                val handX = circleX + kotlin.math.cos(angle).toFloat() * radius
                val handY = circleY + sin(angle).toFloat() * radius

                drawHand(handX, handY, color)

                // Draw circle path
                drawCircle(
                    color = color.copy(alpha = 0.3f),
                    radius = radius,
                    center = Offset(circleX, circleY),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHand(
    x: Float,
    y: Float,
    color: Color
) {
    // Simple hand icon - circle with lines for fingers
    val handSize = 24f

    // Wrist/palm
    drawCircle(
        color = color,
        radius = handSize,
        center = Offset(x, y)
    )

    // Fingers - 4 small circles around the palm
    val fingerDistance = handSize * 1.3f
    val fingerSize = handSize * 0.4f

    // Thumb
    drawCircle(
        color = color,
        radius = fingerSize,
        center = Offset(x - fingerDistance * 0.7f, y + fingerDistance * 0.3f)
    )

    // Index finger
    drawCircle(
        color = color,
        radius = fingerSize,
        center = Offset(x, y - fingerDistance)
    )

    // Middle finger
    drawCircle(
        color = color,
        radius = fingerSize,
        center = Offset(x + fingerDistance * 0.7f, y - fingerDistance * 0.7f)
    )

    // Ring finger
    drawCircle(
        color = color,
        radius = fingerSize,
        center = Offset(x + fingerDistance * 0.7f, y + fingerDistance * 0.3f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(
    tip: Offset,
    from: Offset,
    color: Color
) {
    val size = 12f
    val angle = kotlin.math.atan2((from.y - tip.y).toDouble(), (from.x - tip.x).toDouble()).toFloat()

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(
            tip.x + (size * kotlin.math.cos(angle + 5.5f)),
            tip.y + (size * sin(angle + 5.5f))
        )
        lineTo(
            tip.x + (size * kotlin.math.cos(angle - 5.5f)),
            tip.y + (size * sin(angle - 5.5f))
        )
        close()
    }

    drawPath(path, color = color)
}

@Composable
private fun AnimatedColorCircle() {
    val infiniteTransition = rememberInfiniteTransition(label = "color_animation")
    val hueOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hue_animation"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val hue = (hueOffset % 360f).coerceIn(0f, 360f)
        val color = Color.hsv(hue, 0.8f, 0.9f)

        drawCircle(
            color = color,
            radius = size.width * 0.2f,
            center = Offset(size.width / 2, size.height / 2)
        )

        // Small rotating indicator
        val rotation = hueOffset * (Math.PI / 180f)
        val indicatorRadius = size.width * 0.25f
        val indicatorX = (size.width / 2 + indicatorRadius * kotlin.math.cos(rotation)).toFloat()
        val indicatorY = (size.height / 2 + indicatorRadius * sin(rotation)).toFloat()

        drawCircle(
            color = color,
            radius = 8f,
            center = Offset(indicatorX, indicatorY)
        )
    }
}
