// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
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

@OptIn(InternalHazeApi::class)
class HazeSourceDrawUpdateTest : ScreenshotTest() {

  @Test
  fun blur_sourceDrawChange_preservesExpectedOutput() = verifySourceDrawChanges(glass = false)

  @Test
  fun glass_sourceDrawChange_preservesExpectedOutput() = verifySourceDrawChanges(glass = true)

  private fun verifySourceDrawChanges(glass: Boolean) = runScreenshotTest(size = Size(200f, 200f)) {
    val sourceColor = mutableStateOf(Color.Red)

    setContent {
      ScreenshotTheme {
        val state = rememberHazeState()
        Box(Modifier.fillMaxSize()) {
          Canvas(Modifier.fillMaxSize().hazeSource(state)) {
            // Only the source's draw phase observes this value. The effect's style and
            // composition remain unchanged while its retained layer receives new input.
            drawRect(sourceColor.value)
          }
          // Hide the source itself so a transparent or missing effect cannot pass.
          Box(Modifier.fillMaxSize().background(Color.Black))
          val effect = if (glass) {
            Modifier.hazeGlass(
              input = HazeInput.Sources(state),
              performanceMode = HazePerformanceMode.Quality,
              style = GlassStyle {
                backgroundColor(Color.Transparent)
                tint(Color.Transparent)
                specularIntensity(0f)
                optics(blurRadius = 8.dp, depth = 0.5f)
              },
            )
          } else {
            Modifier.hazeBlur(
              input = HazeInput.Sources(state),
              performanceMode = HazePerformanceMode.Quality,
              style = HazeBlurStyle {
                blurRadius(8.dp)
                noiseFactor(0f)
                colorEffects(emptyList())
              },
            )
          }
          Box(Modifier.fillMaxSize().then(effect))
        }
      }
    }
    val samplesSource = if (glass) isRuntimeShaderRenderEffectSupported() else supportsRuntimeBlur
    fun assertOutput(source: Color) {
      // Transparent fallback styles leave the black occluder visible on older Android APIs.
      val expected = if (samplesSource) source else Color.Black
      val actual = captureRootPixels().let { it[it.width / 2, it.height / 2] }
      assertThat(actual.red).isCloseTo(expected.red, 0.1f)
      assertThat(actual.green).isCloseTo(expected.green, 0.1f)
      assertThat(actual.blue).isCloseTo(expected.blue, 0.1f)
    }
    waitForIdle()
    assertOutput(Color.Red)

    sourceColor.value = Color.Blue
    waitForIdle()
    assertOutput(Color.Blue)

    sourceColor.value = Color.Red
    waitForIdle()
    assertOutput(Color.Red)
  }
}
