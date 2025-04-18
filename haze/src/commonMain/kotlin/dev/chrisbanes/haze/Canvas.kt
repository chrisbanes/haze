// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalGraphicsContext

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

internal fun CompositionLocalConsumerModifierNode.withGraphicsLayer(block: (GraphicsLayer) -> Unit) {
  val graphicsContext = currentValueOf(LocalGraphicsContext)
  val layer = graphicsContext.createGraphicsLayer()
  try {
    block(layer)
  } finally {
    graphicsContext.releaseGraphicsLayer(layer)
  }
}

internal inline fun DrawScope.withAlpha(
  alpha: Float,
  node: CompositionLocalConsumerModifierNode,
  crossinline block: DrawScope.() -> Unit,
) {
  if (alpha < 1f) {
    node.withGraphicsLayer { layer ->
      layer.alpha = alpha
      layer.record { block() }
      drawLayer(layer)
    }
  } else {
    block()
  }
}
