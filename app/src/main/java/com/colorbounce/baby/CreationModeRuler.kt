package com.colorbounce.baby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun CreationModeRuler(
    session: CreationSession,
    onSessionChange: (CreationSession) -> Unit,
    onCollapse: () -> Unit,
    isSideBar: Boolean,
    maxHeight: Dp
) {
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onSurface
    val columnModifier = if (isSideBar) {
        Modifier
            .widthIn(240.dp, 272.dp)
            .heightIn(max = maxHeight)
    } else {
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
    }
    Column(
        modifier = columnModifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RulerLineIcon(
                majorColor = ink.copy(alpha = 0.9f),
                minorColor = scheme.outlineVariant.copy(alpha = 0.85f),
                size = DpSize(32.dp, 18.dp)
            )
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = scheme.onSurfaceVariant.copy(alpha = 0.9f)
                )
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Hide ruler"
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = scheme.outlineVariant.copy(alpha = 0.35f)
        )
        RulerToolStrip {
            RulerIconChip(
                selected = session.spawnType == null,
                onClick = {
                    if (session.spawnType == null) {
                        val next = when (session.defaultShapeSelectionMode) {
                            ShapeSelectionMode.ALTERNATE -> ShapeSelectionMode.RANDOM
                            ShapeSelectionMode.RANDOM -> ShapeSelectionMode.ALTERNATE
                        }
                        onSessionChange(session.copy(defaultShapeSelectionMode = next))
                    } else {
                        onSessionChange(session.copy(spawnType = null))
                    }
                },
                contentDescription = when (session.defaultShapeSelectionMode) {
                    ShapeSelectionMode.ALTERNATE ->
                        "Default shapes: alternate. Tap to switch to random."
                    ShapeSelectionMode.RANDOM ->
                        "Default shapes: random. Tap to switch to alternate."
                }
            ) { tint ->
                val icon = when (session.defaultShapeSelectionMode) {
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
            for (t in listOf(
                ShapeType.CIRCLE,
                ShapeType.RECTANGLE,
                ShapeType.TRIANGLE,
                ShapeType.ARCH
            )) {
                RulerIconChip(
                    selected = session.spawnType == t,
                    onClick = { onSessionChange(session.copy(spawnType = t)) },
                    contentDescription = shapeSpawnChipDescription(t)
                ) {
                    RulerShapeTypeGlyph(t, isDefault = false, tint = it)
                }
            }
        }
        RulerToolStrip {
            RulerIconChip(
                selected = session.spawnColor == null,
                onClick = { onSessionChange(session.copy(spawnColor = null)) },
                contentDescription = "Spawn color follows palette"
            ) {
                Icon(
                    Icons.Outlined.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = it
                )
            }
            for (preset in CreationColorPresets) {
                val c = session.spawnColor
                val same = c != null &&
                    kotlin.math.abs(c.first - preset.hue) < 0.1f &&
                    kotlin.math.abs(c.second - preset.s) < 0.02f &&
                    kotlin.math.abs(c.third - preset.v) < 0.02f
                val fill = Color.hsv(
                    preset.hue.normalizeHue(),
                    preset.s.coerceIn(0f, 1f),
                    preset.v.coerceIn(0f, 1f)
                )
                RulerIconChip(
                    selected = same,
                    onClick = {
                        onSessionChange(
                            session.copy(
                                spawnColor = preset.toTriple(),
                                disableHueWhileDragging = true
                            )
                        )
                    },
                    contentDescription = "Color preset"
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .border(
                                1.dp,
                                scheme.outlineVariant.copy(alpha = 0.55f),
                                CircleShape
                            )
                    )
                }
            }
        }
        RulerSimulationAndRulesCluster(
            session = session,
            onSessionChange = onSessionChange,
            compactGrid = isSideBar
        )
    }
}

