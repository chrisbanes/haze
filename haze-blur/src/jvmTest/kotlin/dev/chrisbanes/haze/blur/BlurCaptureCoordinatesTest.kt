// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import kotlin.test.Test

class BlurCaptureCoordinatesTest {
  @Test
  fun quality_verticalMask_crossesAtAuthoredCoordinate() = assertCoordinates(HazePerformanceMode.Quality, tint = false)

  @Test
  fun quality_verticalTint_crossesAtAuthoredCoordinate() = assertCoordinates(HazePerformanceMode.Quality, tint = true)

  @Test
  fun performance_verticalMask_crossesAtAuthoredCoordinate() = assertCoordinates(HazePerformanceMode.Performance, tint = false)

  @Test
  fun performance_verticalTint_crossesAtAuthoredCoordinate() = assertCoordinates(HazePerformanceMode.Performance, tint = true)

  private fun assertCoordinates(mode: HazePerformanceMode, tint: Boolean) {
    val pixels = render(mode, tint)
    val first = if (tint) pixels[10, 8].red else pixels[10, 8].alpha
    val second = if (tint) pixels[10, 9].red else pixels[10, 9].alpha
    assertThat(first, "$mode tint=$tint at y=8.5").isCloseTo(0.75f, 0.025f)
    assertThat(crossing(first, second), "$mode tint=$tint crossing").isCloseTo(9f, 0.025f)
  }

  @Test
  fun horizontalMaskAndTint_crossAtAuthoredCoordinate() {
    for (mode in listOf(HazePerformanceMode.Quality, HazePerformanceMode.Default, HazePerformanceMode.Fixed(0.5f), HazePerformanceMode.Performance)) {
      for (tint in listOf(false, true)) {
        val pixels = render(mode, tint, horizontal = true)
        val first = if (tint) pixels[8, 10].red else pixels[8, 10].alpha
        val second = if (tint) pixels[9, 10].red else pixels[9, 10].alpha
        assertThat(first, "$mode horizontal tint=$tint at x=8.5").isCloseTo(0.75f, 0.025f)
        assertThat(crossing(first, second), "$mode horizontal tint=$tint crossing").isCloseTo(9f, 0.025f)
      }
    }
  }

  @Test
  fun captureGeometry_bothAxesAndModes_preserveAuthoredCrossing() {
    val geometries = listOf(
      Size(21f, 19f) to Offset.Zero,
      Size(21f, 21f) to Offset.Zero,
      Size(25f, 23f) to Offset(2f, 4f),
      Size(25.2f, 23.4f) to Offset(2.25f, 4.5f),
    )
    val modes = listOf(HazePerformanceMode.Quality, HazePerformanceMode.Default, HazePerformanceMode.Fixed(0.5f), HazePerformanceMode.Performance)
    for (mode in modes) for ((layerSize, offset) in geometries) for (horizontal in listOf(false, true)) for (tint in listOf(false, true)) {
      val pixels = render(mode, tint, horizontal, layerSize, offset, wide = true)
      fun sample(position: Int): Float {
        val pixel = if (horizontal) pixels[position, 10] else pixels[10, position]
        return if (tint) pixel.red else pixel.alpha
      }
      assertThat(crossing(sample(8), sample(9)), "$mode $layerSize $offset horizontal=$horizontal tint=$tint")
        .isCloseTo(9f, 0.025f)
    }
  }

  @Test
  fun relativeMask_fractionalCapture_resolvesAgainstLogicalSize() {
    val pixels = render(
      HazePerformanceMode.Performance,
      tint = false,
      layerSize = Size(25.2f, 23.4f),
      offset = Offset(2.25f, 4.5f),
      relative = true,
    )
    for (y in listOf(4, 9, 14)) {
      assertThat(pixels[10, y].alpha, "relative mask y=$y").isCloseTo(1f - (y + 0.5f) / 19f, 0.025f)
    }
  }

  @Test
  fun radialMask_fractionalCapture_matchesQuality() = assertNonlinearMask(
    Brush.radialGradient(listOf(Color.Black, Color.Transparent), center = Offset(10f, 9f), radius = 12f),
  )

  @Test
  fun sweepMask_fractionalCapture_matchesQualityAwayFromDiscontinuities() = assertNonlinearMask(
    Brush.sweepGradient(listOf(Color.Black, Color.Transparent, Color.Black), center = Offset(10f, 9f)),
  )

  private fun assertNonlinearMask(brush: Brush) {
    val quality = render(HazePerformanceMode.Quality, tint = false, layerSize = Size(25.2f, 23.4f), offset = Offset(2.25f, 4.5f), brush = brush)
    val performance = render(HazePerformanceMode.Performance, tint = false, layerSize = Size(25.2f, 23.4f), offset = Offset(2.25f, 4.5f), brush = brush)
    for ((x, y) in listOf(5 to 4, 15 to 4, 5 to 14, 15 to 14)) {
      assertThat(performance[x, y].alpha, "$brush at ($x,$y)").isCloseTo(quality[x, y].alpha, 0.025f)
    }
  }

