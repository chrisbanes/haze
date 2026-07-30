// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import assertk.assertThat
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35], qualifiers = "w393dp-h698dp-440dpi")
class GlassInteractionAndroidRegressionTest : ScreenshotTest() {

  @Test
  fun runtimeShader_hoverThenPress_updatesActiveUniforms() = runScreenshotTest {
    val effect = GlassTestConfiguration().apply {
      applyTestHoverAndPressResponses()
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setContent {
      ScreenshotTheme { GlassInteractionScene("glass", "HOVER / PRESS", effect) }
    }

    onNodeWithTag("glass").performMouseInput { enter(Offset(36f, 36f)) }
    waitForIdle()
    val hover = captureRootPixels().snapshot()

    onNodeWithTag("glass").performTouchInput {
      down(Offset(center.x * 1.5f, center.y * 1.5f))
    }
    waitForIdle()
    val press = captureRootPixels().snapshot()

    assertThat(hover.changedPixelRatio(press)).isGreaterThan(0.01f)
  }
}
