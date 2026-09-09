// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class SourceSelectionAndroidTest : ScreenshotTest() {
  @Test
  @Config(sdk = [32, 35])
  fun blur_selectionChange_capturesPreviouslyUnselectedSource() = assertSelectionChange(glass = false)

  @Test
  fun glass_selectionChange_capturesPreviouslyUnselectedSource() = assertSelectionChange(glass = true)

  private fun assertSelectionChange(glass: Boolean) = runScreenshotTest {
    val state = HazeState()
    val selectedKey = mutableStateOf("first")
    val selection = HazeSourceSelection.All.where { it.key == selectedKey.value }
    val input = HazeInput.Sources(state, selection = selection)

    setContent {
      ScreenshotTheme {
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().hazeSource(state, key = "first").background(Color.Red))
          Box(Modifier.fillMaxSize().hazeSource(state, key = "second").background(Color.Blue))
          Box(Modifier.fillMaxSize().background(Color.Black))
          val modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
          Box(
            if (glass) {
              modifier.hazeGlass(
                input = input,
                performanceMode = HazePerformanceMode.Quality,
                style = GlassStyle {
                  backgroundColor(Color.Transparent)
                  tint(Color.Transparent)
                  specularIntensity(0f)
                  optics(blurRadius = 8.dp, depth = 0.5f)
                },
              )
            } else {
              modifier.hazeBlur(
                input = input,
                performanceMode = HazePerformanceMode.Quality,
                style = HazeBlurStyle {
                  blurRadius(8.dp)
                  noiseFactor(0f)
                  colorEffects(emptyList())
                },
              )
            },
          )
        }
      }
    }

    fun assertOutput(expected: Color) {
      waitForIdle()
      val pixels = captureRootPixels()
      val actual = pixels[pixels.width / 2, pixels.height / 2]
      assertThat(actual.red).isCloseTo(expected.red, 0.1f)
      assertThat(actual.green).isCloseTo(expected.green, 0.1f)
      assertThat(actual.blue).isCloseTo(expected.blue, 0.1f)
    }

    assertOutput(Color.Red)
    selectedKey.value = "second"
    assertOutput(Color.Blue)
    selectedKey.value = "first"
    assertOutput(Color.Red)
  }
}
