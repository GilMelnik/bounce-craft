package com.colorbounce.baby

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RapidTouchStabilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rapidTouchSequences_keepGameResponsive() {
        composeRule.onNodeWithText("Play").performClick()
        composeRule.waitForIdle()

        val gameSurface = composeRule.onNodeWithTag("game_surface")
        gameSurface.assertExists()
        composeRule.onNodeWithTag("exit_button").assertExists()

        // Stress test with many rapid drag interactions.
        repeat(200) { idx ->
            val baseX = 120f + (idx % 8) * 95f
            val baseY = 140f + (idx % 5) * 110f
            gameSurface.performTouchInput {
                down(Offset(baseX, baseY))
                moveBy(Offset(8f + (idx % 10), 10f + (idx % 7)))
                moveBy(Offset(6f, 9f))
                up()
            }
            composeRule.mainClock.advanceTimeBy(8L)
        }

        composeRule.waitForIdle()
        gameSurface.assertExists()
        composeRule.onNodeWithTag("exit_button").assertExists()
    }
}
