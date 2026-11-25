// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader.TileMode.REPEAT
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectLayoutScope
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.drawScaledContent
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.scaledContentScale
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import org.robolectric.annotation.Config

/**
 * Noise grain must not grow as the capture scale drops (#1832). Paths that draw noise in
 * presentation space keep identical grain at any scale; the RenderEffect path draws it inside the
 * capture, so its grain is one capture pixel. References are rendered through the same pipeline,
 * and each test checks that the old `1/scale` texture matrix is separable from its reference.
 */
class NoiseGrainScaleAndroidHostTest : ScreenshotTest() {
  private val context get() = ApplicationProvider.getApplicationContext<Context>()

  @Test
  @Config(sdk = [33, 35])
  fun renderEffect_reducedCapture_grainIsOneCapturePixel() = checkRenderEffectGrain(tint = null)

  // Robolectric cannot construct the combined noise-tint RuntimeShader effect before SDK 35. That
  // effect shares createNoiseShader() with the plain path covered on SDK 33.
  @Test
  @Config(sdk = [35])
  fun renderEffect_reducedCaptureWithTint_grainIsOneCapturePixel() = checkRenderEffectGrain(tint = TINT)

  @Test
  @Config(sdk = [33, 35])
  fun renderEffect_reducedCapture_keepsNoiseContrast() = checkRenderEffectContrast(tint = null)

  @Test
  @Config(sdk = [35])
  fun renderEffect_reducedCaptureWithTint_keepsNoiseContrast() = checkRenderEffectContrast(tint = TINT)

  @Test
  @Config(sdk = [35])
  fun renderScript_fullAndReducedCaptureScale_presentIdenticalGrain() = runScreenshotTest {
    val offset = Offset(5f, 7f)
    // 0.3125 is the radius-limited scale RenderScript picks for an 80 px radius (25 px / 80 px).
    val scales = listOf(1f, 0.5f, 0.3125f)
    val factories = scales.map { RenderScriptPresentationFactory(blurEffect(HazeBlurStyle { noiseFactor(1f) }), it, offset) }
    setContent {
      Scene {
        factories.forEach { Box(Modifier.size(SIZE.dp).hazeEffect(it, HazeInput.Content, 0)) }
        Canvas(Modifier.size(SIZE.dp)) { drawRenderScriptReference(offset, legacyNoiseShader(0.5f)) }
      }
    }
    val pixels = frame()

    for (index in 1..scales.lastIndex) {
      assertThat(regionDifference(pixels, pixels, index), "capture scale ${scales[index]}").isLessThan(0.01f)
    }
    assertThat(regionDifference(pixels, pixels, scales.size), "old-matrix control").isGreaterThan(0.1f)
  }

  @Test
  @Config(sdk = [32])
  fun logicalCustomBrush_fullAndReducedInputScale_presentIdenticalGrain() = runScreenshotTest {
    val scales = listOf(1f, 0.5f)
    val style = HazeBlurStyle {
      blurRadius(8.dp)
      noiseFactor(1f)
      backgroundColor(Color.Transparent)
      mask(OPAQUE_CUSTOM_MASK)
    }
    val factories = scales.map { LogicalPathFactory(blurEffect(style), it) }
    setContent {
      Scene {
        factories.forEach { Box(Modifier.size(SIZE.dp).hazeEffect(it, HazeInput.Content, 0)) }
        Canvas(Modifier.size(SIZE.dp)) { drawLogicalReference(context.createNoiseShader()) }
        Canvas(Modifier.size(SIZE.dp)) { drawLogicalReference(legacyNoiseShader(0.5f)) }
      }
    }
    val pixels = frame()

    assertThat(regionDifference(pixels, pixels, 1), "input scale 0.5").isLessThan(0.02f)
    // The layer is expanded, so this also checks the noise origin sits at the layer's top left.
    assertThat(regionDifference(pixels, pixels, 2), "full-resolution reference").isLessThan(0.02f)
    assertThat(regionDifference(pixels, pixels, 3), "old-matrix control").isGreaterThan(0.1f)
  }

  // Guards the API 31–32 full-resolution constraint for custom brushes, not the noise matrix.
  @Test
  @Config(sdk = [32])
  fun logicalCustomBrush_productionReducedMode_keepsFullResolutionGrain() = runScreenshotTest {
    val style = HazeBlurStyle {
      blurRadius(8.dp)
      noiseFactor(1f)
      backgroundColor(Color.Transparent)
      mask(OPAQUE_CUSTOM_MASK)
    }
    var mode by mutableStateOf(HazePerformanceMode.Quality)
    setContent {
      Scene { BlurBox(style, mode) }
    }
    val full = frame()
    // Custom brushes on API 31–32 force a full-resolution capture, so the grain is unchanged.
    mode = HazePerformanceMode.Performance

    assertThat(regionDifference(full, frame(), 0)).isLessThan(0.02f)
  }

