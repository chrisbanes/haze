// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.blur.BlurVisualEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [32])
class BlurInputScaleAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun progressiveBlur_preservesScreenSpaceRadiusAcrossInputScales() = runScreenshotTest {
    val effect = BlurVisualEffect().apply {
      blurRadius = 48.dp
      noiseFactor = 0f
      progressive = HazeProgressive.verticalGradient()
    }
    var inputScale by mutableStateOf<HazeInputScale>(HazeInputScale.None)

    setContent {
      ScreenshotTheme {
        val hazeState = rememberHazeState()
        Box(Modifier.fillMaxSize()) {
          Row(
            Modifier
              .fillMaxSize()
              .hazeSource(hazeState),
          ) {
            Box(
              Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black),
            )
            Box(
              Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.White),
            )
          }
          Box(
            Modifier
              .fillMaxSize()
              .hazeEffect(hazeState) {
                this.inputScale = inputScale
                visualEffect = effect
              },
          )
        }
      }
    }

    val unscaled = captureRootPixels().snapshot()
    inputScale = HazeInputScale.Fixed(0.5f)
    waitForIdle()
    val scaled = captureRootPixels().snapshot()

    val scanY = unscaled.height * 3 / 4
    val unscaledWidth = unscaled.horizontalTransitionWidth(scanY)
    val scaledWidth = scaled.horizontalTransitionWidth(scanY)

    assertThat(abs(unscaledWidth - scaledWidth)).isLessThanOrEqualTo(4)
  }
}

private fun PixelSnapshot.horizontalTransitionWidth(y: Int): Int {
  val luminance = (0 until width).map { x -> this[x, y].luminance() }
  val minimum = luminance.min()
  val maximum = luminance.max()
  val range = maximum - minimum
  assertThat(range, "transition luminance range").isGreaterThan(0.5f)

  val low = minimum + range * 0.1f
  val high = minimum + range * 0.9f
  val lowIndex = luminance.indexOfFirst { it >= low }
  val highIndex = luminance.indexOfFirst { it >= high }
  assertThat(lowIndex, "low transition threshold index").isGreaterThanOrEqualTo(0)
  assertThat(highIndex, "high transition threshold index").isGreaterThanOrEqualTo(lowIndex)
  return highIndex - lowIndex
}
