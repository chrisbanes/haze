// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.sqrt
import kotlin.test.Test
import org.robolectric.annotation.Config

/**
 * The API 31–32 layered linear progressive blur fades noise and colour filters with each band's
 * intensity, on both the RenderEffect route (box 0) and the Logical custom-brush route (box 1).
 * Every band has the same constant intensity, so an interior pixel is exactly one band's output.
 */
@Config(sdk = [32])
class LayeredProgressiveIntensityAndroidHostTest : ScreenshotTest() {
  @Test
  fun layeredBands_scaleColorFilterByIntensity_onBothRoutes() = render(Color.Red, noise = 0f, listOf(SWAP)) { full, half ->
    for (route in ROUTES.indices) {
      assertColor(full.center(route), Color.Blue, "${ROUTES[route]} intensity=1")
      assertColor(half.center(route), Color(0.5f, 0f, 0.5f), "${ROUTES[route]} intensity=0.5")
    }
  }

  @Test
  fun layeredBands_scaleNoiseByIntensity_onBothRoutes() = render(GREY, noise = 1f, emptyList()) { full, half ->
    for (route in ROUTES.indices) {
      val fullContrast = contrast(full, route)
      assertThat(fullContrast, "${ROUTES[route]} intensity=1 contrast").isGreaterThan(0.01f)
      assertThat(contrast(half, route) / fullContrast, "${ROUTES[route]} contrast ratio").isBetween(0.35f, 0.65f)
    }
  }

  /** Renders both routes at intensity 1, then at 0.5, and passes both frames to [verify]. */
  private fun render(
    content: Color,
    noise: Float,
    colorEffects: List<HazeColorEffect>,
    verify: (full: PixelMap, half: PixelMap) -> Unit,
  ) = runScreenshotTest {
    var intensity by mutableStateOf(1f)
    fun style(customMask: Boolean) = HazeBlurStyle {
      blurRadius(8.dp)
      noiseFactor(noise)
      backgroundColor(Color.Transparent)
      colorEffects(colorEffects)
      progressive(HazeProgressive.verticalGradient(startIntensity = intensity, endIntensity = intensity))
      if (customMask) mask(OPAQUE_CUSTOM_MASK)
    }
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Row(Modifier.background(Color.White)) {
          BlurBox(style(customMask = false), content)
          BlurBox(style(customMask = true), content)
        }
      }
    }
    val full = frame()
    intensity = 0.5f
    verify(full, frame())
  }

  private fun assertColor(actual: Color, expected: Color, label: String) {
    val tolerance = 4f / 255f
    assertThat(actual.red, "$label red").isCloseTo(expected.red, tolerance)
    assertThat(actual.green, "$label green").isCloseTo(expected.green, tolerance)
    assertThat(actual.blue, "$label blue").isCloseTo(expected.blue, tolerance)
    assertThat(actual.alpha, "$label alpha").isCloseTo(expected.alpha, tolerance)
  }
}

@Composable
private fun BlurBox(style: HazeBlurStyle, content: Color) {
  val state = rememberHazeState()
  Box(Modifier.size(SIZE.dp)) {
    Box(Modifier.matchParentSize().hazeSource(state).background(content))
    Box(Modifier.matchParentSize().hazeBlur(HazeInput.Sources(state), style, HazePerformanceMode.Quality))
  }
}

private fun PixelMap.center(index: Int): Color = this[index * SIZE + SIZE / 2, SIZE / 2]

private fun contrast(pixels: PixelMap, index: Int): Float {
  val values = (MARGIN until SIZE - MARGIN).flatMap { y ->
    (MARGIN until SIZE - MARGIN).map { x -> pixels[index * SIZE + x, y].luminance() }
  }
  val mean = values.average()
  return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size).toFloat()
}

private const val SIZE = 128
private const val MARGIN = 16
private val GREY = Color(0.5f, 0.5f, 0.5f)
private val ROUTES = listOf("RenderEffect", "Logical")

private val SWAP = HazeColorEffect.colorFilter(
  ColorFilter.colorMatrix(
    ColorMatrix(
      floatArrayOf(
        0f, 0f, 1f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        1f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
      ),
    ),
  ),
  BlendMode.SrcOver,
)

/** An opaque custom shader brush, so the API 31–32 Logical path draws the style. */
private val OPAQUE_CUSTOM_MASK = object : ShaderBrush() {
  override fun createShader(size: Size): Shader = LinearGradientShader(Offset.Zero, Offset(1f, 0f), listOf(Color.Black, Color.Black))
}
