// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class GlassInputScaleScreenshotTest : ScreenshotTest() {

  @Test
  fun explicitTiers_preserveProgressiveRoundedRefractionAcrossTransition() = runScreenshotTest {
    val shape = RoundedCornerShape(32.dp)
    val effect = GlassTestConfiguration().apply {
      optics = GlassOptics.Fixed(
        refractionStrength = 1f,
        refractionDisplacement = 24.dp,
        depth = 1f,
        blurRadius = 24.dp,
        progressive = HazeProgressive.verticalGradient(),
      )
      tint = Color.White.copy(alpha = 0.08f)
      this.shape = shape
    }
    var performanceMode by mutableStateOf<HazePerformanceMode>(HazePerformanceMode.Quality)

    setContent {
      ScreenshotTheme {
        GlassInvariantSample(
          effect = effect,
          performanceMode = performanceMode,
          shape = shape,
        )
      }
    }

    val reference = captureRootPixels().snapshot()

    performanceMode = HazePerformanceMode.Balanced
    waitForIdle()
    val balanced = captureRootPixels().snapshot()
    captureRoot("balanced")
    reference.assertRepresentativeGlassQuality(balanced, "balanced tier", maximumDifference = 0.03f)

    performanceMode = HazePerformanceMode.Performance
    waitForIdle()
    val aggressive = captureRootPixels().snapshot()
    captureRoot("aggressive")
    reference.assertRepresentativeGlassQuality(aggressive, "aggressive tier", maximumDifference = 0.05f)

    performanceMode = HazePerformanceMode.Adaptive
    waitForIdle()
    val adaptive = captureRootPixels().snapshot()
    captureRoot("adaptive")
    reference.assertRepresentativeGlassQuality(adaptive, "adaptive tier", maximumDifference = 0.05f)

    performanceMode = HazePerformanceMode.Balanced
    waitForIdle()
    val returned = captureRootPixels().snapshot()
    captureRoot("returned")
    assertThat(
      balanced.meanAbsoluteDifference(returned),
      "return transition mean absolute difference",
    ).isLessThanOrEqualTo(1f / 255f)
  }
}

private fun PixelSnapshot.assertRepresentativeGlassQuality(
  other: PixelSnapshot,
  label: String,
  maximumDifference: Float,
) {
  // Android's pre-RuntimeShader fallback ignores input scaling, so a zero difference is valid.
  val changedPixelRatio = changedPixelRatio(other)
  val meanAbsoluteDifference = meanAbsoluteDifference(other)
  println(
    "$label visual comparison: changedPixelRatio=$changedPixelRatio, " +
      "meanAbsoluteDifference=$meanAbsoluteDifference",
  )
  assertThat(meanAbsoluteDifference, "$label mean absolute difference")
    .isLessThanOrEqualTo(maximumDifference)
}
