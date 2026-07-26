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
import dev.chrisbanes.haze.blur.BlurVisualEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class BlurInputScaleScreenshotTest : ScreenshotTest() {

  @Test
  fun adaptiveTiers_preserveRepresentativeBlurQuality() = runScreenshotTest {
    val effect = BlurVisualEffect().apply { blurRadius = 12.dp }
    var inputScale by mutableStateOf<HazeInputScale>(HazeInputScale.None)

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(effect, inputScale = inputScale)
      }
    }

    val balancedReference = captureRootPixels().snapshot()
    inputScale = HazeInputScale.Default
    waitForIdle()
    val balancedAdaptive = captureRootPixels().snapshot()
    captureRoot("balanced")
    balancedReference.assertPerceptuallyCloseTo(
      balancedAdaptive,
      label = "0.8 tier",
      expectsScaledBlur = supportsRuntimeBlur,
    )

    effect.blurRadius = 24.dp
    inputScale = HazeInputScale.None
    waitForIdle()
    val aggressiveReference = captureRootPixels().snapshot()
    inputScale = HazeInputScale.Default
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
    val effect = BlurVisualEffect().apply {
      blurRadius = 24.dp
      progressive = HazeProgressive.verticalGradient()
    }
    var inputScale by mutableStateOf<HazeInputScale>(HazeInputScale.None)

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(effect, inputScale = inputScale)
      }
    }

    val reference = captureRootPixels().snapshot()
    inputScale = HazeInputScale.Default
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
    val effect = BlurVisualEffect().apply {
      blurRadius = 24.dp
      mask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
    }
    var inputScale by mutableStateOf<HazeInputScale>(HazeInputScale.None)

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(effect, inputScale = inputScale)
      }
    }

    val gradientReference = captureRootPixels().snapshot()
    inputScale = HazeInputScale.Default
    waitForIdle()
    val gradientAdaptive = captureRootPixels().snapshot()
    gradientReference.assertPerceptuallyCloseTo(
      gradientAdaptive,
      label = "gradient mask ordinary tier",
      expectsScaledBlur = supportsRuntimeBlur,
    )

    effect.mask = Brush.verticalGradient(
      0f to Color.Black,
      0.49f to Color.Black,
      0.51f to Color.Transparent,
      1f to Color.Transparent,
    )
    inputScale = HazeInputScale.None
    waitForIdle()
    val hardEdgeReference = captureRootPixels().snapshot()
    inputScale = HazeInputScale.Default
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
    val effect = BlurVisualEffect().apply { blurRadius = 12.dp }

    setContent {
      ScreenshotTheme {
        CreditCardContentBlurring(effect, inputScale = HazeInputScale.Default)
      }
    }

    effect.blurRadius = 11.25.dp
    waitForIdle()
    captureRoot("held")

    effect.blurRadius = 10.dp
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
