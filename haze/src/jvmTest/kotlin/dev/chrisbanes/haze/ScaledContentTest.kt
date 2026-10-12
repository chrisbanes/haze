// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalComposeUiApi::class, InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.SkiaGraphicsContext
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.prop
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

class ScaledContentTest {

  @Test
  fun translate_finiteOffset_translatesBlock() {
    val pixels = render {
      translate(Offset(10f, 20f)) { drawRect(Color.Red, size = Size(5f, 5f)) }
    }

    assertThat(pixels[12, 22]).isEqualTo(Color.Red)
    assertThat(pixels[2, 2]).isEqualTo(Color.Transparent)
  }

  @Test
  fun translate_zeroOffset_runsBlockUntranslated() {
    var runs = 0
    val pixels = render {
      translate(Offset.Zero) {
        runs++
        drawRect(Color.Red, size = Size(5f, 5f))
      }
    }

    assertThat(runs).isEqualTo(1)
    assertThat(pixels[2, 2]).isEqualTo(Color.Red)
  }

  @Test
  fun translate_nonFiniteOffset_runsBlockUntranslated() {
    for (offset in listOf(
      Offset(Float.NaN, 0f),
      Offset(0f, Float.POSITIVE_INFINITY),
      Offset(Float.NEGATIVE_INFINITY, Float.NaN),
    )) {
      var runs = 0
      val pixels = render {
        translate(offset) {
          runs++
          drawRect(Color.Red, size = Size(5f, 5f))
        }
      }

      assertThat(runs, name = "runs for $offset").isEqualTo(1)
      assertThat(pixels[2, 2], name = "pixel for $offset").isEqualTo(Color.Red)
    }
  }

  @Test
  fun drawScaledContent_scalesByTheLargerAxisRatioAndTranslates() {
    val pixels = render {
      // max(40 / 20, 40 / 10) = 4, so the 2x2 rect covers 4..12 after the offset.
      drawScaledContent(offset = Offset(4f, 4f), scaledSize = Size(20f, 10f)) {
        drawRect(Color.Red, size = Size(2f, 2f))
      }
    }

    assertThat(pixels[6, 6]).isEqualTo(Color.Red)
    assertThat(pixels[11, 11]).isEqualTo(Color.Red)
    assertThat(pixels[13, 13]).isEqualTo(Color.Transparent)
    assertThat(pixels[2, 2]).isEqualTo(Color.Transparent)
  }

  @Test
  fun drawScaledContent_clipsToTheScopeOnlyWhenAsked() {
    fun pixelOutsideScope(clip: Boolean): Color = render(scopeSize = Size(20f, 20f)) {
      drawScaledContent(offset = Offset.Zero, scaledSize = Size(10f, 10f), clip = clip) {
        drawRect(Color.Red, size = Size(20f, 20f))
      }
    }[30, 30]

    assertThat(pixelOutsideScope(clip = true)).isEqualTo(Color.Transparent)
    assertThat(pixelOutsideScope(clip = false)).isEqualTo(Color.Red)
  }

  @Test
  fun createScaledContentLayer_emptyScaledSize_returnsNullWithoutCreatingALayer() {
    val context = FakeRuntimeDrawScope()

    val layer = context.createScaledContentLayer(
      context = context,
      backgroundColor = Color.Red,
      scaleFactor = 0.01f,
      layerSize = Size(10f, 10f),
    )

    assertThat(layer).isNull()
    assertThat(context.createdLayers).isEqualTo(0)
  }

  @Test
  fun createScaledContentLayer_reusesALiveLayerAndReplacesAReleasedOne() {
    val context = FakeRuntimeDrawScope()
    fun capture(existing: GraphicsLayer?): GraphicsLayer = checkNotNull(
      context.createScaledContentLayer(
        context = context,
        backgroundColor = Color.Red,
        scaleFactor = 1f,
        layerSize = Size(10f, 10f),
        existingLayer = existing,
      ),
    )

    val first = capture(existing = null)
    assertThat(capture(existing = first)).isSameInstanceAs(first)

    context.graphicsContext.releaseGraphicsLayer(first)
    val third = capture(existing = first)

    assertThat(third).isNotSameInstanceAs(first)
    assertThat(third.isReleased).isFalse()
    assertThat(context.createdLayers).isEqualTo(2)
  }

