// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toPixelMap
import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.prop
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import kotlin.test.Test

/**
 * The Scrim fallback composites its tint with the tint's blend mode against the content behind
 * the effect, scaled by mask coverage and effect alpha, with no intermediate destination.
 */
class ScrimBlurBlendDestinationTest {

  @Test
  fun draw_maskedSolidMultiply_fullAlpha_blendsWithDestination() = assertMaskedMultiply(solidMultiply, alpha = 1f)

  @Test
  fun draw_maskedSolidMultiply_halfAlpha_blendsWithDestination() = assertMaskedMultiply(solidMultiply, alpha = 0.5f)

  @Test
  fun draw_maskedBrushMultiply_fullAlpha_blendsWithDestination() = assertMaskedMultiply(brushMultiply, alpha = 1f)

  @Test
  fun draw_maskedBrushMultiply_halfAlpha_blendsWithDestination() = assertMaskedMultiply(brushMultiply, alpha = 0.5f)

  @Test
  fun draw_unmaskedSolidMultiply_halfAlpha_blendsWithDestination() {
    val pixels = Harness(alpha = 0.5f, mask = null, tint = solidMultiply).draw()

    assertThat(pixels[10, 2]).isCloseTo(multiply(k = 0.5f), TOLERANCE)
    assertThat(pixels[10, 17]).isCloseTo(multiply(k = 0.5f), TOLERANCE)
  }

  @Test
  fun draw_unmaskedBrushMultiply_halfAlpha_blendsWithDestination() {
    val pixels = Harness(alpha = 0.5f, mask = null, tint = brushMultiply).draw()

    assertThat(pixels[10, 2]).isCloseTo(multiply(k = 0.5f), TOLERANCE)
    assertThat(pixels[10, 17]).isCloseTo(multiply(k = 0.5f), TOLERANCE)
  }

  @Test
  fun draw_uniformHalfMask_blendsWithHalfCoverage() {
    val pixels = Harness(alpha = 1f, mask = SolidColor(Color.Black.copy(alpha = 0.5f)), tint = solidMultiply).draw()

    assertThat(pixels[10, 2]).isCloseTo(multiply(k = 0.5f), TOLERANCE)
  }

  @Test
  fun draw_maskAndProgressive_composeInOrderAgainstDestination() {
    val pixels = Harness(
      alpha = 1f,
      mask = HARD_STOP_MASK,
      tint = solidMultiply,
      progressive = HazeProgressive.horizontalGradient(easing = LinearEasing, startIntensity = 0f, endIntensity = 1f),
    ).draw()

    for (x in 0 until SIZE) {
      assertThat(pixels[x, 17]).isCloseTo(DST, 1f / 255f)
      assertThat(pixels[x, 2]).isCloseTo(multiply(k = (x + 0.5f) / SIZE), 3f / 255f)
    }
  }

  @Test
  fun draw_blendModeChangeOnRetainedLayer_matchesAFreshDelegate() {
    val solidScreen = HazeColorEffect.tint(SRC, BlendMode.Screen)
    val reused = Harness(alpha = 0.5f, mask = HARD_STOP_MASK, tint = solidMultiply)
    val multiplied = reused.draw()
    reused.update(alpha = 0.5f, mask = HARD_STOP_MASK, tint = solidScreen)
    val screened = reused.draw()

    val fresh = Harness(alpha = 0.5f, mask = HARD_STOP_MASK, tint = solidScreen).draw()

    assertSamePixels(screened, fresh)
    assertThat(screened[10, 2]).isNotEqualTo(multiplied[10, 2])
  }

  @Test
  fun draw_srcOverTint_halfAlpha_lerpsTowardTint() {
    val tint = HazeColorEffect.tint(SRC)
    val masked = Harness(alpha = 0.5f, mask = HARD_STOP_MASK, tint = tint).draw()
    val unmasked = Harness(alpha = 0.5f, mask = null, tint = tint).draw()

    assertThat(masked[10, 2]).isCloseTo(lerp(DST, SRC, 0.5f), TOLERANCE)
    assertThat(masked[10, 17]).isCloseTo(DST, 1f / 255f)
    assertThat(unmasked[10, 2]).isCloseTo(lerp(DST, SRC, 0.5f), TOLERANCE)
  }

  @Test
  fun draw_colorFilter_halfAlpha_lerpsTowardFilteredColor() {
    val tint = HazeColorEffect.colorFilter(ColorFilter.tint(Color.Red.copy(alpha = 0.5f)))
    val masked = Harness(alpha = 0.5f, mask = HARD_STOP_MASK, tint = tint).draw()
    val unmasked = Harness(alpha = 0.5f, mask = null, tint = tint).draw()

    assertThat(masked[10, 2]).isCloseTo(lerp(DST, Color.Red, 0.25f), TOLERANCE)
    assertThat(masked[10, 17]).isCloseTo(DST, 1f / 255f)
    assertThat(unmasked[10, 2]).isCloseTo(lerp(DST, Color.Red, 0.25f), TOLERANCE)
  }

  @Test
  fun draw_maskedSrcTint_fullAlpha_keepsDestinationWhereMaskIsTransparent() {
    val pixels = Harness(alpha = 1f, mask = HARD_STOP_MASK, tint = HazeColorEffect.tint(SRC, BlendMode.Src)).draw()

    assertThat(pixels[10, 2]).isCloseTo(SRC, TOLERANCE)
    assertThat(pixels[10, 17]).isCloseTo(DST, 1f / 255f)
  }

