// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [31, 32])
class ProgressiveBlurAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun progressiveBlur_contentInputKeepsCoverageAtBothEndsOfTheAxis() = runScreenshotTest {
    val style = HazeBlurStyle {
      blurRadius(48.dp)
      noiseFactor(0f)
      progressive(
        HazeProgressive.horizontalGradient(
          startIntensity = 0.5f,
          endIntensity = 0.5f,
        ),
      )
    }

    setContent {
      ScreenshotTheme {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.Black),
        ) {
          Column(
            Modifier
              .fillMaxSize()
              .hazeBlur(
                input = HazeInput.Content,
                style = style,
                performanceMode = HazePerformanceMode.Quality,
              ),
          ) {
            Box(
              Modifier
                .weight(1f)
                .fillMaxSize()
                .background(Color.Black),
            )
            Box(
              Modifier
                .weight(1f)
                .fillMaxSize()
                .background(Color.White),
            )
          }
        }
      }
    }

    val pixels = captureRootPixels()
    listOf(pixels.width / 4, pixels.width * 3 / 4).forEach { x ->
      val farUpper = pixels[x, pixels.height / 4].luminance()
      val farLower = pixels[x, pixels.height * 3 / 4].luminance()
      assertThat(farUpper, "upper source at x=$x").isLessThan(0.2f)
      assertThat(farLower, "lower source at x=$x").isGreaterThan(0.8f)

      listOf(pixels.height / 2 - 4, pixels.height / 2 + 4).forEach { y ->
        val luminance = pixels[x, y].luminance()
        val label = "progressive transition at ($x, $y) on ${pixels.width}x${pixels.height}"
        assertThat(luminance, label).isGreaterThan(0.02f)
        assertThat(luminance, label).isLessThan(0.98f)
      }
    }
  }
}
