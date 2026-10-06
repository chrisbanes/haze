// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class BlurEdgeCoverageInstrumentationTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  @SdkSuppress(minSdkVersion = 31)
  fun downsampledBlur_coversEdgesWhenResized() {
    val state = HazeState()
    // Balanced maps 203px to 160.49px; rounding down clips the final output row.
    val height = mutableStateOf(203)
    val mode = mutableStateOf<HazePerformanceMode>(HazePerformanceMode.Quality)
    val style = HazeBlurStyle {
      blurRadius(8.dp)
      noiseFactor(0f)
      backgroundColor(Color.Transparent)
      colorEffects(emptyList())
    }
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(Modifier.size(305.dp, height.value.dp).testTag("root")) {
          Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
          Box(Modifier.fillMaxSize().background(Color.Black))
          Box(
            Modifier.fillMaxSize().hazeBlur(
              input = HazeInput.Sources(state),
              style = style,
              performanceMode = mode.value,
            ),
          )
        }
      }
    }

    for (performanceMode in listOf(HazePerformanceMode.Quality, HazePerformanceMode.Balanced)) {
      composeTestRule.runOnIdle { mode.value = performanceMode }
      for (size in listOf(203, 208, 203)) {
        composeTestRule.runOnIdle { height.value = size }
        composeTestRule.waitForIdle()
        val pixels = composeTestRule.onNodeWithTag("root").captureToImage().toPixelMap()
        assertThat(pixels.width).isEqualTo(305)
        assertThat(pixels.height).isEqualTo(size)
        // The source is hidden by black, so only the effect can draw red at these edges.
        val edges = mapOf(
          "top" to pixels[pixels.width / 2, 0],
          "bottom" to pixels[pixels.width / 2, pixels.height - 1],
          "left" to pixels[0, pixels.height / 2],
          "right" to pixels[pixels.width - 1, pixels.height / 2],
        )
        for ((edge, pixel) in edges) {
          assertThat(pixel.red, "$performanceMode height=$size $edge edge").isCloseTo(1f, 0.05f)
          assertThat(pixel.green).isCloseTo(0f, 0.05f)
          assertThat(pixel.blue).isCloseTo(0f, 0.05f)
        }
      }
    }
  }
}
