// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.toSize

internal class HazeNode(
  var state: HazeState,
  var defaultStyle: HazeStyle,
) : Modifier.Node(),
  CompositionLocalConsumerModifierNode,
  LayoutAwareModifierNode,
  GlobalPositionAwareModifierNode,
  DrawModifierNode {

  fun update() {
    state.contentArea.style = defaultStyle
  }

  override fun onAttach() {
    update()
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) = onPlaced(coordinates)

  override fun onPlaced(coordinates: LayoutCoordinates) {
    Snapshot.withMutableSnapshot {
      state.contentArea.apply {
        size = coordinates.size.toSize()
        positionOnScreen = coordinates.positionInWindow() + calculateWindowOffset()
      }
    }
  }

  override fun ContentDrawScope.draw() {
    if (!USE_GRAPHICS_LAYERS) {
      // If we're not using graphics layers, just call drawContent and return early
      drawContent()
      return
    }

    val graphicsContext = currentValueOf(LocalGraphicsContext)

    val contentLayer = state.contentLayer ?: graphicsContext.createGraphicsLayer()
    state.contentLayer = contentLayer

    // First we draw the composable content into a graphics layer
    contentLayer.record {
      this@draw.drawContent()
    }

    // Now we draw `content` into the window canvas
    drawLayer(contentLayer)
  }

  override fun onDetach() {
    super.onDetach()

    state.contentLayer?.let { layer ->
      currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(layer)
    }
    state.contentLayer = null
  }
}

internal expect val USE_GRAPHICS_LAYERS: Boolean
