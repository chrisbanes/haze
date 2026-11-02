// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazePerformanceMode.Fixed
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import kotlin.test.Test

class ScaledContentEdgeTest {
  @Test
  fun defaultMode_whiteContent_matchesUnblurredControlInsideRoundedOutputClip() {
    val dimensions = IntSize(413, 384)
    val size = Size(413f, 384f)
    val base = RecordingDrawContext(recordedSize = size)
    val graphics = base.requireGraphicsContext()
    val input = graphics.createGraphicsLayer()
    input.record(Density(1f), LayoutDirection.Ltr, dimensions) { drawRect(Color.White) }
    val context = object : HazeEffectRuntimeDrawScope by base {
      override fun DrawScope.drawInput() = drawLayer(input)
    }
    val effect = BlurVisualEffect()
    effect.update(
      BlurLifecycleScope,
      BlurConfiguration(
        HazeBlurStyle {
          blurRadius(4.dp)
          noiseFactor(0f)
          colorEffects(emptyList())
          backgroundColor(Color.Transparent)
        },
        HazePerformanceMode.Default,
      ),
      HazeSampling.FullResolution,
    )
    val delegate = RenderEffectBlurVisualEffectDelegate(effect)
    val clip = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(24f, 24f))) }
    try {
      base.render(size) {
        drawRect(Color.Magenta)
        clipPath(clip) { with(delegate) { draw(context) } }
      }
      val actual = checkNotNull(base.output).toPixelMap()
      val control = ImageBitmap(413, 384)
      androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(control), size) {
        drawRect(Color.Magenta)
        clipPath(clip) { drawRect(Color.White) }
      }
      val expected = control.toPixelMap()
      for (y in 0 until 384) for (x in 0 until 413) {
        val a = actual[x, y]
        val e = expected[x, y]
        val delta = maxOf(kotlin.math.abs(a.red - e.red), kotlin.math.abs(a.green - e.green), kotlin.math.abs(a.blue - e.blue), kotlin.math.abs(a.alpha - e.alpha))
        assertThat(delta, "rounded clip pixel=($x,$y)").isLessThan(0.02f)
      }
    } finally {
      delegate.detach()
      graphics.releaseGraphicsLayer(input)
    }
  }

  @Test
  fun defaultMode_uniformWhiteContent_preservesAllEdges() {
    assertEdges(HazePerformanceMode.Default)
  }

  @Test
  fun qualityMode_uniformWhiteContent_preservesAllEdges() {
    assertEdges(HazePerformanceMode.Quality)
  }

  @Test
  fun supportedModes_sizes_radiiAndAlpha_preserveFlatEdges() {
    val modes = listOf(HazePerformanceMode.Quality, HazePerformanceMode.Default, HazePerformanceMode.Performance, Fixed(0.75f))
    val sizes = listOf(IntSize(413, 384), IntSize(412, 383), IntSize(414, 385), IntSize(400, 371), IntSize(405, 376), IntSize(203, 203), IntSize(208, 208), IntSize(1, 1))
    for (mode in modes) for (size in sizes) for (radius in listOf(4f, 80f)) for (alpha in listOf(1f, 0.5f)) {
      assertEdges(mode, size, radius, alpha)
    }
  }

  @Test
  fun reusedDelegate_matchesFreshAfterGeometryModeAndSameAllocationChanges() {
    val sourceContext = RecordingDrawContext(recordedSize = Size(500f, 500f))
    val source = sourceContext.requireGraphicsContext().createGraphicsLayer()
    source.record(Density(1f), LayoutDirection.Ltr, IntSize(500, 500)) {
      drawRect(Color.White)
      for (x in 0 until 500 step 8) drawRect(if ((x / 4) % 2 == 0) Color.Black else Color.White, topLeft = Offset(x.toFloat(), 0f), size = Size(4f, 500f))
    }
    val reusedEffect = BlurVisualEffect()
    val reusedDelegate = RenderEffectBlurVisualEffectDelegate(reusedEffect)
    val reusedContext = RecordingDrawContext(recordedSize = Size(413f, 384f))
    fun render(effect: BlurVisualEffect, delegate: RenderEffectBlurVisualEffectDelegate, context: RecordingDrawContext, size: Size, mode: HazePerformanceMode) {
      context.layerSize = size
      effect.update(
        BlurLifecycleScope,
        BlurConfiguration(
          HazeBlurStyle {
            blurRadius(4.dp)
            noiseFactor(0f)
            colorEffects(emptyList())
            backgroundColor(Color.Transparent)
          },
          mode,
        ),
        HazeSampling.FullResolution,
      )
      val inputContext = object : HazeEffectRuntimeDrawScope by context {
        override fun DrawScope.drawInput() = drawLayer(source)
      }
      context.render(size) {
        drawRect(Color.Magenta)
        with(delegate) { draw(inputContext) }
      }
    }
    fun compare(size: Size, mode: HazePerformanceMode, label: String) {
      render(reusedEffect, reusedDelegate, reusedContext, size, mode)
      val freshEffect = BlurVisualEffect()
      val freshDelegate = RenderEffectBlurVisualEffectDelegate(freshEffect)
      val freshContext = RecordingDrawContext(recordedSize = size)
      try {
        render(freshEffect, freshDelegate, freshContext, size, mode)
        val actual = checkNotNull(reusedContext.output).toPixelMap()
        val expected = checkNotNull(freshContext.output).toPixelMap()
        for (y in 0 until actual.height) for (x in 0 until actual.width) {
          val a = actual[x, y]
          val e = expected[x, y]
          val delta = maxOf(kotlin.math.abs(a.red - e.red), kotlin.math.abs(a.green - e.green), kotlin.math.abs(a.blue - e.blue), kotlin.math.abs(a.alpha - e.alpha))
          assertThat(delta, "$label pixel=($x,$y)").isLessThan(0.02f)
        }
      } finally {
        freshDelegate.detach()
      }
    }
    try {
      compare(Size(413f, 384f), HazePerformanceMode.Quality, "initial")
      compare(Size(414f, 385f), HazePerformanceMode.Quality, "grow")
      compare(Size(413f, 384f), HazePerformanceMode.Quality, "shrink")
      compare(Size(413f, 384f), HazePerformanceMode.Default, "default-initial")
      compare(Size(414f, 385f), HazePerformanceMode.Default, "default-grow")
      compare(Size(413f, 384f), HazePerformanceMode.Default, "default-shrink")
      compare(Size(413f, 384f), HazePerformanceMode.Default, "quality-to-default")
      compare(Size(413f, 384f), HazePerformanceMode.Quality, "default-to-quality")
      compare(Size(413f, 384f), HazePerformanceMode.Default, "same-allocation-413")
      compare(Size(413.25f, 384f), HazePerformanceMode.Default, "same-allocation-413.25")
      compare(Size(413f, 384f), HazePerformanceMode.Default, "same-allocation-return")
    } finally {
      reusedDelegate.detach()
      sourceContext.requireGraphicsContext().releaseGraphicsLayer(source)
    }
  }

  @Test
  fun productionDelegate_expandedGradientTint_preservesSampleCoordinates() {
    val pixels = styledDraw(tint = true)
    assertThat(pixels[100, 100].red, "gradient tint red at x=100").isCloseTo(1f - 100.5f / 413f, 0.025f)
    assertThat(pixels[300, 100].blue, "gradient tint blue at x=300").isCloseTo(300.5f / 413f, 0.025f)
  }

  @Test
  fun productionDelegate_expandedRelativeMask_preservesAlphaCoordinates() {
    val pixels = styledDraw(relativeMask = true)
    for (y in listOf(80, 160, 240)) assertThat(pixels[200, y].alpha, "relative mask y=$y").isCloseTo(1f - (y + 0.5f) / 384f, 0.025f)
  }

  @Test
  fun productionDelegate_expandedAbsoluteMask_preservesAlphaCoordinates() {
    val pixels = styledDraw(absoluteMask = true)
    assertThat(pixels[200, 90].alpha, "absolute mask midpoint").isCloseTo(0.475f, 0.025f)
  }

  @Test
  fun productionDelegate_expandedProgressiveBlursMoreAtHighIntensity() {
    val pixels = styledDraw(progressive = true)
    assertThat(pixels[202, 40].alpha, "progressive high alpha").isCloseTo(1f, 0.025f)
    assertThat(pixels[202, 320].alpha, "progressive low alpha").isCloseTo(1f, 0.025f)
    assertThat(pixels[202, 40].red - pixels[202, 320].red, "high intensity softens black stripe").isGreaterThan(0.03f)
  }

  @Test
  fun productionDelegate_expandedProgressiveAndRelativeMask_preserveBothEffects() {
    val combined = styledDraw(progressive = true, relativeMask = true)
    val progressive = styledDraw(progressive = true)
    for (y in listOf(120, 240)) {
      assertThat(combined[200, y].alpha, "combined mask alpha y=$y").isCloseTo(1f - (y + 0.5f) / 384f, 0.025f)
      assertThat(combined[202, y].red, "combined preserves progressive color y=$y").isCloseTo(progressive[202, y].red, 0.025f)
    }
  }

  private fun styledDraw(
    tint: Boolean = false,
    relativeMask: Boolean = false,
    absoluteMask: Boolean = false,
    progressive: Boolean = false,
  ): androidx.compose.ui.graphics.PixelMap {
    val size = Size(413f, 384f)
    val expanded = Size(445f, 416f)
    val base = RecordingDrawContext(recordedSize = size).also {
      it.layerSize = expanded
      it.layerOffset = Offset(16f, 16f)
    }
    val graphics = base.requireGraphicsContext()
    val input = graphics.createGraphicsLayer()
    input.record(Density(1f), LayoutDirection.Ltr, IntSize(500, 500)) {
      drawRect(Color.White)
      for (x in 0 until 500 step 8) drawRect(Color.Black, topLeft = Offset(x.toFloat(), 0f), size = Size(4f, 500f))
    }
    val inputContext = object : HazeEffectRuntimeDrawScope by base {
      override fun DrawScope.drawInput() {
        translate(16f, 16f) { drawLayer(input) }
      }
    }
    fun draw(style: HazeBlurStyle): androidx.compose.ui.graphics.PixelMap {
      val effect = BlurVisualEffect()
      effect.update(BlurLifecycleScope, BlurConfiguration(style, HazePerformanceMode.Default), HazeSampling.FullResolution)
      val delegate = RenderEffectBlurVisualEffectDelegate(effect)
      try {
        base.render(size) { with(delegate) { draw(inputContext) } }
        return checkNotNull(base.output).toPixelMap()
      } finally {
        delegate.detach()
      }
    }
    val style = HazeBlurStyle {
      blurRadius(4.dp)
      noiseFactor(0f)
      backgroundColor(Color.Transparent)
      colorEffects(if (tint) listOf(HazeColorEffect.tint(Brush.horizontalGradient(listOf(Color.Red, Color.Blue)))) else emptyList())
      if (relativeMask) mask(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = 0f, endY = Float.POSITIVE_INFINITY))
      if (absoluteMask) mask(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = 80f, endY = 100f))
      if (progressive) progressive(HazeProgressive.verticalGradient(easing = LinearEasing, startY = 0f, startIntensity = 1f, endY = Float.POSITIVE_INFINITY, endIntensity = 0f))
    }
    return try {
      draw(style)
    } finally {
      graphics.releaseGraphicsLayer(input)
    }
  }

  private fun assertEdges(
    mode: HazePerformanceMode,
    dimensions: IntSize = IntSize(413, 384),
    radius: Float = 4f,
    alpha: Float = 1f,
  ) {
    val size = Size(dimensions.width.toFloat(), dimensions.height.toFloat())
    val base = RecordingDrawContext(recordedSize = size)
    val graphics = base.requireGraphicsContext()
    val input = graphics.createGraphicsLayer()
    input.record(Density(1f), LayoutDirection.Ltr, dimensions) { drawRect(Color.White.copy(alpha = alpha)) }
    val context = object : HazeEffectRuntimeDrawScope by base {
      override fun DrawScope.drawInput() = drawLayer(input)
    }
    val effect = BlurVisualEffect()
    effect.update(
      BlurLifecycleScope,
      BlurConfiguration(
        HazeBlurStyle {
          blurRadius(radius.dp)
          noiseFactor(0f)
          colorEffects(emptyList())
          backgroundColor(Color.Transparent)
        },
        mode,
      ),
      HazeSampling.FullResolution,
    )
    val delegate = RenderEffectBlurVisualEffectDelegate(effect)
    try {
      base.render {
        drawRect(Color.Magenta)
        with(delegate) { draw(context) }
      }
      val pixels = checkNotNull(base.output).toPixelMap()
      val expected = if (alpha == 1f) Color.White else Color(1f, 0.5f, 1f, 1f)
      val edgeCoords = buildSet {
        for (x in 0 until pixels.width) {
          add(x to 0)
          add(x to pixels.height - 1)
        }
        for (y in 0 until pixels.height) {
          add(0 to y)
          add(pixels.width - 1 to y)
        }
      }
      for ((x, y) in edgeCoords) {
        val actual = pixels[x, y]
        val deviation = maxOf(kotlin.math.abs(actual.red - expected.red), kotlin.math.abs(actual.green - expected.green), kotlin.math.abs(actual.blue - expected.blue), kotlin.math.abs(actual.alpha - 1f))
        assertThat(deviation, "$mode $dimensions radius=$radius alpha=$alpha edge=($x,$y)").isLessThan(0.02f)
      }
    } finally {
      delegate.detach()
      graphics.releaseGraphicsLayer(input)
    }
  }
}
