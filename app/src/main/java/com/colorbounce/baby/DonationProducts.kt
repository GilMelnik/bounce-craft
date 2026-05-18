package com.colorbounce.baby

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * In-app donation tiers. Create matching one-time products in Play Console (consumable).
 */
enum class DonationTier(
    val productId: String,
    val label: String,
    val fallbackPrice: String,
    val shapeType: ShapeType,
    val shapeSize: Dp,
    val hue: Float
) {
    SMALL(
        productId = "donation_small",
        label = "Small",
        fallbackPrice = "$1",
        shapeType = ShapeType.CIRCLE,
        shapeSize = 52.dp,
        hue = 210f
    ),
    MEDIUM(
        productId = "donation_medium",
        label = "Medium",
        fallbackPrice = "$5",
        shapeType = ShapeType.RECTANGLE,
        shapeSize = 76.dp,
        hue = 38f
    ),
    LARGE(
        productId = "donation_large",
        label = "Large",
        fallbackPrice = "$10",
        shapeType = ShapeType.ARCH,
        shapeSize = 100.dp,
        hue = 145f
    ),
    HUGE(
        productId = "donation_huge",
        label = "Huge",
        fallbackPrice = "$20",
        shapeType = ShapeType.HEART,
        shapeSize = 124.dp,
        hue = 340f
    );

    companion object {
        val all: List<DonationTier> = entries
        val productIds: List<String> = all.map { it.productId }
    }
}
