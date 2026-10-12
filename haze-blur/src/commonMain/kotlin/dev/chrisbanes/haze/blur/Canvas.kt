// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope

@OptIn(ExperimentalHazeApi::class)
internal inline fun DrawScope.withAlpha(
  alpha: Float,
  retainedLayer: RetainedGraphicsLayer,
  context: HazeEffectRuntimeDrawScope,
  crossinline block: DrawScope.() -> Unit,
) {
  if (alpha < 1f) {
    val layer = retainedLayer.acquire(context.requireGraphicsContext())
    layer.alpha = alpha
    layer.record { block() }
    drawLayer(layer)
  } else {
    retainedLayer.release()
    block()
  }
}
