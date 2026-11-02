// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
@SdkSuppress(minSdkVersion = 31)
class ScaledContentEdgeInstrumentationTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun contentBlur_coversRoundedCaptureEdgesAcrossModesSizesAndAlpha() {
    val cases = listOf(413 to 384, 412 to 383, 414 to 385, 400 to 371, 405 to 376, 203 to 203, 208 to 208)
    val modes = listOf(
      HazePerformanceMode.Quality,
      HazePerformanceMode.Default,
      HazePerformanceMode.Performance,
      HazePerformanceMode.Fixed(0.75f),
    )
    for ((width, height) in cases) for (radius in listOf(4, 80)) for (alpha in listOf(1f, 0.5f)) {
      for (mode in modes) {
        val pixels = render(width, height, radius, alpha, mode)
        assertThat(pixels.width).isEqualTo(width + 20)
        assertThat(pixels.height).isEqualTo(height + 20)
        for (x in 10 until width + 10) {
          assertFlat(pixels[x, height + 9], alpha, "${width}x$height $mode alpha=$alpha bottom x=$x")
        }
        for (y in 10 until height + 10) {
          assertFlat(pixels[width + 9, y], alpha, "${width}x$height $mode alpha=$alpha right y=$y")
        }
      }
    }
  }

  @Test
  fun contentBlur_reversesSizeAndQualityChangesOnTheSameNode() {
    val size = mutableStateOf(413)
    val mode = mutableStateOf(HazePerformanceMode.Quality)
    rule.setContent { content(size.value, 384, mode.value, 4, 1f, density = 1f) }
    for ((nextSize, nextMode) in listOf(
      413 to HazePerformanceMode.Quality,
      414 to HazePerformanceMode.Default,
      413 to HazePerformanceMode.Quality,
    )) {
      rule.runOnIdle {
        size.value = nextSize
        mode.value = nextMode
      }
      rule.waitForIdle()
      val map = rule.onNodeWithTag(ROOT).captureToImage().toPixelMap()
      assertThat(map.width).isEqualTo(nextSize + 20)
      assertFlat(map[nextSize + 9, 200], 0f, "resize/mode reverse right edge")
    }
  }

  @Test
  fun contentBlur_supportsOrdinaryDensityWithPixelDerivedSize() {
    val pixels = render(413, 384, 4, 1f, HazePerformanceMode.Default, density = 2f)
    assertThat(pixels.width).isEqualTo(866)
    assertThat(pixels.height).isEqualTo(808)
    assertFlat(pixels[845, 400], 1f, "ordinary-density right edge")
  }

  private fun render(
    width: Int,
    height: Int,
    radius: Int,
    alpha: Float,
    mode: HazePerformanceMode,
    density: Float = 1f,
  ): androidx.compose.ui.graphics.PixelMap {
    rule.setContent { content(width, height, mode, radius, alpha, density) }
    rule.waitForIdle()
    return rule.onNodeWithTag(ROOT).captureToImage().toPixelMap()
  }

  @Composable
  private fun content(
    width: Int,
    height: Int,
    mode: HazePerformanceMode,
    radius: Int,
    alpha: Float,
    density: Float,
  ) {
    CompositionLocalProvider(LocalDensity provides Density(density)) {
      Box(Modifier.size((width + 20).dp / density, (height + 20).dp / density).background(Color.Magenta).testTag(ROOT)) {
        Box(
          Modifier.padding((10.dp / density)).size(width.dp / density, height.dp / density).background(Color.White.copy(alpha = alpha))
            .hazeBlur(
              input = HazeInput.Content,
              expandLayerBounds = false,
              performanceMode = mode,
              style = HazeBlurStyle {
                blurRadius(radius.dp / density)
                noiseFactor(0f)
                backgroundColor(Color.Transparent)
                colorEffects(emptyList())
              },
            ),
        )
      }
    }
  }

  private fun assertFlat(pixel: androidx.compose.ui.graphics.Color, whiteAlpha: Float, context: String) {
    val whiteWeight = whiteAlpha * 0.5f
    val expectedRed = 1f
    val expectedGreen = 1f - whiteWeight
    val expectedBlue = 1f
    val tolerance = 8f / 255f
    assertThat(pixel.red, "$context red").isCloseTo(expectedRed, tolerance)
    assertThat(pixel.green, "$context green").isCloseTo(expectedGreen, tolerance)
    assertThat(pixel.blue, "$context blue").isCloseTo(expectedBlue, tolerance)
    assertThat(pixel.alpha, "$context alpha").isCloseTo(1f, tolerance)
  }

  private companion object {
    const val ROOT = "scaled_content_edge_root"
  }
}
