// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.SkiaGraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test

@OptIn(InternalComposeUiApi::class)
class HazeAreaReleaseContentLayerTest {

  @Test
  fun releaseContentLayer_releasesAndClearsTheLayer() {
    val graphicsContext = SkiaGraphicsContext()
    val layer = graphicsContext.createGraphicsLayer()
    val area = HazeArea().apply {
      contentLayer = layer
      inputBounds = Rect(-4f, -4f, 44f, 44f)
    }

    area.releaseContentLayer(graphicsContext)

    assertThat(layer.isReleased).isTrue()
    assertThat(area.contentLayer).isNull()
    assertThat(area.inputBounds).isNull()
  }

  @Test
  fun reset_clearsInputBounds() {
    val area = HazeArea().apply { inputBounds = Rect(-4f, -4f, 44f, 44f) }

    area.reset()

    assertThat(area.inputBounds).isNull()
  }

  @Test
  fun releaseContentLayer_withoutALayer_releasesNothing() {
    val released = mutableListOf<GraphicsLayer>()
    val graphicsContext = object : GraphicsContext {
      override fun createGraphicsLayer(): GraphicsLayer = error("Unused")
      override fun releaseGraphicsLayer(layer: GraphicsLayer) {
        released += layer
      }
    }

    HazeArea().releaseContentLayer(graphicsContext)

    assertThat(released).isEmpty()
  }
}
