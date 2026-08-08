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
  fun adaptiveTiers_preserveRepresentativeBlurQuality() = runScreenshotTest {
    var effect by mutableStateOf(HazeBlurStyle { blurRadius(12.dp) })
    var performanceMode by mutableStateOf<HazePerformanceMode>(HazePerformanceMode.Quality)

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(effect, performanceMode = performanceMode)
      }
    }

    val balancedReference = captureRootPixels().snapshot()
    performanceMode = HazePerformanceMode.Adaptive
    waitForIdle()
    val balancedAdaptive = captureRootPixels().snapshot()
    captureRoot("balanced")
    balancedReference.assertPerceptuallyCloseTo(
      balancedAdaptive,
      label = "0.8 tier",
      expectsScaledBlur = supportsRuntimeBlur,
    )

    effect = effect.then { blurRadius(24.dp) }
    performanceMode = HazePerformanceMode.Quality
    waitForIdle()
    val aggressiveReference = captureRootPixels().snapshot()
    performanceMode = HazePerformanceMode.Adaptive
    waitForIdle()
    val aggressiveAdaptive = captureRootPixels().snapshot()
    captureRoot("aggressive")
    aggressiveReference.assertPerceptuallyCloseTo(
      aggressiveAdaptive,
      label = "0.5 tier",
      expectsScaledBlur = supportsRuntimeBlur,
    )
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
    performanceMode = HazePerformanceMode.Adaptive
    waitForIdle()
    val adaptive = captureRootPixels().snapshot()
    captureRoot()

    reference.assertPerceptuallyCloseTo(
      adaptive,
      label = "progressive 0.8 cap",
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
    performanceMode = HazePerformanceMode.Adaptive
    waitForIdle()
    val gradientAdaptive = captureRootPixels().snapshot()
    gradientReference.assertPerceptuallyCloseTo(
      gradientAdaptive,
      label = "gradient mask ordinary tier",
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
    performanceMode = HazePerformanceMode.Adaptive
    waitForIdle()
    val hardEdgeAdaptive = captureRootPixels().snapshot()
    captureRoot()
    hardEdgeReference.assertPerceptuallyCloseTo(
      hardEdgeAdaptive,
      label = "hard-edged mask ordinary tier",
      expectsScaledBlur = supportsRuntimeBlur,
    )
  }

  @Test
  fun boundaryHysteresis_preservesTierUntilExitMargin() = runScreenshotTest {
    var effect by mutableStateOf(HazeBlurStyle { blurRadius(12.dp) })

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(effect, performanceMode = HazePerformanceMode.Adaptive)
      }
    }

    effect = effect.then { blurRadius(11.25.dp) }
    waitForIdle()
    captureRoot("held")

    effect = effect.then { blurRadius(10.dp) }
    waitForIdle()
    captureRoot("exited")
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
