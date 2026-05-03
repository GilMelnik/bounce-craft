package com.colorbounce.baby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Circular toggle chip used for shape pool selection in settings and creation ruler strips.
 */
@Composable
fun ShapeSelectionIconChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable (tint: Color) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (selected) {
        scheme.onPrimaryContainer
    } else {
        scheme.onSurfaceVariant.copy(alpha = 0.92f)
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            scheme.primaryContainer
        } else {
            scheme.surfaceContainerHighest.copy(alpha = 0.72f)
        },
        border = if (selected) {
            BorderStroke(1.dp, scheme.primary.copy(alpha = 0.35f))
        } else {
            BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f))
        },
        modifier = modifier
            .size(40.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content(tint)
        }
    }
}

@Composable
fun ShapeOutlineGlyph(
    type: ShapeType,
    tint: Color,
    modifier: Modifier = Modifier,
    glyphDp: Dp = 20.dp
) {
    Canvas(modifier.size(glyphDp)) {
        val s = 2.2f
        val pad = 2.2f
        val dim = min(size.width, size.height)
        when (type) {
            ShapeType.CIRCLE -> {
                val r2 = dim * 0.4f
                drawCircle(
                    color = tint,
                    radius = r2,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(s)
                )
            }
            ShapeType.RECTANGLE -> {
                val w = size.width - 2 * pad
                val h = size.height - 2 * pad
                drawRect(
                    color = tint,
                    topLeft = Offset(pad, pad),
                    size = Size(w, h),
                    style = Stroke(s)
                )
            }
            ShapeType.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(size.width / 2f, pad * 1.2f)
                    lineTo(pad, size.height - pad)
                    lineTo(size.width - pad, size.height - pad)
                    close()
                }
                drawPath(path, color = tint, style = Stroke(s))
            }
            ShapeType.ARCH -> {
                val rad = dim * 0.42f
                val cy = size.height * 0.6f
                val cx = size.width / 2f
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - rad, cy - rad),
                    size = Size(rad * 2f, rad * 2f),
                    style = Stroke(s)
                )
            }
            ShapeType.STAR, ShapeType.HEART, ShapeType.DIAMOND -> {
                val fake = GameShape(
                    id = 0L,
                    type = type,
                    x = size.width / 2f,
                    y = size.height / 2f,
                    width = size.width - pad * 2f,
                    height = size.height - pad * 2f,
                    vx = 0f,
                    vy = 0f,
                    hue = 0f,
                    saturation = 0f,
                    value = 1f,
                    lastInteractionMillis = 0L
                )
                val vx = FloatArray(48)
                val vy = FloatArray(48)
                val n = fillPolygonVertices(fake, vx, vy)
                if (n >= 3) {
                    val path = Path().apply {
                        moveTo(vx[0], vy[0])
                        for (i in 1 until n) lineTo(vx[i], vy[i])
                        close()
                    }
                    drawPath(path, color = tint, style = Stroke(s))
                }
            }
        }
    }
}
