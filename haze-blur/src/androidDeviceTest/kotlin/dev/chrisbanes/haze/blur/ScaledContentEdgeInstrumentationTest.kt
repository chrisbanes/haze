// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
  fun contentBlur_reported413x384DefaultCaseHasNoTransparentEdge() {
    val config = mutableStateOf(EdgeCase(413, 384, 4, 1f, HazePerformanceMode.Default))
    setContent(config, density = 1f)
    assertEdges(config.value)
  }

  @Test
  fun contentBlur_coversRoundedCaptureEdgesAcrossModesSizesAndAlpha() {
    val config = mutableStateOf(EdgeCase(413, 384, 4, 1f, HazePerformanceMode.Default))
    setContent(config, density = 1f)
    val sizes = listOf(413 to 384, 412 to 383, 414 to 385, 400 to 371, 405 to 376, 203 to 203, 208 to 208)
    val modes = listOf(
      HazePerformanceMode.Quality,
      HazePerformanceMode.Default,
      HazePerformanceMode.Performance,
      HazePerformanceMode.Fixed(0.75f),
    )
    for ((width, height) in sizes) for (radius in listOf(4, 80)) for (alpha in listOf(1f, 0.5f)) {
      for (mode in modes) {
        val case = EdgeCase(width, height, radius, alpha, mode)
        rule.runOnIdle { config.value = case }
        rule.waitForIdle()
        assertEdges(case)
      }
    }
  }

  @Test
  fun contentBlur_reversesSizeAndQualityChangesOnTheSameNode() {
    val config = mutableStateOf(EdgeCase(413, 384, 4, 1f, HazePerformanceMode.Quality))
    setContent(config, density = 1f)
    for (case in listOf(
      EdgeCase(413, 384, 4, 1f, HazePerformanceMode.Quality),
      EdgeCase(414, 385, 4, 1f, HazePerformanceMode.Default),
      EdgeCase(413, 384, 4, 1f, HazePerformanceMode.Quality),
    )) {
      rule.runOnIdle { config.value = case }
      rule.waitForIdle()
      assertEdges(case)
    }
    for (mode in listOf(HazePerformanceMode.Quality, HazePerformanceMode.Default, HazePerformanceMode.Quality)) {
      val case = EdgeCase(413, 384, 4, 1f, mode)
      rule.runOnIdle { config.value = case }
      rule.waitForIdle()
      assertEdges(case)
    }
  }

  @Test
  fun contentBlur_supportsOrdinaryDensityWithPixelDerivedSize() {
    val config = mutableStateOf(EdgeCase(413, 384, 4, 1f, HazePerformanceMode.Default))
    setContent(config, density = 2f)
    rule.waitForIdle()
    val pixels = capture()
    assertThat(pixels.width).isEqualTo(433)
    assertThat(pixels.height).isEqualTo(404)
    assertFlat(pixels[422, 200], 1f, "413x384 radius=4 density=2 right edge")
    assertFlat(pixels[200, 393], 1f, "413x384 radius=4 density=2 bottom edge")
  }

  private fun setContent(config: androidx.compose.runtime.State<EdgeCase>, density: Float) {
    rule.setContent { edgeContent(config.value, density) }
    rule.waitForIdle()
  }

  private fun assertEdges(case: EdgeCase) {
    val pixels = capture()
    assertThat(pixels.width).isEqualTo(case.width + 20)
    assertThat(pixels.height).isEqualTo(case.height + 20)
    for (x in 10 until case.width + 10) {
      assertFlat(pixels[x, case.height + 9], case.alpha, case.label("bottom x=$x"))
    }
    for (y in 10 until case.height + 10) {
      assertFlat(pixels[case.width + 9, y], case.alpha, case.label("right y=$y"))
    }
  }

  private fun capture() = rule.onNodeWithTag(ROOT).captureToImage().toPixelMap()

  @Composable
  private fun edgeContent(case: EdgeCase, density: Float) {
    CompositionLocalProvider(LocalDensity provides Density(density)) {
      Box(
        Modifier.size((case.width + 20).dp / density, (case.height + 20).dp / density)
          .background(Color.Magenta).testTag(ROOT),
      ) {
        Box(
          Modifier.offset(10.dp / density, 10.dp / density)
            .size(case.width.dp / density, case.height.dp / density)
            .hazeBlur(
              input = HazeInput.Content,
              expandLayerBounds = false,
              performanceMode = case.mode,
              style = HazeBlurStyle {
                blurRadius(case.radius.dp / density)
                noiseFactor(0f)
                backgroundColor(Color.Transparent)
                colorEffects(emptyList())
              },
            )
            .background(Color.White.copy(alpha = case.alpha)),
        )
      }
    }
  }

  private fun assertFlat(pixel: Color, whiteAlpha: Float, context: String) {
    val tolerance = 8f / 255f
    assertThat(pixel.red, "$context red").isCloseTo(1f, tolerance)
    assertThat(pixel.green, "$context green").isCloseTo(whiteAlpha, tolerance)
    assertThat(pixel.blue, "$context blue").isCloseTo(1f, tolerance)
    assertThat(pixel.alpha, "$context alpha").isCloseTo(1f, tolerance)
  }

  private data class EdgeCase(
    val width: Int,
    val height: Int,
    val radius: Int,
    val alpha: Float,
    val mode: HazePerformanceMode,
  ) {
    fun label(probe: String) = "$width x $height radius=$radius mode=$mode alpha=$alpha $probe"
  }

  private companion object {
    const val ROOT = "scaled_content_edge_root"
  }
}
