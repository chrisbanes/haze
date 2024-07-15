// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

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
import androidx.compose.ui.unit.roundToIntSize
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
    state.content.style = defaultStyle
  }

  override fun onAttach() {
    update()
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) = onPlaced(coordinates)

  override fun onPlaced(coordinates: LayoutCoordinates) {
    state.content.apply {
      size = coordinates.size.toSize()
      positionOnScreen = coordinates.positionInWindow() + calculateWindowOffset()
    }
  }

  override fun ContentDrawScope.draw() {
    val graphicsContext = currentValueOf(LocalGraphicsContext)

    state.contentLayer?.let { graphicsContext.releaseGraphicsLayer(it) }
    state.contentLayer = null

    if (!USE_GRAPHICS_LAYERS) {
      // If we're not using graphics layers, just call drawContent and return early
      drawContent()
      return
    }

    val contentLayer = graphicsContext.createGraphicsLayer()

    // First we draw the composable content into a graphics layer
    contentLayer.record(size = size.roundToIntSize()) {
      this@draw.drawContent()
    }

    // Now we draw `content` into the window canvas
    drawLayer(contentLayer)

    // Otherwise we need to stuff the content graphics layer into the HazeState
    state.contentLayer = contentLayer
  }

  override fun onDetach() {
    super.onDetach()

    state.contentLayer?.let { old ->
      currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(old)
      state.contentLayer = null
    }
  }
}

internal expect val USE_GRAPHICS_LAYERS: Boolean