  private fun checkRenderEffectGrain(tint: Color?) = runScreenshotTest {
    val style = renderEffectStyle(tint)
    var mode by mutableStateOf(HazePerformanceMode.Quality)
    var scale by mutableStateOf(1f)
    setContent {
      Scene {
        BlurBox(style, mode)
        Reference(scale, legacy = false, tint)
        Reference(scale, legacy = true, tint)
      }
    }
    val full = frame()
    // Performance resolves to exactly half resolution.
    mode = HazePerformanceMode.Performance
    scale = 0.5f
    val reduced = frame()

    val fullReference = grain(full, 1)
    assertThat(grain(full, 0), "Quality grain").isBetween(fullReference * 0.8f, fullReference * 1.2f)
    val reference = grain(reduced, 1)
    assertThat(grain(reduced, 0), "Performance grain").isBetween(reference * 0.8f, reference * 1.2f)
    assertThat(grain(reduced, 2), "old-matrix control grain").isGreaterThanOrEqualTo(reference * 1.5f)
  }

  // Upscaling one-pixel capture noise smooths it, so the contrast at a reduced scale is compared with
  // a reference capture at that scale. Matching it shows the noise alpha and blend are unchanged.
  private fun checkRenderEffectContrast(tint: Color?) = runScreenshotTest {
    val style = renderEffectStyle(tint)
    var mode by mutableStateOf(HazePerformanceMode.Quality)
    var scale by mutableStateOf(1f)
    setContent {
      Scene {
        BlurBox(style, mode)
        Reference(scale, legacy = false, tint)
      }
    }
    val full = frame()
    mode = HazePerformanceMode.Performance
    scale = 0.5f
    val reduced = frame()

    val fullReference = contrast(full, 1)
    assertThat(contrast(full, 0), "Quality contrast").isBetween(fullReference * 0.85f, fullReference * 1.15f)
    val reference = contrast(reduced, 1)
    assertThat(contrast(reduced, 0), "Performance contrast").isBetween(reference * 0.85f, reference * 1.15f)
  }

  private fun renderEffectStyle(tint: Color?) = HazeBlurStyle {
    blurRadius(8.dp)
    noiseFactor(1f)
    backgroundColor(Color.Transparent)
    colorEffects(listOfNotNull(tint?.let { HazeColorEffect.tint(it) }))
  }

  private fun blurEffect(style: HazeBlurStyle) = BlurVisualEffect().apply {
    update(HostLifecycleScope(context), BlurConfiguration(style, HazePerformanceMode.Quality), HazeSampling.FullResolution)
  }

  private fun legacyNoiseShader(scale: Float) = BitmapShader(context.getNoiseTexture(), REPEAT, REPEAT).apply {
    setLocalMatrix(Matrix().apply { setScale(1f / scale, 1f / scale) })
  }

  /** The RenderScript presentation of a flat grey output with full-strength noise. */
  private fun DrawScope.drawRenderScriptReference(offset: Offset, noise: Shader) {
    drawRect(GREY)
    translate(-offset.x, -offset.y) {
      drawRect(ShaderBrush(noise), size = size.expanded(offset), blendMode = BlendMode.SrcAtop)
    }
  }

  /** The logical presentation of a flat grey output with full-strength noise. */
  private fun DrawScope.drawLogicalReference(noise: Shader) {
    drawRect(GREY)
    translate(-LOGICAL_EXPANSION, -LOGICAL_EXPANSION) {
      drawRect(ShaderBrush(noise), blendMode = BlendMode.Softlight)
    }
  }
}

@Composable
private fun Scene(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalDensity provides Density(1f)) {
    Row(Modifier.background(Color.White)) { content() }
  }
}

@Composable
private fun BlurBox(style: HazeBlurStyle, mode: HazePerformanceMode) {
  val state = rememberHazeState()
  Box(Modifier.size(SIZE.dp)) {
    Box(Modifier.matchParentSize().hazeSource(state).background(GREY))
    Box(Modifier.matchParentSize().hazeBlur(HazeInput.Sources(state), style, mode))
  }
}

/** Noise drawn into a flat grey capture at [scale], then presented as the RenderEffect path does. */
@Composable
private fun Reference(scale: Float, legacy: Boolean, tint: Color?) {
  val context = LocalContext.current
  val layer = rememberGraphicsLayer()
  Canvas(Modifier.size(SIZE.dp)) {
    val noise = context.createNoiseShader().apply {
      if (legacy) setLocalMatrix(Matrix().apply { setScale(1f / scale, 1f / scale) })
    }
    layer.record((size * scale).roundToIntSize()) {
      drawRect(GREY)
      drawRect(ShaderBrush(noise), blendMode = BlendMode.Softlight)
      tint?.let { drawRect(it) }
    }
    drawScaledContent(Offset.Zero, scaledContentScale(size, scale)) { drawLayer(layer) }
  }
}

