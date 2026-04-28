package com.colorbounce.baby

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import kotlin.math.min

/** Exposed for instrumented tests. */
//const val GAME_SURFACE_TAG = "game_surface"

@Composable
fun GamePlayfield(
    shapes: List<GameShape>,
    modifier: Modifier = Modifier
) {
    val GAME_SURFACE_TAG = ""
    Canvas(
        modifier = modifier.testTag(GAME_SURFACE_TAG)
    ) {
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
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                }
            }
        }
    }
}
