package com.colorbounce.baby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun CreationModeRuler(
    session: CreationSession,
    onSessionChange: (CreationSession) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    maxHeight: Dp
) {
    val scheme = MaterialTheme.colorScheme
    if (!expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            AssistChip(
                onClick = onToggleExpanded,
                label = {
                    Text(
                        "Ruler & tools",
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Expand ruler"
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = scheme.onSurface,
                    leadingIconContentColor = scheme.onSurface
                )
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxHeight)
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Creation tools",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onToggleExpanded,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Hide ruler"
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Spawning — shape type (new shapes)",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShapeTypeChoiceChip(
                label = "Default",
                selected = session.spawnType == null,
                onClick = { onSessionChange(session.copy(spawnType = null)) }
            ) {
                RulerShapeTypeGlyph(ShapeType.CIRCLE, isDefault = true, tint = scheme.primary)
            }
            for (t in listOf(
                ShapeType.CIRCLE,
                ShapeType.RECTANGLE,
                ShapeType.TRIANGLE,
                ShapeType.ARCH
            )) {
                ShapeTypeChoiceChip(
                    label = null,
                    selected = session.spawnType == t,
                    onClick = { onSessionChange(session.copy(spawnType = t)) }
                ) {
                    RulerShapeTypeGlyph(t, isDefault = false, tint = scheme.primary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Color for new shapes",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorSwatch(
                selected = session.spawnColor == null,
                onClick = { onSessionChange(session.copy(spawnColor = null)) }
            ) {
                Text("Auto", style = MaterialTheme.typography.labelSmall, color = scheme.onSurface)
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
                ColorSwatch(
                    selected = same,
                    onClick = { onSessionChange(session.copy(spawnColor = preset.toTriple())) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .border(1.dp, scheme.outline.copy(alpha = 0.5f), CircleShape)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(8.dp))
        Text(
            "Toggles: double-tap a shape to open its menu. Physics runs again when the menu " +
                "closes unless you paused with the play/pause control above.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2f
        )
        Spacer(Modifier.height(8.dp))
        RulerPlayPauseControl(
            pausedByRuler = session.physicsPaused,
            onToggle = {
                onSessionChange(session.copy(physicsPaused = !session.physicsPaused))
            }
        )
        RulerSwitchRow(
            label = "No color change while dragging",
            checked = session.disableHueWhileDragging,
            onChecked = { onSessionChange(session.copy(disableHueWhileDragging = it)) }
        )
        RulerSwitchRow(
            label = "New shapes are pinned (fixed in place until dragged)",
            checked = session.newShapesPinned,
            onChecked = { onSessionChange(session.copy(newShapesPinned = it)) }
        )
        RulerSwitchRow(
            label = "New shapes never time out (immortal)",
            checked = session.newShapesImmortal,
            onChecked = { onSessionChange(session.copy(newShapesImmortal = it)) }
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ShapeTypeChoiceChip(
    label: String?,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { icon() }
                if (label != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedLabelColor = scheme.onSecondaryContainer,
            labelColor = scheme.onSurface,
            selectedContainerColor = scheme.secondaryContainer
        )
    )
}

@Composable
private fun ColorSwatch(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { content() },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = scheme.secondaryContainer,
            labelColor = scheme.onSurface
        )
    )
}

@Composable
private fun RulerShapeTypeGlyph(
    type: ShapeType,
    isDefault: Boolean,
    tint: Color
) {
    Canvas(Modifier.size(22.dp)) {
        val s = 2.5f
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
            val pad = 2.5f
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

@Composable
private fun RulerPauseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val barW = w * 0.22f
        val gap = w * 0.18f
        val left = w * 0.18f
        val top = h * 0.2f
        val barH = h * 0.6f
        drawRect(color, topLeft = Offset(left, top), size = Size(barW, barH))
        drawRect(
            color,
            topLeft = Offset(left + barW + gap, top),
            size = Size(barW, barH)
        )
    }
}

@Composable
private fun RulerPlayPauseControl(
    pausedByRuler: Boolean,
    onToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Pause physics (ruler)",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface
        )
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(40.dp)
        ) {
            if (pausedByRuler) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play — resume physics",
                    tint = scheme.primary
                )
            } else {
                RulerPauseGlyph(
                    color = scheme.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .semantics { contentDescription = "Pause physics" }
                )
            }
        }
    }
}

@Composable
private fun RulerSwitchRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = scheme.primary,
                checkedTrackColor = scheme.primaryContainer
            )
        )
    }
}
