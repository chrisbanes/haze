// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import assertk.assertThat
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DialogBlurInputRefreshInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun dialogBlur_updatesWhenNestedSourceLayerChanges() {
    val hazeState = HazeState()
    val overlayAlpha = mutableFloatStateOf(1f)
    val showDialog = mutableStateOf(true)

    composeTestRule.setContent {
      Box(
        Modifier
          .fillMaxSize()
          .hazeSource(hazeState)
          .background(Color.Blue),
      ) {
        Box(
          Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.floatValue }
            .background(Color.Red),
        )
      }

      if (showDialog.value) {
        Dialog(onDismissRequest = {}) {
          Box(
            Modifier
              .size(160.dp)
              .testTag(DIALOG_EFFECT_TAG)
              .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = HazeBlurStyle {
                  blurRadius(8.dp)
                  noiseFactor(0f)
                  backgroundColor(Color.Transparent)
                  colorEffects(emptyList())
                  fallbackColorEffect(HazeColorEffect.tint(Color.Transparent))
                },
                performanceMode = HazePerformanceMode.Quality,
              ),
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    val before = composeTestRule.captureDialogCenterPixel()
    assertThat(before.red, "initial dialog red channel").isGreaterThan(before.blue)

    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.runOnIdle { overlayAlpha.floatValue = 0f }
    composeTestRule.advanceCrossWindowFrames()

    val after = composeTestRule.captureDialogCenterPixel()
    assertThat(after.blue, "updated dialog blue channel").isGreaterThan(after.red)

    composeTestRule.runOnIdle { overlayAlpha.floatValue = 1f }
    composeTestRule.advanceCrossWindowFrames()

    val restored = composeTestRule.captureDialogCenterPixel()
    assertThat(restored.red, "restored dialog red channel").isGreaterThan(restored.blue)

    composeTestRule.runOnIdle { showDialog.value = false }
    composeTestRule.advanceCrossWindowFrames()
    composeTestRule.runOnIdle { overlayAlpha.floatValue = 0f }
    composeTestRule.advanceCrossWindowFrames()
  }

  private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.advanceCrossWindowFrames() {
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeByFrame()
    waitForIdle()
  }

  private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.captureDialogCenterPixel(): Color {
    val pixels = onNodeWithTag(DIALOG_EFFECT_TAG).captureToImage().toPixelMap()
    return pixels[pixels.width / 2, pixels.height / 2]
  }

  private companion object {
    const val DIALOG_EFFECT_TAG = "dialog_effect"
  }
}