  @Test
  fun progressiveIntensity_fractionalCapture_blursStripedInputMoreAtHighIntensity() {
    for (mode in listOf(HazePerformanceMode.Quality, HazePerformanceMode.Performance)) {
      val size = Size(80f, 80f)
      val base = RecordingDrawContext(recordedSize = size).also {
        it.layerSize = Size(84.2f, 84.4f)
        it.layerOffset = Offset(2.25f, 2.5f)
      }
      val graphics = base.requireGraphicsContext()
      val input = graphics.createGraphicsLayer()
      input.record(Density(1f), LayoutDirection.Ltr, IntSize(100, 100)) {
        drawRect(Color.White)
        for (x in 0 until 100 step 16) {
          drawRect(Color.Black, topLeft = Offset(x.toFloat(), 0f), size = Size(8f, 100f))
        }
      }
      val context = object : HazeEffectRuntimeDrawScope by base {
        override fun DrawScope.drawInput() = drawLayer(input)
      }
      val effect = BlurVisualEffect()
      effect.update(
        BlurLifecycleScope,
        BlurConfiguration(
          HazeBlurStyle {
            blurRadius(8.dp)
            noiseFactor(0f)
            backgroundColor(Color.Transparent)
            colorEffects(emptyList())
            progressive(HazeProgressive.verticalGradient(startY = 0f, endY = 80f, startIntensity = 1f, endIntensity = 0f))
          },
          mode,
        ),
        HazeSampling.FullResolution,
      )
      val delegate = RenderEffectBlurVisualEffectDelegate(effect)
      try {
        base.render { with(delegate) { draw(context) } }
        val pixels = checkNotNull(base.output).toPixelMap()
        assertThat(pixels[17, 8].red - pixels[17, 70].red, "$mode progressive intensity")
          .isGreaterThan(0.05f)
      } finally {
        delegate.detach()
        graphics.releaseGraphicsLayer(input)
      }
    }
  }

  @Test
  fun sameAllocationGeometryChanges_reusedDelegateMatchesFreshInBothDirections() {
    val size = Size(21f, 19f)
    val context = RecordingDrawContext(recordedSize = size)
    val effect = BlurVisualEffect()
    effect.update(
      BlurLifecycleScope,
      BlurConfiguration(
        HazeBlurStyle {
          blurRadius(0.dp)
          noiseFactor(0f)
          backgroundColor(Color.Transparent)
          colorEffects(emptyList())
          mask(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = 6f, endY = 12f))
        },
        HazePerformanceMode.Performance,
      ),
      HazeSampling.FullResolution,
    )
    val delegate = RenderEffectBlurVisualEffectDelegate(effect)
    val graphics = context.requireGraphicsContext()
    val input = graphics.createGraphicsLayer()
    input.record(Density(1f), LayoutDirection.Ltr, IntSize(40, 40)) { drawRect(Color.White) }
    val inputContext = object : HazeEffectRuntimeDrawScope by context {
      override fun DrawScope.drawInput() = drawLayer(input)
    }
    try {
      for (layerSize in listOf(size, Size(21.2f, 19.2f), size)) {
        context.layerSize = layerSize
        context.render { with(delegate) { draw(inputContext) } }
        val actual = checkNotNull(context.output).toPixelMap()
        val fresh = render(HazePerformanceMode.Performance, tint = false, layerSize = layerSize, wide = true)
        for (y in 0 until actual.height) for (x in 0 until actual.width) {
          assertThat(actual[x, y], "$layerSize pixel ($x,$y)").isEqualTo(fresh[x, y])
        }
      }
    } finally {
      delegate.detach()
      graphics.releaseGraphicsLayer(input)
    }
  }

  // Pixel centers are 8.5 and 9.5; interpolate the unaveraged samples at 0.5.
  private fun crossing(first: Float, second: Float): Float = 8.5f + (first - 0.5f) / (first - second)

  private fun render(
    mode: HazePerformanceMode,
    tint: Boolean,
    horizontal: Boolean = false,
    layerSize: Size = Size(21f, 19f),
    offset: Offset = Offset.Zero,
    wide: Boolean = false,
    relative: Boolean = false,
    brush: Brush? = null,
  ): PixelMap {
    val size = Size(21f, 19f)
    val base = RecordingDrawContext(recordedSize = size).also {
      it.layerSize = layerSize
      it.layerOffset = offset
    }
    val graphics = base.requireGraphicsContext()
    val input = graphics.createGraphicsLayer()
    input.record(Density(1f), LayoutDirection.Ltr, IntSize(40, 40)) { drawRect(Color.White) }
    val context = object : HazeEffectRuntimeDrawScope by base {
      override fun DrawScope.drawInput() = drawLayer(input)
    }
    val colors = if (tint) listOf(Color.White, Color.Black) else listOf(Color.Black, Color.Transparent)
    val start = if (relative) 0f else if (wide) 6f else 8f
    val end = if (relative) Float.POSITIVE_INFINITY else if (wide) 12f else 10f
    val gradient = brush ?: if (horizontal) {
      Brush.horizontalGradient(colors, startX = start, endX = end)
    } else {
      Brush.verticalGradient(colors, startY = start, endY = end)
    }
    val effect = BlurVisualEffect()
    effect.update(
      BlurLifecycleScope,
      BlurConfiguration(
        HazeBlurStyle {
          blurRadius(0.dp)
          noiseFactor(0f)
          backgroundColor(Color.Transparent)
          colorEffects(if (tint) listOf(HazeColorEffect.tint(gradient)) else emptyList())
          if (!tint) mask(gradient)
        },
        mode,
      ),
      HazeSampling.FullResolution,
    )
    val delegate = RenderEffectBlurVisualEffectDelegate(effect)
    try {
      base.render { with(delegate) { draw(context) } }
      return checkNotNull(base.output).toPixelMap()
    } finally {
      delegate.detach()
      graphics.releaseGraphicsLayer(input)
    }
  }
}
