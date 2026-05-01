package com.colorbounce.baby

import android.os.SystemClock
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntSize

/** Spectrum for the open (unlocked) hue-lock icon — reads as “color” in any theme. */
internal val rainbowLockOpenGradientColors = listOf(
    Color(0xFFFF1744),
    Color(0xFFFF9100),
    Color(0xFFFFEA00),
    Color(0xFF00E676),
    Color(0xFF00B0FF),
    Color(0xFFD500F9),
    Color(0xFFFF1744)
)

@Composable
fun ShapeContextMenuIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    emphasized: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (emphasized) {
                scheme.primaryContainer.copy(alpha = 0.92f)
            } else {
                Color.Transparent
            },
            contentColor = tint
        )
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

@Composable
fun ShapeContextMenuHueLockButton(
    shape: GameShape,
    rulerHueGloballyLocked: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shapeBodyColor = shape.color
    val lockedVisual = if (rulerHueGloballyLocked) {
        !shape.exemptFromGlobalHueLock
    } else {
        shape.freezeHueWhileDragging
    }
    val emphasized = if (rulerHueGloballyLocked) {
        shape.exemptFromGlobalHueLock
    } else {
        shape.freezeHueWhileDragging
    }
    val (openDesc, closedDesc) = if (rulerHueGloballyLocked) {
        "This shape can shift hue while dragging (overrides ruler lock). Tap to follow ruler lock like other shapes." to
            "Hue locked while dragging (same as ruler). Tap to allow only this shape to shift hue when dragged."
    } else {
        "Tap to freeze hue while dragging this shape." to
            "Hue frozen while dragging this shape. Tap to allow hue to shift."
    }
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (emphasized) {
                scheme.primaryContainer.copy(alpha = 0.92f)
            } else {
                Color.Transparent
            },
            contentColor = if (lockedVisual) shapeBodyColor else Color.White
        )
    ) {
        if (lockedVisual) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = closedDesc,
                tint = shapeBodyColor
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.LockOpen,
                contentDescription = openDesc,
                tint = Color.White,
                modifier = Modifier.drawWithCache {
                    val brush = Brush.linearGradient(
                        colors = rainbowLockOpenGradientColors,
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(
                            brush = brush,
                            blendMode = BlendMode.SrcIn
                        )
                    }
                }
            )
        }
    }
}

/** Floating toolbar for a shape — same visuals as the in-game shape menu. */
@Composable
fun ShapeContextMenuBar(
    shape: GameShape,
    menuIconInk: Color,
    menuIconInkDim: Color,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleImmortal: () -> Unit,
    onToggleFreezeHueWhileDragging: () -> Unit,
    rulerHueGloballyLocked: Boolean = false
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShapeContextMenuIconButton(
                onClick = onDelete,
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete shape",
                tint = menuIconInk
            )
            ShapeContextMenuIconButton(
                onClick = onTogglePin,
                imageVector = Icons.Filled.PushPin,
                contentDescription =
                    "Pin this shape. Pinned shapes stay still until you drag them.",
                tint = if (shape.isPinned) menuIconInk else menuIconInkDim,
                emphasized = shape.isPinned
            )
            ShapeContextMenuIconButton(
                onClick = onToggleImmortal,
                imageVector = if (shape.isImmortal) {
                    Icons.Filled.AllInclusive
                } else {
                    Icons.Outlined.Timer
                },
                contentDescription =
                    "Keep this shape from timing out. Tap again to allow timeout.",
                tint = if (shape.isImmortal) menuIconInk else menuIconInkDim,
                emphasized = shape.isImmortal
            )
            ShapeContextMenuHueLockButton(
                shape = shape,
                rulerHueGloballyLocked = rulerHueGloballyLocked,
                onClick = onToggleFreezeHueWhileDragging
            )
        }
    }
}

/** Menu bar bounds in playfield coordinates (matches floating toolbar placement). */
fun contextMenuScreenBounds(
    shape: GameShape,
    menuSize: IntSize,
    estMenuW: Float,
    estMenuH: Float,
    marginPx: Float,
    gapPx: Float,
    screenW: Float,
    screenH: Float
): Rect {
    val menuW = if (menuSize.width > 0) menuSize.width.toFloat() else estMenuW
    val menuH = if (menuSize.height > 0) menuSize.height.toFloat() else estMenuH
    var x = shape.x - menuW / 2f
    var y = shape.y + shape.height / 2f + gapPx
    if (y + menuH > screenH - marginPx) {
        y = shape.y - shape.height / 2f - gapPx - menuH
    }
    x = x.coerceIn(marginPx, screenW - menuW - marginPx)
    y = y.coerceIn(marginPx, screenH - menuH - marginPx)
    return Rect(x, y, x + menuW, y + menuH)
}

/** Pairs a quick down after a short tap on the same shape to open the shape menu (double-tap). */
class DoubleTapState {
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
