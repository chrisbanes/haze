// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.HazeProgressive
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
