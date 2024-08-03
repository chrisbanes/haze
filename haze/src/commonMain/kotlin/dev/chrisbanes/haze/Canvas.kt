// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer

internal inline fun GraphicsContext.useGraphicsLayer(block: (GraphicsLayer) -> Unit) {
  val layer = createGraphicsLayer()
  try {
    block(layer)
  } finally {
    releaseGraphicsLayer(layer)
  }
}

inline fun DrawScope.translate(
  offset: Offset,
  block: DrawScope.() -> Unit,
) {
  if (offset.isFinite && offset != Offset.Zero) {
    translate(offset.x, offset.y, block)
  } else {
    block()
  }
}
