// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class BlurInputScaleScreenshotTest : ScreenshotTest() {

  @Test
  fun fixedModes_preserveRepresentativeBlurFeatures() = runScreenshotTest {
    val effect = HazeBlurStyle {
      blurRadius(24.dp)
      mask(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)))
      progressive(HazeProgressive.verticalGradient())
    }
    var performanceMode by mutableStateOf<HazePerformanceMode>(HazePerformanceMode.Quality)

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(effect, performanceMode = performanceMode)
      }
    }

    captureRoot("quality")
    performanceMode = HazePerformanceMode.Balanced
    waitForIdle()
    captureRoot("balanced")
    performanceMode = HazePerformanceMode.Performance
    waitForIdle()
    captureRoot("performance")
  }

  @Test
  fun progressiveDefault_preservesGeometryAtBalancedCap() = runScreenshotTest {
    val effect = HazeBlurStyle {
      blurRadius(24.dp)
      progressive(HazeProgressive.verticalGradient())
    }
    var performanceMode by mutableStateOf<HazePerformanceMode>(HazePerformanceMode.Quality)

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(effect, performanceMode = performanceMode)
      }
    }

    val reference = captureRootPixels().snapshot()
    performanceMode = HazePerformanceMode.Default
    waitForIdle()
    val defaultProfile = captureRootPixels().snapshot()
    captureRoot()

    reference.assertPerceptuallyCloseTo(
      defaultProfile,
      label = "progressive Balanced default",
      expectsScaledBlur = supportsRuntimeBlur,
    )
  }

  @Test
  fun gradientAndHardEdgedMasks_useOrdinaryLadder() = runScreenshotTest {
    var effect by mutableStateOf(
      HazeBlurStyle {
        blurRadius(24.dp)
        mask(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)))
      },
    )
    var performanceMode by mutableStateOf<HazePerformanceMode>(HazePerformanceMode.Quality)

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(effect, performanceMode = performanceMode)
      }
    }

    val gradientReference = captureRootPixels().snapshot()
    performanceMode = HazePerformanceMode.Balanced
    waitForIdle()
    val gradientBalanced = captureRootPixels().snapshot()
    gradientReference.assertPerceptuallyCloseTo(
      gradientBalanced,
      label = "gradient mask Balanced profile",
      expectsScaledBlur = supportsRuntimeBlur,
    )

    effect = effect.then {
      mask(
        Brush.verticalGradient(
          0f to Color.Black,
          0.49f to Color.Black,
          0.51f to Color.Transparent,
          1f to Color.Transparent,
        ),
      )
    }
    performanceMode = HazePerformanceMode.Quality
    waitForIdle()
    val hardEdgeReference = captureRootPixels().snapshot()
    performanceMode = HazePerformanceMode.Balanced
    waitForIdle()
    val hardEdgeBalanced = captureRootPixels().snapshot()
    captureRoot()
    hardEdgeReference.assertPerceptuallyCloseTo(
      hardEdgeBalanced,
      label = "hard-edged mask Balanced profile",
      expectsScaledBlur = supportsRuntimeBlur,
    )
  }
}

private fun PixelSnapshot.assertPerceptuallyCloseTo(
  other: PixelSnapshot,
  label: String,
  expectsScaledBlur: Boolean,
) {
  val meanAbsoluteDifference = meanAbsoluteDifference(other)
  val changedPixelRatio = changedPixelRatio(other)
  println(
    "$label perceptual comparison: changedPixelRatio=$changedPixelRatio, " +
      "meanAbsoluteDifference=$meanAbsoluteDifference",
  )
  if (expectsScaledBlur) {
    assertThat(changedPixelRatio, "$label changed pixel ratio").isGreaterThan(0.001f)
  } else {
    assertThat(changedPixelRatio, "$label scrim fallback changed pixel ratio")
      .isLessThanOrEqualTo(0.0001f)
  }
  assertThat(meanAbsoluteDifference, "$label mean absolute difference")
    .isLessThanOrEqualTo(0.01f)
}
