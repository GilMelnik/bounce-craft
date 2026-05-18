package com.colorbounce.baby

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.min

@Composable
fun DonationScreen(
    billingManager: DonationBillingManager,
    onBack: () -> Unit
) {
    val billingState by billingManager.state.collectAsStateWithLifecycle()
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
                    start = 24.dp,
                    end = 24.dp
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
                    text = "Buy me a coffee",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(scheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(20.dp)) {
                        val ink = scheme.onSurfaceVariant
                        drawLine(ink, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 4f)
                        drawLine(ink, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 4f)
                    }
                }
            }

            Text(
                text = "If you enjoy the app, please consider a contribution to help cover the costs of maintaining it.",
                style = MaterialTheme.typography.bodyLarge
            )

            billingState.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            billingState.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error
                )
            }

            if (!billingState.billingReady && billingState.productDetails.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Connecting to Google Play…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            DonationTier.all.forEach { tier ->
                val purchasing = billingState.purchasingTier == tier
                DonationTierCard(
                    tier = tier,
                    price = billingManager.priceLabel(tier),
                    enabled = !purchasing && billingState.purchasingTier == null,
                    purchasing = purchasing,
                    onClick = {
                        billingManager.clearMessages()
                        billingManager.launchPurchase(tier)
                    }
                )
            }

            Text(
                text = "Contributions are processed securely through Google Play. Thank you!",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun DonationTierCard(
    tier: DonationTier,
    price: String,
    enabled: Boolean,
    purchasing: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.55f

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerHigh.copy(alpha = 0.85f * alpha),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            scheme.outlineVariant.copy(alpha = 0.5f * alpha)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(tier.shapeSize + 16.dp),
                contentAlignment = Alignment.Center
            ) {
                DonationShapePreview(
                    type = tier.shapeType,
                    size = tier.shapeSize,
                    hue = tier.hue
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tier.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = price,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.primary
                )
            }
            if (purchasing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    text = "Support",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary
                )
            }
        }
    }
}

@Composable
private fun DonationShapePreview(
    type: ShapeType,
    size: Dp,
    hue: Float
) {
    val fill = Color.hsv(hue.normalizeHue(), 0.85f, 0.95f)
    Canvas(Modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val dim = min(this.size.width, this.size.height)
        when (type) {
            ShapeType.CIRCLE -> {
                drawCircle(color = fill, radius = dim * 0.38f, center = Offset(cx, cy))
            }
            ShapeType.RECTANGLE -> {
                val w = dim * 0.72f
                val h = dim * 0.72f
                drawRect(
                    color = fill,
                    topLeft = Offset(cx - w / 2f, cy - h / 2f),
                    size = Size(w, h)
                )
            }
            ShapeType.ARCH -> {
                val radius = dim * 0.42f
                val centerY = cy + radius * 0.15f
                val strokeWidth = min(dim * 0.22f, radius * 0.55f)
                drawArc(
                    color = fill,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - radius, centerY - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            ShapeType.HEART -> {
                val fake = GameShape.create(
                    id = 0L,
                    type = ShapeType.HEART,
                    x = cx,
                    y = cy,
                    width = dim * 0.88f,
                    height = dim * 0.88f,
                    vx = 0f,
                    vy = 0f,
                    hue = hue,
                    saturation = 0.85f,
                    value = 0.95f,
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
                    drawPath(path, color = fill)
                }
            }
            else -> Unit
        }
    }
}
