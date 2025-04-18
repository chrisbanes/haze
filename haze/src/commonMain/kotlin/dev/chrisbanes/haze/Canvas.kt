// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.withSaveLayer

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

internal inline fun DrawScope.withAlpha(
  alpha: Float,
  rect: Rect = size.toRect(),
  block: DrawScope.() -> Unit,
) {
  if (alpha < 1f) {
    PaintPool.usePaint { paint ->
      paint.alpha = alpha
      drawContext.canvas.withSaveLayer(rect, paint) {
        block()
      }
    }
  } else {
    block()
  }
}
