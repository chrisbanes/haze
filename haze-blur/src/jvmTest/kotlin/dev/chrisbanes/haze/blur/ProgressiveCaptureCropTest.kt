// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asSkiaBitmap
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
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.createProgressiveBlurRenderEffect
import dev.chrisbanes.haze.drawScaledContent
import dev.chrisbanes.haze.scaledContentScale
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.test.Test

class ProgressiveCaptureCropTest {
  @Test
  fun roundedDownCapture_matchesValidCropOnBothAxes() = checkCapture(203)

  @Test
  fun exactCapture_matchesValidCropOnBothAxes() = checkCapture(204)

  @Test
  fun nonSquareFixed_matchesContinuousReference() = checkGeometry(Size(203f, 205f), Size(203f, 205f), Offset.Zero, HazePerformanceMode.Fixed(5f / 12f))

  @Test
  fun transposedFixed_matchesContinuousReference() = checkGeometry(Size(205f, 203f), Size(205f, 203f), Offset.Zero, HazePerformanceMode.Fixed(5f / 12f))

  @Test
  fun default_matchesContinuousReference() = checkGeometry(Size(203f, 205f), Size(203f, 205f), Offset.Zero, HazePerformanceMode.Default)

  @Test
  fun performance_matchesContinuousReference() = checkGeometry(Size(205f, 203f), Size(205f, 203f), Offset.Zero, HazePerformanceMode.Performance)

  @Test
  fun quality_matchesContinuousReference() = checkGeometry(Size(203f, 205f), Size(203f, 205f), Offset.Zero, HazePerformanceMode.Quality)

  @Test
  fun expandedHorizontalMask_matchesContinuousReference() = checkGeometry(Size(21f, 19f), Size(25f, 23f), Offset(2f, 4f), HazePerformanceMode.Performance)

  @Test
  fun fractionalVerticalMask_matchesContinuousReference() = checkGeometry(Size(21f, 19f), Size(25.2f, 23.4f), Offset(2.25f, 4.5f), HazePerformanceMode.Performance, vertical = true)

  @Test
  fun clippedHorizontalMask_preservesAuthoredOrigin() = checkGeometry(Size(21f, 19f), Size(25.2f, 23.4f), Offset(-5.25f, -4.5f), HazePerformanceMode.Performance)

  @Test
  fun clippedVerticalMask_preservesAuthoredOrigin() = checkGeometry(Size(21f, 19f), Size(25.2f, 23.4f), Offset(-5.25f, -4.5f), HazePerformanceMode.Performance, vertical = true)

