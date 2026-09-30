// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.chrisbanes.haze.HazeProgressive
import kotlin.test.Test

class ProgressiveBlurBoundsTest {
  @Test
  fun boundsRelativeGeometry_preservesFiniteLengthAndSampling() {
    val probes = listOf(
      Triple(Offset.Zero, Offset(Float.POSITIVE_INFINITY, 0f), 64f),
      Triple(Offset(Float.POSITIVE_INFINITY, 0f), Offset.Zero, 64f),
      Triple(Offset.Zero, Offset(0f, Float.POSITIVE_INFINITY), 64f),
      Triple(Offset(0f, Float.POSITIVE_INFINITY), Offset.Zero, 64f),
      Triple(Offset.Zero, Offset.Infinite, 90.50967f),
      Triple(Offset.Infinite, Offset.Zero, 90.50967f),
      Triple(Offset(Float.POSITIVE_INFINITY, 0f), Offset(0f, Float.POSITIVE_INFINITY), 90.50967f),
      Triple(Offset.Infinite, Offset.Infinite, 0f),
      Triple(Offset(64f, 0f), Offset(Float.POSITIVE_INFINITY, 0f), 0f),
    )
    probes.forEach { (start, end, length) ->
      assertThat(calculateLength(start, end, Size(64f, 64f))).isCloseTo(length, 0.0001f)
      assertThat(layers(gradient(start, end)).size).isEqualTo(3)
    }
    val finite = gradient(Offset(-64f, 0f), Offset(128f, 0f))
    assertThat(calculateLength(finite.start, finite.end, Size(64f, 64f))).isEqualTo(192f)
    assertThat(layers(finite).size).isEqualTo(4)
    assertThat(layers(gradient(Offset.Infinite, Offset.Zero), Size.Zero).size).isEqualTo(3)
  }

  @Test
  fun reverseGeometry_preservesIntensityAndEasingOrder() {
    val start = Offset(Float.POSITIVE_INFINITY, 0f)
    assertThat(layers(gradient(start, Offset.Zero, 0.25f, 0.75f)).map { it.second })
      .containsExactly(0.25f, 0.5f, 0.75f)
    assertThat(layers(gradient(start, Offset.Zero, 0.75f, 0.25f)).map { it.second })
      .containsExactly(0.25f, 0.5f, 0.75f)
    val nonlinear = HazeProgressive.LinearGradient(
      start = start,
      end = Offset.Zero,
      startIntensity = 0.75f,
      endIntensity = 0.25f,
      easing = Easing { it * it },
    )
    assertThat(layers(nonlinear).map { it.second }).containsExactly(0.25f, 0.625f, 0.75f)
  }

  @Test
  fun layeredMasks_preserveAuthoredEndpoints() {
    for ((start, end, finiteStart, finiteEnd) in listOf(
      listOf(Offset(Float.POSITIVE_INFINITY, 0f), Offset.Zero, Offset(64f, 0f), Offset.Zero),
      listOf(Offset(0f, Float.POSITIVE_INFINITY), Offset.Zero, Offset(0f, 64f), Offset.Zero),
      listOf(Offset.Infinite, Offset.Zero, Offset(64f, 64f), Offset.Zero),
      listOf(Offset(-64f, 0f), Offset(128f, 0f), Offset(-64f, 0f), Offset(128f, 0f)),
    )) {
      val masks = layers(gradient(start, end))
      val referenceMasks = layers(gradient(finiteStart, finiteEnd))
      masks.forEachIndexed { index, (mask, _) ->
        val actual = rasterize(mask)
        // Independently author the expected brush stops for the known 2/3-step fixtures.
        val steps = if (start.x == -64f) 3f else 2f
        val reference = rasterize(
          Brush.linearGradient(
            (index - 2f) / steps to Color.Transparent,
            (index - 1f) / steps to Color.Black,
            index / steps to Color.Black,
            (index + 1f) / steps to Color.Transparent,
            start = finiteStart,
            end = finiteEnd,
          ),
        )
        for (x in listOf(8, 16, 32, 48, 56)) {
          for (y in listOf(8, 16, 32, 48, 56)) {
            assertThat(actual[x, y].alpha).isCloseTo(reference[x, y].alpha, 1f / 255)
          }
        }
      }
      assertThat(masks.size).isEqualTo(referenceMasks.size)
    }
  }

  @Test
  fun invalidGeometry_failsBeforeDrawing() {
    for ((start, end) in listOf(
      Offset(Float.NaN, 0f) to Offset.Zero,
      Offset(Float.NEGATIVE_INFINITY, 0f) to Offset.Zero,
      Offset(-Float.MAX_VALUE, 0f) to Offset(Float.MAX_VALUE, 0f),
      Offset.Zero to Offset(Float.MAX_VALUE, 0f),
    )) {
      var callbacks = 0
      assertFailure {
        draw(gradient(start, end)) { _, _ ->
          callbacks++
          check(callbacks < 100)
        }
      }.isInstanceOf<IllegalArgumentException>()
      assertThat(callbacks).isEqualTo(0)
    }
  }

  @Test
  fun invalidArithmetic_failsBeforeDrawing() {
    val gradient = HazeProgressive.LinearGradient(start = Offset.Zero, end = Offset(64f, 0f))
    for (step in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.MIN_VALUE)) {
      var callbacks = 0
      assertFailure {
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(64, 64)), Size(64f, 64f)) {
          drawProgressiveWithMultipleLayers(gradient, step.dp) { _, _ ->
            callbacks++
            check(callbacks < 100) { "Abort runaway layered gradient" }
          }
        }
      }.isInstanceOf<IllegalArgumentException>()
      assertThat(callbacks).isEqualTo(0)
    }
  }

  @Test
  fun reverseInfiniteGradient_hasBoundedLayerCount() {
    var callbacks = 0
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(64, 64)), Size(64f, 64f)) {
      drawProgressiveWithMultipleLayers(
        HazeProgressive.LinearGradient(start = Offset(Float.POSITIVE_INFINITY, 0f), end = Offset.Zero, easing = LinearEasing),
      ) { _, _ ->
        callbacks++
        check(callbacks < 100) { "Abort runaway layered gradient" }
      }
    }
    assertThat(callbacks).isEqualTo(3)
  }
  private fun gradient(start: Offset, end: Offset, startIntensity: Float = 0f, endIntensity: Float = 1f) =
    HazeProgressive.LinearGradient(
      start = start,
      end = end,
      startIntensity = startIntensity,
      endIntensity = endIntensity,
      easing = LinearEasing,
    )

  private fun layers(progressive: HazeProgressive.LinearGradient, size: Size = Size(64f, 64f)): List<Pair<Brush, Float>> {
    val result = mutableListOf<Pair<Brush, Float>>()
    draw(progressive, size) { mask, intensity ->
      result += mask to intensity
      check(result.size < 100) { "Abort runaway layered gradient" }
    }
    return result
  }

  private fun draw(progressive: HazeProgressive.LinearGradient, size: Size = Size(64f, 64f), block: (Brush, Float) -> Unit) {
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(64, 64)), size) {
      drawProgressiveWithMultipleLayers(progressive, block = block)
    }
  }

  private fun rasterize(brush: Brush): PixelMap {
    val bitmap = ImageBitmap(64, 64)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(64f, 64f)) {
      drawRect(brush)
    }
    return bitmap.toPixelMap()
  }
}
