// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer

internal class RetainedGlassGroupAlphaLayer {
  var layer: GraphicsLayer? = null
    private set

  val isAvailable: Boolean get() = layer?.isReleased == false

  fun prepare(required: Boolean, graphicsContext: GraphicsContext) {
    if (required) {
      layer = ensureLayer(layer, graphicsContext)
    } else {
      release(graphicsContext)
    }
  }

  fun release(graphicsContext: GraphicsContext?) {
    releaseLayer(layer, graphicsContext)
    layer = null
  }
}

internal fun requiresGlassGroupAlpha(alpha: Float): Boolean = alpha > 0f && alpha < 1f
