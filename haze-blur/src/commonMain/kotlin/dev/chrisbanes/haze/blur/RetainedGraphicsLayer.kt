// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer

/**
 * Keeps one [GraphicsLayer] across draws, so a delegate re-records it instead of creating a layer
 * per draw.
 *
 * A retained layer keeps whatever state the previous draw set. Callers set every layer property they
 * use on every draw, and [release] the layer when a draw stops using it and on detach, trim and
 * retained-output clears. Confined to the draw thread, like the delegate fields beside it.
 */
internal class RetainedGraphicsLayer {
  private var owner: GraphicsContext? = null
  private var key: Long = 0L

  /** The retained layer, or null when there is none. */
  var layer: GraphicsLayer? = null
    private set

  /**
   * Returns the retained layer, creating one from [graphicsContext] when there is none, when it was
   * released elsewhere, or when [key] changed (the old layer is released first).
   */
  fun acquire(graphicsContext: GraphicsContext, key: Long = 0L): GraphicsLayer {
    layer?.takeIf { !it.isReleased && this.key == key }?.let { return it }
    release()
    return graphicsContext.createGraphicsLayer().also {
      layer = it
      owner = graphicsContext
      this.key = key
    }
  }

  /** Releases the retained layer, if any. Safe to call repeatedly. */
  fun release() {
    layer?.takeUnless { it.isReleased }?.let { owner?.releaseGraphicsLayer(it) }
    layer = null
    owner = null
    key = 0L
  }
}
