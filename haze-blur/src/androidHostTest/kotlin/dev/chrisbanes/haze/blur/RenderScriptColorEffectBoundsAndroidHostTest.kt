// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectLayoutScope
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.scaledContentScale
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

/**
 * RenderScript tints and colour filters cover the effect's layer, `Rect(-layerOffset, layerSize)`,
 * when the layer is clipped on one side or offset inside the node (#1912). The node is 64 px at
 * (32, 32) in a white 128 px scene; row y = 64 crosses its middle.
 */
@Config(sdk = [35])
class RenderScriptColorEffectBoundsAndroidHostTest : ScreenshotTest() {
  private val context get() = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun drawPresentation_clippedLayer_tintCoversLayerSize() =
    checkBounds(HazeColorEffect.tint(Color.Red), Color.Red, CLIPPED, covered = listOf(40, 104), uncovered = listOf(120))

  @Test
  fun drawPresentation_clippedLayer_colorFilterCoversLayerSize() =
    checkBounds(HazeColorEffect.colorFilter(ColorFilter.tint(Color.Blue)), Color.Blue, CLIPPED, covered = listOf(40, 104), uncovered = listOf(120))

  @Test
  fun drawPresentation_negativeOffsetLayer_tintCoversLayerSize() =
    checkBounds(HazeColorEffect.tint(Color.Red), Color.Red, NEGATIVE_OFFSET, covered = listOf(50), uncovered = listOf(36, 100))

  @Test
  fun drawPresentation_negativeOffsetLayer_colorFilterCoversLayerSize() =
    checkBounds(HazeColorEffect.colorFilter(ColorFilter.tint(Color.Blue)), Color.Blue, NEGATIVE_OFFSET, covered = listOf(50), uncovered = listOf(36, 100))

  private fun checkBounds(
    colorEffect: HazeColorEffect,
    expected: Color,
    layer: LayerCase,
    covered: List<Int>,
    uncovered: List<Int>,
  ) = runScreenshotTest {
    val effect = BlurVisualEffect().apply {
      val style = HazeBlurStyle {
        blurredEdgeTreatment(BlurredEdgeTreatment.Unbounded)
        noiseFactor(0f)
        backgroundColor(Color.Transparent)
        colorEffects(listOf(colorEffect))
        fallbackColorEffect(null)
      }
      update(HostLifecycleScope(context), BlurConfiguration(style, HazePerformanceMode.Quality), HazeSampling.FullResolution)
    }
    val factory = PresentationFactory(effect, layer.bounds)
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(Modifier.size(128.dp).background(Color.White)) {
          Box(Modifier.offset(32.dp, 32.dp).size(64.dp).hazeEffect(factory, HazeInput.Content, 0))
        }
      }
    }
    val pixels = frame()

    assertThat(factory.layerSize, "layer size").isEqualTo(layer.size)
    assertThat(factory.layerOffset, "layer offset").isEqualTo(layer.offset)
    covered.forEach { pixels.assertColor(it, expected) }
    uncovered.forEach { pixels.assertColor(it, Color.White) }
  }

  private fun PixelMap.assertColor(x: Int, expected: Color) {
    val actual = this[x, ROW]
    assertThat(actual.red, "red at x=$x").isCloseTo(expected.red, TOLERANCE)
    assertThat(actual.green, "green at x=$x").isCloseTo(expected.green, TOLERANCE)
    assertThat(actual.blue, "blue at x=$x").isCloseTo(expected.blue, TOLERANCE)
  }

  private class LayerCase(val size: Size, val offset: Offset, val bounds: HazeEffectLayoutScope.() -> Rect)

  private companion object {
    const val ROW = 64
    const val TOLERANCE = 8f / 255f

    /** Extends 16 px past the node's right and bottom edges, so the layer is asymmetric. */
    val CLIPPED = LayerCase(Size(80f, 80f), Offset.Zero) {
      modifierBounds.run { Rect(left, top, right + 16f, bottom + 16f) }
    }

    /** Starts 10 px inside the node's top-left corner, so the layer offset is negative. */
    val NEGATIVE_OFFSET = LayerCase(Size(54f, 54f), Offset(-10f, -10f)) {
      modifierBounds.run { Rect(left + 10f, top + 10f, right, bottom) }
    }
  }
}

/** Presents a flat grey RenderScript output over the layer that [bounds] requests. */
private class PresentationFactory(
  val effect: BlurVisualEffect,
  val bounds: HazeEffectLayoutScope.() -> Rect,
) : HazeEffectFactory<Int> {
  var layerSize: Size = Size.Unspecified
  var layerOffset: Offset = Offset.Unspecified

  override fun createRenderer(): HazeEffectRenderer<Int> = object : HazeEffectRenderer<Int> {
    private var delegate: RenderScriptBlurVisualEffectDelegate? = null
    private var output: GraphicsLayer? = null
    private var outputOwner: GraphicsContext? = null

    override fun HazeEffectLayoutScope.calculateLayerBounds(style: Int): Rect = bounds()

    override fun HazeEffectDrawScope.draw(style: Int) {
      val runtime = this as HazeEffectRuntimeDrawScope
      this@PresentationFactory.layerSize = runtime.layerSize
      this@PresentationFactory.layerOffset = runtime.layerOffset
      val graphicsContext = runtime.requireGraphicsContext()
      val current = delegate ?: RenderScriptBlurVisualEffectDelegate(effect, graphicsContext, runtime.requirePlatformContext())
        .also { delegate = it }
      val layer = output ?: graphicsContext.createGraphicsLayer().apply {
        record(runtime.layerSize.roundToIntSize()) { drawRect(GREY) }
      }.also {
        output = it
        outputOwner = graphicsContext
      }
      with(current) { drawPresentation(runtime, layer, runtime.layerOffset, scaledContentScale(runtime.layerSize, 1f)) }
    }

    override fun dispose() {
      delegate?.detach()
      // The delegate does not own the output layer, so release it with the context that created it.
      output?.let { outputOwner?.releaseGraphicsLayer(it) }
      output = null
    }
  }
}

private val GREY = Color(0.5f, 0.5f, 0.5f)
