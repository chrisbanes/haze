// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.withGraphicsLayer

internal inline fun DrawScope.translate(
  offset: Offset,
  block: DrawScope.() -> Unit,
) {
  if (offset.isFinite && offset != Offset.Zero) {
    translate(offset.x, offset.y, block)
  } else {
    block()
  }
}

@OptIn(ExperimentalHazeApi::class)
internal inline fun DrawScope.withAlpha(
  alpha: Float,
  context: VisualEffectContext,
  crossinline block: DrawScope.() -> Unit,
) {
  if (alpha < 1f) {
    context.withGraphicsLayer { layer ->
      layer.alpha = alpha
      layer.record { block() }
      drawLayer(layer)
    }
  } else {
    block()
  }
}
