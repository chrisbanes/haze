// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSupported
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * A masked Scrim tint applies its blend mode against the content behind the effect, at full and
 * partial effect alpha, and leaves that content untouched where the mask is transparent.
 */
class ScrimBlendDestinationTest : ScreenshotTest() {

  @Test
  fun scrimSolidTint_blendsWithDestination() = runScreenshotTest {
    assertBlendsWithDestination(HazeColorEffect.tint(SRC, BLEND_MODE), "solid")
  }

  @Test
  fun scrimBrushTint_blendsWithDestination() = runScreenshotTest {
    // A shader brush of constant colour, so the expected value matches the solid tint.
    assertBlendsWithDestination(
      HazeColorEffect.tint(Brush.verticalGradient(listOf(SRC, SRC)), BLEND_MODE),
      "brush",
    )
  }

  private fun ScreenshotUiTest.assertBlendsWithDestination(tint: HazeColorEffect, label: String) {
    fun style(alpha: Float) = HazeBlurStyle {
      blurEnabled(false)
      noiseFactor(0f)
      backgroundColor(Color.Transparent)
      colorEffects(emptyList())
      fallbackColorEffect(tint)
      mask(HARD_STOP_MASK)
      alpha(alpha)
    }

    var style by mutableStateOf(style(1f))
    setContent {
      val state = remember { HazeState() }
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(320.dp).hazeSource(state)) {
          drawRect(DST)
        }
        Box(
          Modifier
            .size(200.dp)
            .testTag(EFFECT_TAG)
            .hazeBlur(HazeInput.Sources(state), style = style, performanceMode = HazePerformanceMode.Balanced),
        )
      }
    }

    for (alpha in listOf(1f, 0.5f)) {
      style = style(alpha)
      waitForIdle()
      waitForIdle()
      val pixels = captureRootPixels().snapshot()
      val bounds = effectBounds()
      val name = "$label $BLEND_MODE alpha=$alpha"

      val opaque = pixels.crop(bounds.region(0.05f..0.40f)).mean()
      val transparent = pixels.crop(bounds.region(0.60f..0.95f)).mean()
      println("$name opaque=$opaque transparent=$transparent")

      assertChannelsClose(opaque, blend(k = alpha), 4f / 255f, "$name mask-opaque")
      assertChannelsClose(transparent, DST, 2f / 255f, "$name mask-transparent")
      // A SrcOver result would mean the blend never saw the destination.
      val srcOver = lerp(DST, SRC, alpha)
      assertThat(
        maxOf(abs(opaque.red - srcOver.red), abs(opaque.green - srcOver.green), abs(opaque.blue - srcOver.blue)),
        "$name differs from SrcOver",
      ).isGreaterThan(SENSITIVITY)
    }
  }

  private fun ScreenshotUiTest.effectBounds(): Rect =
    onNodeWithTag(EFFECT_TAG).fetchSemanticsNode().boundsInRoot.also {
      assertThat(it.width, "effect width").isGreaterThan(0f)
    }

  private fun Rect.region(y: ClosedFloatingPointRange<Float>) = IntRect(
    left = (left + width * 0.05f).roundToInt(),
    top = (top + height * y.start).roundToInt(),
    right = (left + width * 0.95f).roundToInt(),
    bottom = (top + height * y.endInclusive).roundToInt(),
  )

  private fun PixelSnapshot.mean(): Color {
    var r = 0f
    var g = 0f
    var b = 0f
    for (i in 0 until size) {
      val c = get(i)
      r += c.red
      g += c.green
      b += c.blue
    }
    return Color(red = r / size, green = g / size, blue = b / size)
  }

  private fun assertChannelsClose(actual: Color, expected: Color, tolerance: Float, name: String) {
    assertThat(actual.red, "$name red").isCloseTo(expected.red, tolerance)
    assertThat(actual.green, "$name green").isCloseTo(expected.green, tolerance)
    assertThat(actual.blue, "$name blue").isCloseTo(expected.blue, tolerance)
  }

  private companion object {
    const val EFFECT_TAG = "scrim-blend-effect"
    const val SENSITIVITY = 0.05f
    val DST = Color(0xFFCC9966)
    val SRC = Color(0xFF3366FF)
    val HARD_STOP_MASK = Brush.verticalGradient(
      0f to Color.Black,
      0.5f to Color.Black,
      0.5f to Color.Transparent,
      1f to Color.Transparent,
    )

    // Android before API 29 has no W3C Multiply and draws it as SrcOver; Screen is a Porter-Duff mode there.
    val BLEND_MODE = if (BlendMode.Multiply.isSupported()) BlendMode.Multiply else BlendMode.Screen

    /** `lerp(dst, B(src, dst), k)` per channel for [BLEND_MODE] at coverage `k`. */
    fun blend(k: Float): Color {
      fun channel(s: Float, d: Float) = d * (1 - k) + k * if (BLEND_MODE == BlendMode.Multiply) s * d else s + d - s * d
      return Color(
        red = channel(SRC.red, DST.red),
        green = channel(SRC.green, DST.green),
        blue = channel(SRC.blue, DST.blue),
      )
    }

    /** Per-channel `SrcOver` at coverage `k`; Compose's `lerp(Color)` interpolates in Oklab instead. */
    fun lerp(dst: Color, src: Color, k: Float) = Color(
      red = dst.red + (src.red - dst.red) * k,
      green = dst.green + (src.green - dst.green) * k,
      blue = dst.blue + (src.blue - dst.blue) * k,
    )
  }
}
