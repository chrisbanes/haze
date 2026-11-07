// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.createProgressiveBlurRenderEffect
import kotlin.test.Test

class ProgressiveBlurMaskTest {

  @Test
  fun layeredMasks_coverTheFullAxisIndependentOfIntensityRange() {
    val probes = listOf(
      Probe(
        name = "vertical equal",
        progressive = HazeProgressive.LinearGradient(
          easing = LinearEasing,
          start = Offset(0f, 0f),
          startIntensity = 0.5f,
          end = Offset(0f, 128f),
          endIntensity = 0.5f,
        ),
        expectedIntensities = listOf(0.5f, 0.5f, 0.5f),
        samples = listOf(64 to 8, 64 to 64, 64 to 120),
      ),
      Probe(
        name = "horizontal restricted increasing",
        progressive = HazeProgressive.LinearGradient(
          easing = LinearEasing,
          start = Offset(0f, 0f),
          startIntensity = 0.25f,
          end = Offset(128f, 0f),
          endIntensity = 0.75f,
        ),
        expectedIntensities = listOf(0.25f, 0.5f, 0.75f),
        samples = listOf(8 to 64, 64 to 64, 120 to 64),
      ),
      Probe(
        name = "vertical restricted decreasing",
        progressive = HazeProgressive.LinearGradient(
          easing = LinearEasing,
          start = Offset(0f, 0f),
          startIntensity = 0.75f,
          end = Offset(0f, 128f),
          endIntensity = 0.25f,
        ),
        expectedIntensities = listOf(0.25f, 0.5f, 0.75f),
        samples = listOf(64 to 8, 64 to 64, 64 to 120),
      ),
      Probe(
        name = "horizontal default linear",
        progressive = HazeProgressive.LinearGradient(
          easing = LinearEasing,
          start = Offset(0f, 0f),
          startIntensity = 0f,
          end = Offset(128f, 0f),
          endIntensity = 1f,
        ),
        expectedIntensities = listOf(0f, 0.5f, 1f),
        samples = listOf(8 to 64, 64 to 64, 120 to 64),
      ),
      Probe(
        name = "horizontal nonlinear easing",
        progressive = HazeProgressive.LinearGradient(
          easing = Easing { fraction -> fraction * fraction },
          start = Offset(0f, 0f),
          startIntensity = 0f,
          end = Offset(128f, 0f),
          endIntensity = 1f,
        ),
        expectedIntensities = listOf(0f, 0.25f, 1f),
        samples = listOf(8 to 64, 64 to 64, 120 to 64),
      ),
    )

    probes.forEach { probe ->
      val result = rasterize(probe.progressive)

      assertThat(result.intensities, probe.name).containsExactly(
        *probe.expectedIntensities.toTypedArray(),
      )
      probe.samples.forEach { (x, y) ->
        assertThat(result.pixels[x, y].alpha, "${probe.name} coverage at ($x, $y)")
          .isGreaterThan(0.95f)
      }
    }
  }

  @Test
  fun progressiveMaskOrigin_isIndependentOfCropOrigin() {
    val brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = 80f) as ShaderBrush
    val shader = brush.createShader(Size(100f, 80f))
    fun pixel(crop: Offset, maskOrigin: Offset): Float {
      val effect = createProgressiveBlurRenderEffect(8f, Size(95f, 80f), crop, shader, maskOrigin)
      val bitmap = ImageBitmap(100, 80)
      with(Canvas(bitmap)) {
        saveLayer(Rect(0f, 0f, 100f, 80f), Paint().apply { skiaPaint.imageFilter = effect })
        drawRect(Rect(0f, 0f, 100f, 80f), Paint().apply { color = Color.White })
        drawRect(Rect(40f, 0f, 41f, 80f), Paint().apply { color = Color.Black })
        restore()
      }
      return bitmap.toPixelMap()[40, 40].red
    }
    val original = pixel(Offset.Zero, Offset.Zero)
    val shiftedCrop = pixel(Offset(5f, 0f), Offset.Zero)
    val shiftedMask = pixel(Offset(5f, 0f), Offset(40f, 0f))
    assertThat(shiftedCrop, "crop change away from the sample").isCloseTo(original, 0.01f)
    assertThat(original - shiftedMask, "mask origin changes blur intensity").isGreaterThan(0.1f)
  }

  private fun rasterize(progressive: HazeProgressive.LinearGradient): RasterResult {
    val width = 128
    val height = 128
    val bitmap = ImageBitmap(width, height)
    val intensities = mutableListOf<Float>()

    CanvasDrawScope().draw(
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
      canvas = Canvas(bitmap),
      size = Size(width.toFloat(), height.toFloat()),
    ) {
      drawProgressiveWithMultipleLayers(progressive) { mask, intensity ->
        intensities += intensity
        drawRect(brush = mask)
      }
    }

    return RasterResult(intensities, bitmap.toPixelMap())
  }

  private class Probe(
    val name: String,
    val progressive: HazeProgressive.LinearGradient,
    val expectedIntensities: List<Float>,
    val samples: List<Pair<Int, Int>>,
  )

  private class RasterResult(
    val intensities: List<Float>,
    val pixels: androidx.compose.ui.graphics.PixelMap,
  )
}
