// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.PlatformContext
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.test.Test

class ColorFilterAlignmentRenderEffectTest {
  @Test
  fun identityFilter_preservesLayerCoordinatesAtReducedScales() {
    for (scale in scales) {
      val width = ceil(64f * scale).toInt()
      val reference = raster(scale, Offset.Zero, filtered = false)
      // The zero-offset result is the passing control before padded cases.
      for (offset in offsets) {
        val actual = raster(scale, offset, effects = listOf(identity))
        val probes = listOf(width / 4, width / 2 - 2, width / 2 + 2, width * 3 / 4)
        for (x in probes) {
          assertColor(actual[x, width / 4], reference[x, width / 4], "identity scale=$scale offset=$offset x=$x", 2f / 255f)
        }
        if (scale == 1f && offset == Offset(8f, 8f)) {
          assertColor(actual[36, 16], Color.Blue, "supplied reproduction (36,16)", 2f / 255f)
        }
        println("identity passing scale=$scale offset=$offset")
      }
    }
  }

  @Test
  fun maskedFilter_preservesShaderAndContentCoordinates() {
    for (scale in scales) {
      for (offset in offsets) {
        assertBlends(scale, offset, progressive = true, overallMask = false, modes = listOf(BlendMode.SrcOver, BlendMode.Src))
      }
    }
  }

  @Test
  fun blendedFilter_preservesDestinationAndOverallMask() {
    val modes = listOf(BlendMode.SrcOver, BlendMode.Src, BlendMode.Dst, BlendMode.Multiply, BlendMode.Screen)
    for (scale in scales) {
      for (offset in offsets) {
        for (progressive in listOf(false, true)) {
          for (overallMask in listOf(false, true)) {
            assertBlends(scale, offset, progressive, overallMask, modes)
          }
        }
        if (offset != Offset.Zero) {
          val width = ceil(64f * scale).toInt()
          val shiftedX = (offset.x * scale).roundToInt()
          val brush = raster(
            scale,
            offset,
            effects = listOf(HazeColorEffect.tint(Brush.horizontalGradient(listOf(Color.Green, Color.Yellow)))),
          )
          for (x in listOf(shiftedX + width / 4, shiftedX + width * 3 / 4)) {
            val red = ((x + 0.5f - shiftedX) / width).coerceIn(0f, 1f)
            assertColor(brush[x, width / 2], Color(red, 1f, 0f), "brush origin scale=$scale offset=$offset x=$x")
          }
        }
      }
    }
  }

  private fun assertBlends(
    scale: Float,
    offset: Offset,
    progressive: Boolean,
    overallMask: Boolean,
    modes: List<BlendMode>,
  ) {
    val width = ceil(64f * scale).toInt()
    val shiftedX = (offset.x * scale).roundToInt()
    val shiftedY = (offset.y * scale).roundToInt()
    val xProbes = listOf(1, width / 4, width / 2 - 2, width / 2 + 2, width * 3 / 4)
    val yProbes = if (overallMask) {
      listOf(shiftedY + width / 4, shiftedY + width * 3 / 4) + if (shiftedY > 1) listOf(1) else emptyList()
    } else {
      listOf(width / 2)
    }
    for (mode in modes) {
      val actual = raster(
        scale,
        offset,
        effects = listOf(HazeColorEffect.colorFilter(swapFilter, mode)),
        progressive = if (progressive) HazeProgressive.Brush(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black))) else null,
        mask = if (overallMask) Brush.verticalGradient(listOf(Color.Transparent, Color.Black)) else null,
      )
      for (y in yProbes) for (x in xProbes) {
        val a = if (progressive) ((x + 0.5f - shiftedX) / width).coerceIn(0f, 1f) else 1f
        val g = if (overallMask) ((y + 0.5f - shiftedY) / width).coerceIn(0f, 1f) else 1f
        val original = if (x < width / 2) Color.Red else Color.Blue
        val swapped = if (x < width / 2) Color.Blue else Color.Red
        val expected = when (mode) {
          BlendMode.Src -> swapped.copy(alpha = a * g)
          BlendMode.Dst -> original.copy(alpha = g)
          BlendMode.Multiply -> Color(original.red * (1f - a), 0f, original.blue * (1f - a), g)
          BlendMode.Screen -> Color(original.red + swapped.red * a, 0f, original.blue + swapped.blue * a, g)
          else -> Color(original.red * (1f - a) + swapped.red * a, 0f, original.blue * (1f - a) + swapped.blue * a, g)
        }
        assertColor(actual[x, y], expected, "scale=$scale offset=$offset progressive=$progressive overall=$overallMask mode=$mode ($x,$y)")
      }
    }
  }

  private fun assertColor(actual: Color, expected: Color, label: String, tolerance: Float = 3f / 255f) {
    assertThat(actual.alpha, "$label alpha").isCloseTo(expected.alpha, tolerance)
    if (expected.alpha > 0f) {
      assertThat(actual.red, "$label red").isCloseTo(expected.red, tolerance)
      assertThat(actual.green, "$label green").isCloseTo(expected.green, tolerance)
      assertThat(actual.blue, "$label blue").isCloseTo(expected.blue, tolerance)
    }
  }

  private fun raster(
    scale: Float,
    offset: Offset,
    effects: List<HazeColorEffect> = emptyList(),
    progressive: HazeProgressive? = null,
    mask: Brush? = null,
    filtered: Boolean = true,
  ): PixelMap {
    val width = ceil(64f * scale).toInt()
    val effect = if (filtered) {
      createRenderEffect(
        PlatformContext.INSTANCE,
        Density(1f),
        RenderEffectParams(
          blurRadius = 0.dp,
          noiseFactor = 0f,
          scale = scale,
          contentSize = Size(64f, 64f),
          contentOffset = offset,
          colorEffects = effects,
          colorEffectsAlphaModulate = 1f,
          mask = mask,
          progressive = progressive,
          retainInputWhenMasked = false,
          blurTileMode = TileMode.Clamp,
        ),
      )
    } else {
      null
    }
    return ImageBitmap(width, width).also { bitmap ->
      with(Canvas(bitmap)) {
        saveLayer(Rect(0f, 0f, width.toFloat(), width.toFloat()), Paint().apply { skiaPaint.imageFilter = effect })
        drawRect(Rect(0f, 0f, width / 2f, width.toFloat()), Paint().apply { color = Color.Red })
        drawRect(Rect(width / 2f, 0f, width.toFloat(), width.toFloat()), Paint().apply { color = Color.Blue })
        restore()
      }
    }.toPixelMap()
  }

  private companion object {
    val scales = listOf(1f, 0.75f, 0.5f)
    val offsets = listOf(Offset.Zero, Offset(8f, 8f), Offset(8.4f, 6.6f))
    val identity = HazeColorEffect.colorFilter(ColorFilter.colorMatrix(ColorMatrix()))
    val swapFilter = ColorFilter.colorMatrix(
      ColorMatrix(
        floatArrayOf(
          0f, 0f, 1f, 0f, 0f,
          0f, 1f, 0f, 0f, 0f,
          1f, 0f, 0f, 0f, 0f,
          0f, 0f, 0f, 1f, 0f,
        ),
      ),
    )
  }
}