  private fun checkGeometry(content: Size, allocationLayer: Size, origin: Offset, mode: HazePerformanceMode, vertical: Boolean = false) {
    val base = RecordingDrawContext(recordedSize = content)
    base.layerSize = allocationLayer
    base.layerOffset = origin
    val graphics = base.requireGraphicsContext()
    val input = graphics.createGraphicsLayer()
    input.record(Density(1f), LayoutDirection.Ltr, IntSize(260, 260)) {
      drawRect(Color.White)
      for (x in 0 until 260 step 4) drawRect(Color.Black, Offset(x.toFloat(), 0f), Size(2f, 260f))
      for (y in 0 until 260 step 7) drawRect(Color.Red, Offset(0f, y.toFloat()), Size(260f, 2f))
    }
    val context = object : HazeEffectRuntimeDrawScope by base {
      override fun DrawScope.drawInput() = drawLayer(input)
    }
    val brush = if (vertical) Brush.verticalGradient(listOf(Color.Black, Color.Transparent), endY = content.height) else Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), endX = content.width)
    // Exercise the arbitrary ShaderBrush path as well as the built-in gradient coordinates.
    val custom = object : ShaderBrush() {
      override fun createShader(size: Size) = (brush as ShaderBrush).createShader(size)
    }
    val effect = BlurVisualEffect()
    effect.update(
      BlurLifecycleScope,
      BlurConfiguration(
        HazeBlurStyle {
          blurRadius(8.dp)
          noiseFactor(0f)
          colorEffects(emptyList())
          backgroundColor(Color.Transparent)
          progressive(HazeProgressive.Brush(custom))
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
      val output = checkNotNull(base.output)
      val artifact = java.io.File("build/outputs/1728-crop/skiko-${content.width}-${allocationLayer.width}-${origin.x}-$vertical-${effect.resolveInputScaleFactor()}.png")
      artifact.parentFile.mkdirs()
      org.jetbrains.skia.Image.makeFromBitmap(output.asSkiaBitmap()).use { image ->
        artifact.writeBytes(checkNotNull(image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)).bytes)
      }
      val actual = output.toPixelMap()
      val layer = checkNotNull(delegate.scaledContentLayer)
      val nominal = effect.resolveInputScaleFactor()
      val a = scaledContentScale(allocationLayer, nominal)
      val authored = Offset(origin.x * a.scaleX, origin.y * a.scaleY)
      val start = Offset(maxOf(0f, authored.x), maxOf(0f, authored.y))
      val end = Offset(minOf(layer.size.width.toFloat(), (origin.x + content.width) * a.scaleX), minOf(layer.size.height.toFloat(), (origin.y + content.height) * a.scaleY))
      fun reference(maskOrigin: Offset): PixelMap {
        layer.renderEffect = createProgressiveBlurRenderEffect(8f * nominal, Size(end.x - start.x, end.y - start.y), start, custom.createScaledShader(content, a, Size(layer.size.width.toFloat(), layer.size.height.toFloat()), Offset.Zero), maskOrigin, edgeRepeatPx = if (nominal < 1f) 1f else 0f).asComposeRenderEffect()
        base.render {
          drawRect(Color.Magenta)
          drawScaledContent(-origin, a, clip = layer.clip) { drawLayer(layer) }
        }
        return checkNotNull(base.output).toPixelMap()
      }
      val expected = reference(authored)
      val residual = pixelResidual(actual, expected)
      println("crop content=$content layer=$allocationLayer origin=$origin mode=$mode vertical=$vertical residual=$residual")
      assertThat(residual).isLessThan(0.02f)
      if (origin.x < 0f) {
        val wrongOrigin = pixelResidual(actual, reference(start))
        println("relocated mask fault=$wrongOrigin")
        assertThat(wrongOrigin).isGreaterThan(0.02f)
      }
    } finally {
      delegate.detach()
      graphics.releaseGraphicsLayer(input)
    }
  }

  private fun pixelResidual(a: PixelMap, b: PixelMap): Float {
    var maximum = 0f
    for (y in 0 until a.height) for (x in 0 until a.width) {
      maximum = maxOf(maximum, kotlin.math.abs(a[x, y].red - b[x, y].red), kotlin.math.abs(a[x, y].green - b[x, y].green), kotlin.math.abs(a[x, y].blue - b[x, y].blue))
    }
    return maximum
  }

  @Test
  fun sameAllocationProgressive_reusedMatchesFreshInBothDirections() {
    val size = Size(21f, 19f)
    val base = RecordingDrawContext(recordedSize = size)
    val graphics = base.requireGraphicsContext()
    val input = graphics.createGraphicsLayer()
    input.record(Density(1f), LayoutDirection.Ltr, IntSize(40, 40)) {
      drawRect(Color.White)
      for (x in 0 until 40 step 4) drawRect(Color.Black, Offset(x.toFloat(), 0f), Size(2f, 40f))
    }
    val context = object : HazeEffectRuntimeDrawScope by base {
      override fun DrawScope.drawInput() = drawLayer(input)
    }
    fun effect() = BlurVisualEffect(renderEffectSlotFactory = { CountingBlurRenderEffectSlot() }).also {
      it.update(
        BlurLifecycleScope,
        BlurConfiguration(
          HazeBlurStyle {
            blurRadius(8.dp)
            noiseFactor(0f)
            colorEffects(emptyList())
            backgroundColor(Color.Transparent)
            progressive(HazeProgressive.verticalGradient(startY = 0f, endY = 19f, startIntensity = 1f, endIntensity = 0f))
          },
          HazePerformanceMode.Performance,
        ),
        HazeSampling.FullResolution,
      )
    }
    val blur = effect()
    val reused = RenderEffectBlurVisualEffectDelegate(blur)
    val counts = reused.effectSlot as CountingBlurRenderEffectSlot
    try {
      var previous: dev.chrisbanes.haze.PlatformRenderEffect? = null
      for (dimensions in listOf(size, Size(21.2f, 19.2f), size)) {
        base.layerSize = dimensions
        base.render { with(reused) { draw(context) } }
        val actual = checkNotNull(base.output).toPixelMap()
        assertThat(checkNotNull(reused.scaledContentLayer).size).isEqualTo(IntSize(11, 10))
        val graph = counts.platformEffect
        if (previous != null) assertThat(graph).isNotSameInstanceAs(previous)
        previous = graph
        val wraps = counts.wraps
        val assigns = counts.assigns
        base.render { with(reused) { draw(context) } }
        assertThat(counts.platformEffect).isSameInstanceAs(graph)
        assertThat(counts.wraps).isEqualTo(wraps)
        assertThat(counts.assigns).isEqualTo(assigns)
        val fresh = RenderEffectBlurVisualEffectDelegate(effect())
        try {
          base.render { with(fresh) { draw(context) } }
          assertThat(pixelResidual(actual, checkNotNull(base.output).toPixelMap())).isLessThan(0.00001f)
        } finally {
          fresh.detach()
        }
      }
    } finally {
      reused.detach()
      graphics.releaseGraphicsLayer(input)
    }
  }

  @Test
  fun emptyIntersection_releasesRetainedOutputAndDrawsNothing() {
    val base = RecordingDrawContext(recordedSize = Size(21f, 19f))
    val effect = BlurVisualEffect()
    effect.update(
      BlurLifecycleScope,
      BlurConfiguration(
        HazeBlurStyle {
          blurRadius(8.dp)
          noiseFactor(0f)
          colorEffects(emptyList())
          progressive(HazeProgressive.Brush(SolidColor(Color.Black)))
        },
        HazePerformanceMode.Performance,
      ),
      HazeSampling.FullResolution,
    )
    val delegate = RenderEffectBlurVisualEffectDelegate(effect)
    try {
      base.render { with(delegate) { draw(base) } }
      assertThat(delegate.canDrawRetainedOutput()).isTrue()
      val retained = delegate.scaledContentLayer
      base.layerSize = Size.Zero
      base.render {
        drawRect(Color.Magenta)
        with(delegate) { draw(base) }
      }
      assertThat(delegate.scaledContentLayer).isSameInstanceAs(retained)
      assertThat(delegate.canDrawRetainedOutput()).isFalse()
      base.layerSize = Size(21f, 19f)
      base.render { with(delegate) { draw(base) } }
      assertThat(delegate.canDrawRetainedOutput()).isTrue()
      base.layerOffset = Offset(30f, 0f)
      base.render {
        drawRect(Color.Magenta)
        with(delegate) { draw(base) }
      }
      assertThat(delegate.scaledContentLayer).isNull()
      assertThat(delegate.canDrawRetainedOutput()).isFalse()
      assertThat(delegate.effectSlot.platformEffect).isNull()
      val pixels = checkNotNull(base.output).toPixelMap()
      for (y in 0 until pixels.height) for (x in 0 until pixels.width) assertThat(pixels[x, y]).isEqualTo(Color.Magenta)
      base.layerOffset = Offset.Zero
      base.render { with(delegate) { draw(base) } }
      assertThat(delegate.canDrawRetainedOutput()).isTrue()
    } finally {
      delegate.detach()
    }
  }

  private fun checkCapture(dimension: Int) {
    run {
      val size = Size(dimension.toFloat(), dimension.toFloat())
      val base = RecordingDrawContext(recordedSize = size)
      val graphics = base.requireGraphicsContext()
      val input = graphics.createGraphicsLayer()
      input.record(Density(1f), LayoutDirection.Ltr, IntSize(dimension, dimension)) { drawRect(Color.Red) }
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
            colorEffects(emptyList())
            backgroundColor(Color.Transparent)
            progressive(HazeProgressive.Brush(SolidColor(Color.Black)))
          },
          HazePerformanceMode.Fixed(5f / 12f),
        ),
        HazeSampling.FullResolution,
      )
      val delegate = RenderEffectBlurVisualEffectDelegate(effect)
      try {
        base.render {
          drawRect(Color.Magenta)
          with(delegate) { draw(context) }
        }
        val actual = edges(checkNotNull(base.output).toPixelMap())
        val layer = checkNotNull(delegate.scaledContentLayer)
        val allocation = (dimension * SCALE).roundToInt().toFloat()
        val mask = (Brush.linearGradient(listOf(Color.Black, Color.Black)) as ShaderBrush).createShader(size)
        fun graph(crop: Float): Edges {
          layer.renderEffect = createProgressiveBlurRenderEffect(
            blurRadiusPx = 8f * SCALE,
            size = Size(crop, crop),
            offset = Offset.Zero,
            mask = mask,
            maskOrigin = Offset.Zero,
            edgeRepeatPx = if (SCALE < 1f) 1f else 0f,
          ).asComposeRenderEffect()
          base.render {
            drawRect(Color.Magenta)
            drawScaledContent(Offset.Zero, scaledContentScale(size, SCALE), clip = layer.clip) {
              drawLayer(layer)
            }
          }
          return edges(checkNotNull(base.output).toPixelMap())
        }
        val reference = graph(allocation)
        val nominalCeil = graph(ceil(dimension * SCALE))
        val plain = plainEdges(size)
        println("dimension=$dimension actual=$actual reference=$reference nominalCeil=$nominalCeil plain=$plain")
        assertThat(plain.right, "$dimension plain right").isLessThan(0.02f)
        assertThat(plain.bottom, "$dimension plain bottom").isLessThan(0.02f)
        if (dimension == 203) {
          assertThat(nominalCeil.right - nominalCeil.left, "old crop right excess").isGreaterThan(0.05f)
          assertThat(nominalCeil.bottom - nominalCeil.top, "old crop bottom excess").isGreaterThan(0.05f)
        } else {
          assertThat(nominalCeil.right, "exact allocation right control").isCloseTo(reference.right, 0.02f)
          assertThat(nominalCeil.bottom, "exact allocation bottom control").isCloseTo(reference.bottom, 0.02f)
        }
        assertThat(actual.right, "$dimension right crop residual").isCloseTo(reference.right, 0.02f)
        assertThat(actual.left, "$dimension left crop residual").isCloseTo(reference.left, 0.02f)
        assertThat(actual.bottom, "$dimension bottom crop residual").isCloseTo(reference.bottom, 0.02f)
        assertThat(actual.top, "$dimension top crop residual").isCloseTo(reference.top, 0.02f)
      } finally {
        delegate.detach()
        graphics.releaseGraphicsLayer(input)
      }
    }
  }

  private fun plainEdges(size: Size): Edges {
    val base = RecordingDrawContext(recordedSize = size)
    val graphics = base.requireGraphicsContext()
    val input = graphics.createGraphicsLayer()
    input.record(Density(1f), LayoutDirection.Ltr, IntSize(size.width.toInt(), size.height.toInt())) { drawRect(Color.Red) }
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
          colorEffects(emptyList())
          backgroundColor(Color.Transparent)
        },
        HazePerformanceMode.Fixed(5f / 12f),
      ),
      HazeSampling.FullResolution,
    )
    val delegate = RenderEffectBlurVisualEffectDelegate(effect)
    return try {
      base.render {
        drawRect(Color.Magenta)
        with(delegate) { draw(context) }
      }
      edges(checkNotNull(base.output).toPixelMap())
    } finally {
      delegate.detach()
      graphics.releaseGraphicsLayer(input)
    }
  }

  private fun edges(pixels: PixelMap): Edges {
    var left = 0f
    var right = 0f
    var top = 0f
    var bottom = 0f
    // Exclude corners so the perpendicular edge cannot dominate an axis measurement.
    for (along in pixels.width / 4 until pixels.width * 3 / 4) {
      for (line in 0 until 4) {
        left = maxOf(left, pixels[line, along].blue)
        right = maxOf(right, pixels[pixels.width - 1 - line, along].blue)
        top = maxOf(top, pixels[along, line].blue)
        bottom = maxOf(bottom, pixels[along, pixels.height - 1 - line].blue)
      }
    }
    return Edges(left, right, top, bottom)
  }

  @dev.chrisbanes.haze.Poko
  private class Edges(val left: Float, val right: Float, val top: Float, val bottom: Float)

  private companion object {
    const val SCALE = 0.75f
  }
}
