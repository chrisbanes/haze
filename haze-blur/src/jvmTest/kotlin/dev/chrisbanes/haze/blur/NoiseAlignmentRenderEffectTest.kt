// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.PlatformContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.test.Test

class NoiseAlignmentRenderEffectTest {
  @Test
  fun noiseFade_matchesTintFadeInExpandedLayers() {
    for (scale in scales) {
      val width = ceil(64f * scale).toInt()
      for (vertical in listOf(true, false)) {
        val progressive = HazeProgressive.Brush(step(vertical))
        for (offset in offsets) {
          val reference = raster(scale, offset, progressive, noiseFactor = 0f)
          val noise = raster(scale, offset, progressive, noiseFactor = 1f)
          val tint = raster(
            scale,
            offset,
            progressive,
            noiseFactor = 0f,
            colorEffects = listOf(HazeColorEffect.tint(Color.Red)),
          )

          val label = "scale=$scale offset=$offset vertical=$vertical"
          val noiseStart = firstDifferingLine(noise, reference, vertical)
          val tintStart = firstDifferingLine(tint, reference, vertical)
          val expected = (if (vertical) offset.y else offset.x).let { (it * scale).roundToInt() } + width / 2

          assertThat(noiseStart, "$label noise start").isBetween(expected - 1, expected + 1)
          assertThat(tintStart, "$label tint start").isBetween(expected - 1, expected + 1)
          assertThat(noiseStart - tintStart, "$label noise minus tint").isBetween(-1, 1)
          // Noise must still be present at the far edge of the layer, not just shifted away.
          assertThat(differingLines(noise, reference, vertical).size, "$label noisy lines").isGreaterThan(width / 4)
        }
      }
    }
  }

  private fun step(vertical: Boolean): Brush {
    val stops = arrayOf(0f to Color.Transparent, 0.5f to Color.Transparent, 0.5f to Color.Black, 1f to Color.Black)
    return if (vertical) Brush.verticalGradient(*stops) else Brush.horizontalGradient(*stops)
  }

  private fun differingLines(actual: PixelMap, reference: PixelMap, vertical: Boolean): List<Int> {
    val lines = (if (vertical) actual.height else actual.width)
    return (0 until lines).filter { line ->
      (0 until (if (vertical) actual.width else actual.height)).any { across ->
        val x = if (vertical) across else line
        val y = if (vertical) line else across
        differs(actual[x, y], reference[x, y])
      }
    }
  }

  private fun firstDifferingLine(actual: PixelMap, reference: PixelMap, vertical: Boolean): Int = differingLines(actual, reference, vertical).firstOrNull() ?: -1

  private fun differs(a: Color, b: Color): Boolean = abs(a.red - b.red) > TOLERANCE ||
    abs(a.green - b.green) > TOLERANCE ||
    abs(a.blue - b.blue) > TOLERANCE

  private fun raster(
    scale: Float,
    offset: Offset,
    progressive: HazeProgressive,
    noiseFactor: Float,
    colorEffects: List<HazeColorEffect> = emptyList(),
  ): PixelMap {
    val width = ceil(64f * scale).toInt()
    val effect = createRenderEffect(
      PlatformContext.INSTANCE,
      Density(1f),
      RenderEffectParams(
        blurRadius = 0.dp,
        noiseFactor = noiseFactor,
        scale = scale,
        contentSize = Size(64f, 64f),
        contentOffset = offset,
        colorEffects = colorEffects,
        colorEffectsAlphaModulate = 1f,
        mask = null,
        progressive = progressive,
        retainInputWhenMasked = false,
        blurTileMode = TileMode.Clamp,
      ),
    )
    return ImageBitmap(width, width).also { bitmap ->
      with(Canvas(bitmap)) {
        saveLayer(Rect(0f, 0f, width.toFloat(), width.toFloat()), Paint().apply { skiaPaint.imageFilter = effect })
        // Soft-light noise leaves 0 and 1 channels unchanged, so use mid-grey content.
        drawRect(Rect(0f, 0f, width.toFloat(), width.toFloat()), Paint().apply { color = Color(0.5f, 0.5f, 0.5f) })
        restore()
      }
    }.toPixelMap()
  }

  private companion object {
    const val TOLERANCE = 2f / 255f
    val scales = listOf(1f, 0.75f, 0.5f)
    val offsets = listOf(Offset.Zero, Offset(8f, 8f), Offset(8.4f, 6.6f))
  }
}
