// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isCloseTo
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class BlurEffectResizeInstrumentationTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  @SdkSuppress(minSdkVersion = 31)
  fun resizedBlur_drawsAcrossItsCurrentBounds() {
    val state = HazeState()
    val effectWidth = mutableStateOf(100.dp)
    val style = HazeBlurStyle {
      blurRadius(8.dp)
      noiseFactor(0f)
      backgroundColor(Color.Transparent)
      colorEffects(emptyList())
    }

    composeTestRule.setContent {
      Box(Modifier.size(200.dp).testTag("root")) {
        Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
        // Hide the source so only the blur can put red on screen.
        Box(Modifier.fillMaxSize().background(Color.Black))
        Box(
          Modifier
            .width(effectWidth.value)
            .fillMaxHeight()
            .hazeBlur(
              input = HazeInput.Sources(state),
              style = style,
              performanceMode = HazePerformanceMode.Quality,
            ),
        )
      }
    }

    fun assertPixel(xFraction: Float, expected: Color) {
      composeTestRule.waitForIdle()
      val pixels = composeTestRule.onNodeWithTag("root").captureToImage().toPixelMap()
      val actual = pixels[(pixels.width * xFraction).toInt(), pixels.height / 2]
      assertThat(actual.red, "red at $xFraction").isCloseTo(expected.red, 0.05f)
      assertThat(actual.green, "green at $xFraction").isCloseTo(expected.green, 0.05f)
      assertThat(actual.blue, "blue at $xFraction").isCloseTo(expected.blue, 0.05f)
    }

    assertPixel(0.25f, Color.Red)
    assertPixel(0.75f, Color.Black)

    // Grown: the blur is recorded into the layer it already has, and has to reach the new edge.
    composeTestRule.runOnIdle { effectWidth.value = 200.dp }
    assertPixel(0.25f, Color.Red)
    assertPixel(0.9f, Color.Red)

    // Shrunk again: and stop at the old one.
    composeTestRule.runOnIdle { effectWidth.value = 100.dp }
    assertPixel(0.25f, Color.Red)
    assertPixel(0.75f, Color.Black)
  }
}