  @Test
  fun draw_maskedSrcTint_halfAlpha_keepsDestinationWhereMaskIsTransparent() {
    val pixels = Harness(alpha = 0.5f, mask = HARD_STOP_MASK, tint = HazeColorEffect.tint(SRC, BlendMode.Src)).draw()

    assertThat(pixels[10, 2]).isCloseTo(lerp(DST, SRC, 0.5f), TOLERANCE)
    assertThat(pixels[10, 17]).isCloseTo(DST, 1f / 255f)
  }

  @Test
  fun draw_maskedDstInTint_keepsDestinationWhereMaskIsTransparent() {
    val pixels = Harness(alpha = 1f, mask = HARD_STOP_MASK, tint = HazeColorEffect.tint(SRC, BlendMode.DstIn)).draw()

    assertThat(pixels[10, 17]).isCloseTo(DST, 1f / 255f)
  }

  @Test
  fun draw_maskedSrcColorFilter_keepsDestinationWhereMaskIsTransparent() {
    val tint = HazeColorEffect.colorFilter(ColorFilter.tint(Color.Red.copy(alpha = 0.5f)), BlendMode.Src)
    val pixels = Harness(alpha = 1f, mask = HARD_STOP_MASK, tint = tint).draw()

    assertThat(pixels[10, 2]).isCloseTo(lerp(DST, Color.Red, 0.5f), TOLERANCE)
    assertThat(pixels[10, 17]).isCloseTo(DST, 1f / 255f)
  }

  @Test
  fun draw_unmaskedSrcTint_halfAlpha_fadesOverDestination() {
    val pixels = Harness(alpha = 0.5f, mask = null, tint = HazeColorEffect.tint(SRC, BlendMode.Src)).draw()

    assertThat(pixels[10, 2]).isCloseTo(lerp(DST, SRC, 0.5f), TOLERANCE)
    assertThat(pixels[10, 17]).isCloseTo(lerp(DST, SRC, 0.5f), TOLERANCE)
  }

  private fun assertMaskedMultiply(tint: HazeColorEffect, alpha: Float) {
    val pixels = Harness(alpha = alpha, mask = HARD_STOP_MASK, tint = tint).draw()

    assertThat(pixels[10, 2]).isCloseTo(multiply(k = alpha), TOLERANCE)
    assertThat(pixels[10, 17]).isCloseTo(DST, 1f / 255f)
  }

  private fun assertSamePixels(actual: PixelMap, expected: PixelMap) {
    for (y in 0 until expected.height) {
      for (x in 0 until expected.width) {
        assertThat(actual[x, y]).isEqualTo(expected[x, y])
      }
    }
  }

  private class Harness(alpha: Float, mask: Brush?, tint: HazeColorEffect, progressive: HazeProgressive? = null) {
    val effect = BlurVisualEffect()
    val delegate = ScrimBlurVisualEffectDelegate(effect)
    val context = RecordingDrawContext(recordedSize = Size(SIZE.toFloat(), SIZE.toFloat()))

    init {
      effect.delegate = delegate
      update(alpha, mask, tint, progressive)
    }

    fun update(alpha: Float, mask: Brush?, tint: HazeColorEffect, progressive: HazeProgressive? = null) {
      effect.update(
        BlurLifecycleScope,
        BlurConfiguration(
          HazeBlurStyle {
            alpha(alpha)
            mask(mask)
            fallbackColorEffect(tint)
            progressive(progressive)
          },
          HazePerformanceMode.Quality,
        ),
        HazeSampling.FullResolution,
      )
    }

    fun draw(): PixelMap {
      context.render {
        drawRect(DST)
        with(delegate) { draw(context) }
      }
      return checkNotNull(context.output).toPixelMap()
    }
  }

  private companion object {
    const val SIZE = 20
    const val TOLERANCE = 2f / 255f
    val DST = Color(0xFFCC9966)
    val SRC = Color(0xFF3366FF)

    // sRGB colours pack to 8 bits; compare against exact values in the same transfer function.
    val UNQUANTIZED = ColorSpaces.ExtendedSrgb
    val HARD_STOP_MASK = Brush.verticalGradient(
      0f to Color.Black,
      0.5f to Color.Black,
      0.5f to Color.Transparent,
      1f to Color.Transparent,
    )
    val solidMultiply = HazeColorEffect.tint(SRC, BlendMode.Multiply)

    // A shader brush of constant colour, so the expected value matches the solid tint.
    val brushMultiply = HazeColorEffect.tint(Brush.verticalGradient(listOf(SRC, SRC)), BlendMode.Multiply)

    /** Per-channel `SrcOver` at coverage `k`; Compose's `lerp(Color)` interpolates in Oklab instead. */
    fun lerp(dst: Color, src: Color, k: Float) = Color(
      red = dst.red + (src.red - dst.red) * k,
      green = dst.green + (src.green - dst.green) * k,
      blue = dst.blue + (src.blue - dst.blue) * k,
      colorSpace = UNQUANTIZED,
    )

    /** `dst·(1−k) + k·src·dst` per channel, the Multiply blend at coverage `k`. */
    fun multiply(k: Float) = Color(
      red = DST.red * (1 - k) + k * SRC.red * DST.red,
      green = DST.green * (1 - k) + k * SRC.green * DST.green,
      blue = DST.blue * (1 - k) + k * SRC.blue * DST.blue,
      colorSpace = UNQUANTIZED,
    )
  }
}

private fun Assert<Color>.isCloseTo(expected: Color, tolerance: Float) = all {
  prop(Color::red).isCloseTo(expected.red, tolerance)
  prop(Color::green).isCloseTo(expected.green, tolerance)
  prop(Color::blue).isCloseTo(expected.blue, tolerance)
  prop(Color::alpha).isCloseTo(expected.alpha, tolerance)
}
