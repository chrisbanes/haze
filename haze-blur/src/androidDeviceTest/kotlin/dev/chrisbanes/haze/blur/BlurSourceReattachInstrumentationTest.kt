// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
class BlurSourceReattachInstrumentationTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  @SdkSuppress(minSdkVersion = 31)
  fun conditionalBlur_reattachesWithCurrentSourcePixels() {
    val state = HazeState()
    val enabled = mutableStateOf(true)
    val sourceColor = mutableStateOf(Color.Red)
    val style = HazeBlurStyle {
      blurRadius(8.dp)
      noiseFactor(0f)
      backgroundColor(Color.Transparent)
      colorEffects(emptyList())
    }

    composeTestRule.setContent {
      Box(Modifier.size(200.dp).testTag("root")) {
        Box(Modifier.fillMaxSize().hazeSource(state).drawBehind { drawRect(sourceColor.value) })
        // Hide the source so absent blur cannot look like a successful refresh.
        Box(Modifier.fillMaxSize().background(Color.Black))
        Box(
          Modifier.fillMaxSize()
            .hazeSource(state, zIndex = 1f)
            .clip(RoundedCornerShape(16.dp))
            .then(
              if (enabled.value) {
                Modifier.hazeBlur(
                  input = HazeInput.Sources(state),
                  style = style,
                  performanceMode = HazePerformanceMode.Quality,
                )
              } else {
                Modifier
              },
            ),
        )
      }
    }

    fun assertPixel(expected: Color) {
      composeTestRule.waitForIdle()
      val pixels = composeTestRule.onNodeWithTag("root").captureToImage().toPixelMap()
      val actual = pixels[pixels.width / 2, pixels.height / 2]
      assertThat(actual.red).isCloseTo(expected.red, 0.05f)
      assertThat(actual.green).isCloseTo(expected.green, 0.05f)
      assertThat(actual.blue).isCloseTo(expected.blue, 0.05f)
    }

    assertPixel(Color.Red)
    for (color in listOf(Color.Blue, Color.Red)) {
      composeTestRule.runOnIdle { enabled.value = false }
      assertPixel(Color.Black)
      composeTestRule.runOnIdle { sourceColor.value = color }
      composeTestRule.runOnIdle { enabled.value = true }
      assertPixel(color)
    }
  }
}
