// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class LiquidGlassDepthAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun liquidGlass_depthProgression() = runScreenshotTest(relaxedTolerance = true) {
    val shape = RoundedCornerShape(28.dp)
    val visualEffect = liquidGlassDepthProgressionVisualEffect(
      depth = 0f,
      shape = shape,
    )

    setContent {
      ScreenshotTheme {
        LiquidGlassDepthSingleSample(
          visualEffect = visualEffect,
          shape = shape,
        )
      }
    }

    captureRoot("0")

    visualEffect.depth = 0.5f
    waitForIdle()
    captureRoot("50")

    visualEffect.depth = 1f
    waitForIdle()
    captureRoot("100")
  }
}
