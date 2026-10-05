// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.SkiaGraphicsContext
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlin.test.Test

class HazeContentCaptureTest {
  @Test
  fun ownerRemoval_multipleLeasesReleaseExactlyOnceAfterLastClose() {
    val context = CaptureGraphicsContext()
    val area = HazeArea()
    val layer = area.writableContentLayer(context)
    record(layer, Color.Red)
    val first = checkNotNull(area.acquireContentCapture())
    val second = checkNotNull(area.acquireContentCapture())
    area.releaseContentLayer()
    area.releaseContentLayer()
    assertThat(area.contentLayer).isNull()
    assertThat(layer.isReleased).isFalse()
    first.release()
    assertThat(context.released.size).isEqualTo(0)
    second.release()
    assertThat(context.released).isEqualTo(listOf(layer))
    assertThat(layer.isReleased).isTrue()
  }

  @Test
  fun writableCapture_leasesKeepPreviousFrameAndOwnerMovesToNewLayer() {
    val context = CaptureGraphicsContext()
    val area = HazeArea()
    val oldLayer = area.writableContentLayer(context)
    record(oldLayer, Color.Red)
    assertThat(area.writableContentLayer(context)).isSameInstanceAs(oldLayer)
    val lease = checkNotNull(area.acquireContentCapture())
    val current = area.writableContentLayer(context)
    record(current, Color.Green)
    assertThat(current).isNotSameInstanceAs(oldLayer)
    assertThat(area.contentLayer).isSameInstanceAs(current)
    assertThat(oldLayer.isReleased).isFalse()
    val capture = HazeEffectInputCaptureImpl(listOf(CapturedHazeInput(lease, Matrix())), Offset.Zero)
    assertThat(render(capture)[0, 0]).isEqualTo(Color.Red)
    capture.release()
    capture.release()
    assertThat(context.released).isEqualTo(listOf(oldLayer))
    area.releaseContentLayer()
    assertThat(context.released).isEqualTo(listOf(oldLayer, current))
  }

  @Test
  fun replayCopiedTransformAndOffset_survivesGeometryChangesAndOwnerRemoval() {
    val context = CaptureGraphicsContext()
    val area = HazeArea()
    val layer = area.writableContentLayer(context)
    record(layer, Color.Blue)
    val transform = Matrix().apply { translate(1f, 0f) }
    val copied = Matrix(transform.values.copyOf())
    val capture = HazeEffectInputCaptureImpl(
      listOf(CapturedHazeInput(checkNotNull(area.acquireContentCapture()), copied)),
      Offset(1f, 0f),
    )
    transform.translate(100f, 100f)
    area.size = Size.Zero
    area.releaseContentLayer()
    val pixels = render(capture)
    assertThat(pixels[0, 0]).isEqualTo(Color.Transparent)
    assertThat(pixels[2, 0]).isEqualTo(Color.Blue)
    capture.release()
    capture.release()
    assertThat(context.released).isEqualTo(listOf(layer))
  }

  private fun record(layer: GraphicsLayer, color: Color) {
    layer.record(Density(1f), LayoutDirection.Ltr, IntSize(2, 2)) { drawRect(color) }
  }

  private fun render(capture: HazeEffectInputCapture) = ImageBitmap(5, 3).also { bitmap ->
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(5f, 3f)) {
      with(capture) { this@draw.drawInput() }
    }
  }.toPixelMap()
}

private class CaptureGraphicsContext : GraphicsContext {
  private val delegate = SkiaGraphicsContext()
  val released = mutableListOf<GraphicsLayer>()
  override fun createGraphicsLayer(): GraphicsLayer = delegate.createGraphicsLayer()
  override fun releaseGraphicsLayer(layer: GraphicsLayer) {
    released += layer
    delegate.releaseGraphicsLayer(layer)
  }
}