@Composable
private fun RulerToolStrip(content: @Composable RowScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.32f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun RulerToolStripPanel(content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.32f)),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun RulerSimulationAndRulesCluster(
    session: CreationSession,
    onSessionChange: (CreationSession) -> Unit,
    compactGrid: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val physicsPaused = session.physicsPaused
    val physicsColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = if (physicsPaused) {
            scheme.primaryContainer
        } else {
            scheme.surfaceContainerHighest.copy(alpha = 0.88f)
        },
        contentColor = if (physicsPaused) {
            scheme.onPrimaryContainer
        } else {
            scheme.onSurfaceVariant.copy(alpha = 0.9f)
        }
    )
    val idleSurface = scheme.surfaceContainerHighest.copy(alpha = 0.78f)
    val lockColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = if (session.disableHueWhileDragging) {
            scheme.primaryContainer
        } else {
            idleSurface
        },
        contentColor = if (session.disableHueWhileDragging) {
            scheme.onPrimaryContainer
        } else {
            scheme.onSurfaceVariant
        }
    )
    val pinColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = if (session.newShapesPinned) {
            scheme.primaryContainer
        } else {
            idleSurface
        },
        contentColor = if (session.newShapesPinned) {
            scheme.onPrimaryContainer
        } else {
            scheme.onSurfaceVariant
        }
    )
    val immortalColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = if (session.newShapesImmortal) {
            scheme.primaryContainer
        } else {
            idleSurface
        },
        contentColor = if (session.newShapesImmortal) {
            scheme.onPrimaryContainer
        } else {
            scheme.onSurfaceVariant
        }
    )
    @Composable
    fun PhysicsButton() {
        FilledTonalIconButton(
            onClick = { onSessionChange(session.copy(physicsPaused = !session.physicsPaused)) },
            modifier = Modifier.size(44.dp),
            colors = physicsColors
        ) {
            if (session.physicsPaused) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play — resume physics",
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    Icons.Filled.Pause,
                    contentDescription = "Pause physics",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
    @Composable
    fun HueLockButton() {
        FilledTonalIconButton(
            onClick = {
                val locked = session.disableHueWhileDragging
                onSessionChange(
                    session.copy(
                        disableHueWhileDragging = !locked,
                        spawnColor = if (locked) null else session.spawnColor
                    )
                )
            },
            modifier = Modifier.size(44.dp),
            colors = lockColors
        ) {
            Icon(
                imageVector = if (session.disableHueWhileDragging) {
                    Icons.Filled.Lock
                } else {
                    Icons.Outlined.LockOpen
                },
                contentDescription = if (session.disableHueWhileDragging) {
                    "Hue frozen while moving. Tap to allow shifting when dragging."
                } else {
                    "Tap to freeze hue while moving shapes."
                },
                modifier = Modifier.size(22.dp)
            )
        }
    }
    @Composable
    fun PinButton() {
        FilledTonalIconButton(
            onClick = { onSessionChange(session.copy(newShapesPinned = !session.newShapesPinned)) },
            modifier = Modifier.size(44.dp),
            colors = pinColors
        ) {
            Icon(
                imageVector = if (session.newShapesPinned) {
                    Icons.Filled.PushPin
                } else {
                    Icons.Outlined.PushPin
                },
                contentDescription = if (session.newShapesPinned) {
                    "Pin all shapes on. Tap to turn off."
                } else {
                    "Tap to pin all shapes in place."
                },
                modifier = Modifier.size(22.dp)
            )
        }
    }
    @Composable
    fun LifetimeButton() {
        FilledTonalIconButton(
            onClick = { onSessionChange(session.copy(newShapesImmortal = !session.newShapesImmortal)) },
            modifier = Modifier.size(44.dp),
            colors = immortalColors
        ) {
            Icon(
                imageVector = if (session.newShapesImmortal) {
                    Icons.Filled.AllInclusive
                } else {
                    Icons.Outlined.Timer
                },
                contentDescription = if (session.newShapesImmortal) {
                    "No timeout — all shapes stay. Tap for normal lifetime."
                } else {
                    "Tap so no shape times out."
                },
                modifier = Modifier.size(22.dp)
            )
        }
    }
    if (compactGrid) {
        RulerToolStripPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhysicsButton()
                HueLockButton()
                PinButton()
                LifetimeButton()
            }
        }
    } else {
        RulerToolStrip {
            PhysicsButton()
            HueLockButton()
            PinButton()
            LifetimeButton()
        }
    }
}

