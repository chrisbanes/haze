// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.SkiaGraphicsContext
import assertk.assertThat
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test

@OptIn(InternalComposeUiApi::class)
class HazeAreaReleaseContentLayerTest {

  @Test
  fun releaseContentLayer_releasesAndClearsTheLayer() {
    val graphicsContext = SkiaGraphicsContext()
    val layer = graphicsContext.createGraphicsLayer()
    val area = HazeArea().apply { contentLayer = layer }

    area.releaseContentLayer(graphicsContext)

    assertThat(layer.isReleased).isTrue()
    assertThat(area.contentLayer).isNull()
  }

  @Test
  fun releaseContentLayer_withoutALayer_leavesTheAreaClear() {
    val area = HazeArea()

    area.releaseContentLayer(SkiaGraphicsContext())

    assertThat(area.contentLayer).isNull()
  }
}