  @Test
  fun createScaledContentLayer_roundsTheOutlineOutwardFromTheCaptureSize() {
    val context = FakeRuntimeDrawScope()

    // 21 * 0.4 = 8.4: the capture rounds to 8px, the outline outward to 9px.
    val layer = context.createScaledContentLayer(
      context = context,
      backgroundColor = Color.Red,
      scaleFactor = 0.4f,
      layerSize = Size(21f, 21f),
    )

    assertThat(layer).isNotNull().prop(GraphicsLayer::size).isEqualTo(IntSize(8, 8))
    assertThat(layer!!.outline).isInstanceOf<Outline.Rectangle>()
      .prop(Outline.Rectangle::rect).isEqualTo(Rect(0f, 0f, 9f, 9f))
  }

  @Test
  fun createScaledContentLayer_backgroundShaderWinsAndInvisibleColoursDrawNothing() {
    val blue = LinearGradientShader(
      from = Offset.Zero,
      to = Offset(10f, 0f),
      colors = listOf(Color.Blue, Color.Blue),
    )

    assertThat(backgroundPixel(Color.Red)).isEqualTo(Color.Red)
    assertThat(backgroundPixel(Color.Red, blue)).isEqualTo(Color.Blue)
    assertThat(backgroundPixel(Color.Transparent)).isEqualTo(Color.Transparent)
    assertThat(backgroundPixel(Color.Unspecified)).isEqualTo(Color.Transparent)
  }

  private fun backgroundPixel(
    color: Color,
    shader: androidx.compose.ui.graphics.Shader? = null,
  ): Color {
    val context = FakeRuntimeDrawScope()
    var layer: GraphicsLayer? = null
    return render {
      layer = context.createScaledContentLayer(
        context = context,
        backgroundColor = color,
        scaleFactor = 1f,
        layerSize = Size(10f, 10f),
        backgroundShader = shader,
      )
      drawLayer(checkNotNull(layer))
    }[5, 5]
  }

  private fun render(
    scopeSize: Size = Size(40f, 40f),
    block: DrawScope.() -> Unit,
  ): PixelMap {
    val bitmap = ImageBitmap(40, 40)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), scopeSize, block)
    return bitmap.toPixelMap()
  }

  private class CountingGraphicsContext : GraphicsContext {
    private val delegate = SkiaGraphicsContext()
    var createdLayers = 0
      private set

    override fun createGraphicsLayer(): GraphicsLayer = delegate.createGraphicsLayer().also { createdLayers++ }

    override fun releaseGraphicsLayer(layer: GraphicsLayer) = delegate.releaseGraphicsLayer(layer)
  }

  /** A runtime draw scope whose input draws nothing, so captures hold only their background. */
  private class FakeRuntimeDrawScope(
    drawScope: CanvasDrawScope = CanvasDrawScope(),
  ) : HazeEffectRuntimeDrawScope, DrawScope by drawScope {
    val graphicsContext = CountingGraphicsContext()
    val createdLayers: Int get() = graphicsContext.createdLayers

    override val modifierBounds: Rect = Rect.Zero
    override val sampling: HazeSampling = HazeSampling.FullResolution
    override val modifierSize: Size = Size.Zero
    override val layerSize: Size = Size.Zero
    override val layerOffset: Offset = Offset.Zero
    override val hasDrawableInput: Boolean = true
    override val inputSnapshot: HazeEffectInputSnapshot? = null
    override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

    override fun requirePlatformContext(): PlatformContext = error("Unused")
    override fun requireGraphicsContext(): GraphicsContext = graphicsContext
    override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unused")
    override fun invalidateDraw() = Unit
    override fun drawInput() = Unit
    override fun DrawScope.drawInput() = Unit
  }
}