@Composable
private fun RulerIconChip(
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
private fun RulerShapeTypeGlyph(
    type: ShapeType,
    isDefault: Boolean,
    tint: Color
) {
    Canvas(Modifier.size(20.dp)) {
        val s = 2.2f
        if (isDefault) {
            val r = size.minDimension * 0.2f
            val cy = size.height / 2f
            drawCircle(
                color = tint,
                radius = r,
                center = Offset(size.width * 0.22f, cy),
                style = Stroke(s)
            )
            drawRect(
                tint,
                topLeft = Offset(size.width * 0.4f, cy - r * 0.7f),
                size = Size(r * 1.4f, r * 1.4f),
                style = Stroke(s)
            )
            val path = Path().apply {
                moveTo(size.width * 0.78f, cy - r * 0.5f)
                lineTo(size.width * 0.68f, cy + r * 0.6f)
                lineTo(size.width * 0.9f, cy + r * 0.6f)
                close()
            }
            drawPath(path, tint, style = Stroke(s))
        } else {
            val pad = 2.2f
            when (type) {
                ShapeType.CIRCLE -> {
                    val r2 = min(size.width, size.height) * 0.4f
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
                    val rad = min(size.width, size.height) * 0.42f
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
            }
        }
    }
}

private fun shapeSpawnChipDescription(type: ShapeType): String = when (type) {
    ShapeType.CIRCLE -> "Spawn circles. Tap to select this shape only."
    ShapeType.RECTANGLE -> "Spawn rectangles. Tap to select this shape only."
    ShapeType.TRIANGLE -> "Spawn triangles. Tap to select this shape only."
    ShapeType.ARCH -> "Spawn arches. Tap to select this shape only."
}

private data class HsvPreset(val hue: Float, val s: Float, val v: Float) {
    fun toTriple(): Triple<Float, Float, Float> = Triple(hue, s, v)
}

private val CreationColorPresets = listOf(
    HsvPreset(0f, 0.75f, 0.95f),
    HsvPreset(30f, 0.8f, 0.95f),
    HsvPreset(55f, 0.85f, 0.95f),
    HsvPreset(120f, 0.5f, 0.9f),
    HsvPreset(220f, 0.75f, 0.95f),
    HsvPreset(280f, 0.4f, 0.9f)
)

/**
 * Straightedge + tick marks (symbolic ruler). Used in the creation bar and on the float handle.
 * [majorColor] / [minorColor] should come from [MaterialTheme.colorScheme] so light/dark stays legible.
 */
@Composable
fun RulerLineIcon(
    majorColor: Color,
    modifier: Modifier = Modifier,
    minorColor: Color = majorColor.copy(alpha = 0.55f),
    size: DpSize = DpSize(32.dp, 20.dp)
) {
    Canvas(modifier.size(size.width, size.height)) {
        val pad = 1.5f
        val w = this.size.width
        val h = this.size.height
        val y1 = h * 0.38f
        val y2 = h * 0.72f
        val stroke = (minOf(w, h) * 0.065f).coerceIn(1.3f, 2.9f)
        drawLine(
            color = majorColor,
            start = Offset(pad, y1),
            end = Offset(w - pad, y1),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        var x = pad + 2f
        var i = 0
        while (x < w - pad) {
            val majorTick = i % 5 == 0
            val th = if (majorTick) 0.24f * h else 0.11f * h
            drawLine(
                color = if (majorTick) majorColor else minorColor,
                start = Offset(x, y1 + stroke * 0.35f),
                end = Offset(x, y1 + th),
                strokeWidth = if (majorTick) stroke * 0.58f else stroke * 0.48f,
                cap = StrokeCap.Round
            )
            x += w * 0.088f
            i++
        }
        drawLine(
            color = minorColor,
            start = Offset(pad * 0.85f, y2),
            end = Offset(w * 0.42f, y2),
            strokeWidth = stroke * 0.88f,
            cap = StrokeCap.Round
        )
    }
}
