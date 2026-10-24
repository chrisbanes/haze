// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isCloseTo
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test

/**
 * Layered progressive bands overlap, so the style alpha must fade their composite once. Fading
 * each band compounds: three bands cover a point, giving 1 - (1 - a * m0) * (1 - a) * (1 - a * m1).
 */
@OptIn(ExperimentalTestApi::class)
@SdkSuppress(minSdkVersion = 31, maxSdkVersion = 32)
class LayeredProgressiveAlphaInstrumentationTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun layeredProgressive_alphaHalfCoverageEqualsAlphaAtStepBoundaryMidStepAndGradientEnd() {
    val pixels = render(alpha = 0.5f, mask = null)
    for (y in PROBE_YS) {
      // Black covers the red source, so only the effect's coverage can add red.
      assertThat(pixels[CENTER_X, y].red, "coverage at y=$y").isCloseTo(0.5f, TOLERANCE)
    }
  }

  @Test
  fun layeredProgressive_alphaHalfWithUserMaskFadesCompositeOnce() {
    val pixels = render(
      alpha = 0.5f,
      mask = Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = WIDTH.toFloat()),
    )
    for (y in PROBE_YS) {
      for (x in listOf(WIDTH / 2, WIDTH * 3 / 4)) {
        val expected = x / WIDTH.toFloat() * 0.5f
        assertThat(pixels[x, y].red, "coverage at $x,$y").isCloseTo(expected, TOLERANCE)
      }
    }
  }

  @Test
  fun layeredProgressive_opaqueAlphaIsUnchanged() {
    val pixels = render(alpha = 1f, mask = null)
    for (y in PROBE_YS) {
      assertThat(pixels[CENTER_X, y].red, "coverage at y=$y").isCloseTo(1f, 2f / 255)
    }
  }

  private fun render(alpha: Float, mask: Brush?): PixelMap {
    val state = HazeState()
    val progressive = HazeProgressive.LinearGradient(
      start = Offset.Zero,
      end = Offset(0f, HEIGHT.toFloat()),
      startIntensity = 0f,
      endIntensity = 1f,
      easing = LinearEasing,
    )
    rule.setContent {
      // One px per dp makes the five 64dp steps align with the probes below.
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(Modifier.size(WIDTH.dp, HEIGHT.dp).testTag(TAG)) {
          Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
          Box(Modifier.fillMaxSize().background(Color.Black))
          Box(
            Modifier.fillMaxSize().hazeBlur(
              input = HazeInput.Sources(state),
              performanceMode = HazePerformanceMode.Quality,
              style = HazeBlurStyle {
                blurRadius(8.dp)
                noiseFactor(0f)
                backgroundColor(Color.Transparent)
                colorEffects(emptyList())
                fallbackColorEffect(null)
                alpha(alpha)
                mask(mask)
                progressive(progressive)
              },
            ),
          )
        }
      }
    }
    rule.waitForIdle()
    return rule.onNodeWithTag(TAG).captureToImage().toPixelMap()
  }

  private companion object {
    const val TAG = "layered_progressive_alpha"
    const val WIDTH = 100
    const val HEIGHT = 320
    const val CENTER_X = WIDTH / 2
    const val TOLERANCE = 0.03f

    // Step boundary, mid-step, and inside the final 64px step, where only two bands overlap.
    val PROBE_YS = listOf(128, 160, 272)
  }
}
