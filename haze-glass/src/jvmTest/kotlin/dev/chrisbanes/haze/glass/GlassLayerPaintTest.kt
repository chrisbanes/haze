// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class GlassLayerPaintTest {

  // Skia keeps paint alpha in eight bits, so only multiples of 1/255 read back exactly.
  private val low = 64f / 255f
  private val high = 192f / 255f

  @Test
  fun withLayerPaint_sequentialCallsReuseOnePaintWithEachCallsValues() {
    val layers = drawLayers {
      withLayerPaint(low, BlendMode.SrcOver) {}
      withLayerPaint(high, BlendMode.Plus) {}
    }

    assertThat(layers.map { it.alpha }).isEqualTo(listOf(low, high))
    assertThat(layers.map { it.blendMode }).isEqualTo(listOf(BlendMode.SrcOver, BlendMode.Plus))
    assertThat(layers[1].paint).isSameInstanceAs(layers[0].paint)
  }

  @Test
  fun withLayerPaint_nestedCallsKeepEachLevelsAlphaAndBlendMode() {
    val layers = drawLayers {
      withLayerPaint(low, BlendMode.SrcOver) {
        withLayerPaint(high, BlendMode.Plus) {}
      }
    }
    val (outer, inner) = layers

    assertThat(inner.paint).isNotSameInstanceAs(outer.paint)
    assertThat(outer.alpha).isEqualTo(low)
    assertThat(outer.blendMode).isEqualTo(BlendMode.SrcOver)
    assertThat(inner.alpha).isEqualTo(high)
    assertThat(inner.blendMode).isEqualTo(BlendMode.Plus)
    // The inner call must not have rewritten the enclosing layer's paint.
    assertThat(outer.paint.alpha).isEqualTo(low)
    assertThat(outer.paint.blendMode).isEqualTo(BlendMode.SrcOver)
  }

  @Test
  fun withLayerPaint_throwingBlockReleasesItsDepth() {
    val layers = drawLayers {
      runCatching {
        withLayerPaint(low, BlendMode.SrcOver) { error("boom") }
      }
      withLayerPaint(high, BlendMode.Plus) {}
    }

    assertThat(layers[1].paint).isSameInstanceAs(layers[0].paint)
    assertThat(layers[1].alpha).isEqualTo(high)
  }

  private class RecordedLayer(val paint: Paint, val alpha: Float, val blendMode: BlendMode)

  private fun drawLayers(block: DrawScope.() -> Unit): List<RecordedLayer> {
    val layers = mutableListOf<RecordedLayer>()
    val delegate = Canvas(ImageBitmap(8, 8))
    val canvas = object : Canvas by delegate {
      override fun saveLayer(bounds: Rect, paint: Paint) {
        layers += RecordedLayer(paint, paint.alpha, paint.blendMode)
        delegate.saveLayer(bounds, paint)
      }
    }
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(8f, 8f), block)
    return layers
  }
}
