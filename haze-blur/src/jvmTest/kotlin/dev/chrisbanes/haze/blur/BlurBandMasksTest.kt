// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeProgressive
import kotlin.test.Test

class BlurBandMasksTest {
  private val gradient = HazeProgressive.verticalGradient()

  @Test
  fun unchangedGeometry_returnsTheSameMasks() {
    val masks = BandMasks()

    val first = collect(masks, gradient, Size(64f, 130f))
    val second = collect(masks, gradient, Size(64f, 130f))

    assertThat(first).hasSize(4)
    first.zip(second).forEach { (a, b) -> assertThat(b).isSameInstanceAs(a) }
    assertThat(masks.buildCount).isEqualTo(first.size)
  }

  @Test
  fun intensityOrEasingChange_keepsTheMasks() {
    val masks = BandMasks()
    val first = collect(masks, gradient, Size(64f, 130f))

    val second = collect(
      masks,
      gradient.copy(startIntensity = 0.3f, endIntensity = 0.6f, easing = LinearEasing),
      Size(64f, 130f),
    )

    first.zip(second).forEach { (a, b) -> assertThat(b).isSameInstanceAs(a) }
    assertThat(masks.buildCount).isEqualTo(first.size)
  }

  @Test
  fun sizeChangeKeepingTheStepCount_keepsTheMasks() {
    val masks = BandMasks()
    val first = collect(masks, gradient, Size(64f, 130f))

    val second = collect(masks, gradient, Size(64f, 190f))

    first.zip(second).forEach { (a, b) -> assertThat(b).isSameInstanceAs(a) }
    assertThat(masks.buildCount).isEqualTo(first.size)
  }

  @Test
  fun sizeChangeAlteringTheStepCount_rebuildsTheMasks() {
    val masks = BandMasks()
    val first = collect(masks, gradient, Size(64f, 130f))

    val second = collect(masks, gradient, Size(64f, 260f))

    assertThat(second).hasSize(6)
    assertThat(second.first()).isNotSameInstanceAs(first.first())
    assertThat(masks.buildCount).isEqualTo(first.size + second.size)
  }

  @Test
  fun densityChange_rebuildsWhenItChangesTheStepCount() {
    val masks = BandMasks()
    val first = collect(masks, gradient, Size(64f, 190f), Density(1f))

    // 190px is three 64px steps at density 1 and two 128px steps at density 2.
    val second = collect(masks, gradient, Size(64f, 190f), Density(2f))

    assertThat(first).hasSize(4)
    assertThat(second).hasSize(3)
    assertThat(masks.buildCount).isEqualTo(first.size + second.size)
  }

  @Test
  fun startOrEndChange_rebuildsTheMasks() {
    val masks = BandMasks()
    val first = collect(masks, gradient, Size(64f, 130f))

    val movedStart = collect(masks, HazeProgressive.verticalGradient(startY = 10f), Size(64f, 130f))
    val movedEnd = collect(masks, HazeProgressive.verticalGradient(startY = 10f, endY = 120f), Size(64f, 130f))

    assertThat(movedStart.first()).isNotSameInstanceAs(first.first())
    assertThat(movedEnd.first()).isNotSameInstanceAs(movedStart.first())
    assertThat(masks.buildCount).isEqualTo(first.size + movedStart.size + movedEnd.size)
  }

  @Test
  fun clear_dropsTheMasks() {
    val masks = BandMasks()
    val first = collect(masks, gradient, Size(64f, 130f))
    assertThat(masks.isEmpty).isFalse()

    masks.clear()

    assertThat(masks.isEmpty).isTrue()
    val second = collect(masks, gradient, Size(64f, 130f))
    assertThat(second.first()).isNotSameInstanceAs(first.first())
    assertThat(masks.buildCount).isEqualTo(first.size + second.size)
  }

  @Test
  fun cachedMasks_equalUncachedMasks() {
    val reversed = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
    for (progressive in listOf(gradient, reversed)) {
      val cached = collect(BandMasks(), progressive, Size(64f, 130f))
      val uncached = collect(null, progressive, Size(64f, 130f))

      assertThat(cached).hasSize(uncached.size)
      cached.zip(uncached).forEach { (a, b) ->
        assertThat(a).isEqualTo(b)
        assertThat(rasterize(a)).isEqualTo(rasterize(b))
      }
    }
  }

  private fun collect(
    masks: BandMasks?,
    progressive: HazeProgressive.LinearGradient,
    size: Size,
    density: Density = Density(1f),
  ): List<Brush> {
    val result = mutableListOf<Brush>()
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(ImageBitmap(64, 64)), size) {
      drawProgressiveWithMultipleLayers(progressive, masks = masks) { mask, _ -> result += mask }
    }
    return result
  }

  private fun rasterize(brush: Brush): List<Int> {
    val bitmap = ImageBitmap(64, 64)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(64f, 64f)) {
      drawRect(brush)
    }
    val pixels = bitmap.toPixelMap()
    return List(64 * 64) { pixels[it % 64, it / 64].hashCode() }
  }
}