/** Presents a flat grey RenderScript output recorded at [scale]. */
private class RenderScriptPresentationFactory(
  val effect: BlurVisualEffect,
  val scale: Float,
  val offset: Offset,
) : HazeEffectFactory<Int> {
  override fun createRenderer(): HazeEffectRenderer<Int> = object : HazeEffectRenderer<Int> {
    private var delegate: RenderScriptBlurVisualEffectDelegate? = null
    private var output: GraphicsLayer? = null

    override fun HazeEffectDrawScope.draw(style: Int) {
      val runtime = this as HazeEffectRuntimeDrawScope
      val graphicsContext = runtime.requireGraphicsContext()
      val current = delegate ?: RenderScriptBlurVisualEffectDelegate(effect, graphicsContext, runtime.requirePlatformContext())
        .also { delegate = it }
      val layer = output ?: greyLayer(graphicsContext.createGraphicsLayer(), runtime.layerSize, scale).also { output = it }
      with(current) { drawPresentation(runtime, layer, offset, scaledContentScale(runtime.layerSize, scale)) }
    }

    override fun dispose() {
      delegate?.detach()
    }
  }
}

/** Drives the API 31–32 logical custom-brush path with a flat grey capture at [inputScale]. */
private class LogicalPathFactory(val effect: BlurVisualEffect, val inputScale: Float) : HazeEffectFactory<Int> {
  override fun createRenderer(): HazeEffectRenderer<Int> = object : HazeEffectRenderer<Int> {
    private val delegate = RenderEffectBlurVisualEffectDelegate(effect)

    // A non-zero layer offset, so the test covers where the noise is placed.
    override fun HazeEffectLayoutScope.calculateLayerBounds(style: Int): Rect = modifierBounds.inflate(LOGICAL_EXPANSION)
    private var content: GraphicsLayer? = null

    override fun HazeEffectDrawScope.draw(style: Int) {
      val runtime = this as HazeEffectRuntimeDrawScope
      val layer = content
        ?: greyLayer(runtime.requireGraphicsContext().createGraphicsLayer(), runtime.layerSize, inputScale).also { content = it }
      check(with(delegate) { drawLogicalCustomStyle(this@draw, layer, runtime, inputScale) })
    }

    override fun dispose() {
      delegate.detach()
    }
  }
}

private fun DrawScope.greyLayer(layer: GraphicsLayer, layerSize: Size, scale: Float) = layer.apply {
  record((layerSize * scale).roundToIntSize()) { drawRect(GREY) }
}

private fun Size.expanded(offset: Offset) = Size(width + maxOf(offset.x, 0f) * 2, height + maxOf(offset.y, 0f) * 2)

/** Luminance of box [index]'s interior, row by row. */
private fun PixelMap.interior(index: Int): List<FloatArray> = (MARGIN until SIZE - MARGIN).map { y ->
  FloatArray(SIZE - 2 * MARGIN) { x -> this[index * SIZE + MARGIN + x, y].luminance() }
}

/** About 1 for one-pixel noise; grows with the grain size. */
private fun grain(pixels: PixelMap, index: Int): Float {
  val rows = pixels.interior(index)
  fun meanStep(step: Int) = rows.flatMap { row -> (0 until row.size - step).map { abs(row[it + step] - row[it]) } }.average()
  return (meanStep(8) / meanStep(1)).toFloat()
}

private fun contrast(pixels: PixelMap, index: Int): Float {
  val values = pixels.interior(index).flatMap { it.asList() }
  val mean = values.average()
  return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size).toFloat()
}

/** The largest channel difference between the interior of box 0 in [first] and box [index] in [second]. */
private fun regionDifference(first: PixelMap, second: PixelMap, index: Int): Float {
  var max = 0f
  for (y in MARGIN until SIZE - MARGIN) {
    for (x in MARGIN until SIZE - MARGIN) {
      val a = first[x, y]
      val b = second[index * SIZE + x, y]
      max = maxOf(max, abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue), abs(a.alpha - b.alpha))
    }
  }
  return max
}

private const val SIZE = 128
private const val MARGIN = 16
private const val LOGICAL_EXPANSION = 5f
private val GREY = Color(0.5f, 0.5f, 0.5f)
private val TINT = Color.Red.copy(alpha = 0.3f)

/** An opaque custom shader brush, so the API 31–32 logical path draws the style. */
private val OPAQUE_CUSTOM_MASK = object : ShaderBrush() {
  override fun createShader(size: Size): Shader = LinearGradientShader(Offset.Zero, Offset(1f, 0f), listOf(Color.Black, Color.Black))
}
