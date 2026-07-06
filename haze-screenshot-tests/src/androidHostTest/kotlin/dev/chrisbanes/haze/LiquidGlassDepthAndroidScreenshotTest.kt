// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.liquidglass.LiquidGlassVisualEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35], qualifiers = "w393dp-h698dp-440dpi")
class LiquidGlassDepthAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun liquidGlass_depthProgression() = runScreenshotTest {
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

  @Test
  fun liquidGlass_depthZeroMasksShape() = runScreenshotTest {
    val shape = RoundedCornerShape(48.dp)
    val visualEffect = LiquidGlassVisualEffect().apply {
      tint = Color.White.copy(alpha = 0.28f)
      refractionStrength = 0f
      depth = 0f
      blurRadius = 32.dp
      specularIntensity = 0f
      ambientResponse = 0f
      edgeSoftness = 16.dp
      this.shape = shape
    }

    setContent {
      ScreenshotTheme {
        LiquidGlassBlurRadiusSample(
          visualEffect = visualEffect,
          shape = shape,
          clipShape = false,
        )
      }
    }

    captureRoot()
  }
}
