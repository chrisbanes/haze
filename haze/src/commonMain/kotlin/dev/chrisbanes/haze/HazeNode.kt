// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.toSize

internal class HazeNode(
  override var state: HazeState,
  var defaultStyle: HazeStyle,
) : HazeEffectNode(), DrawModifierNode {

  override fun update() {
    super.update()
    state.content.style = defaultStyle
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    super.onPlaced(coordinates)

    state.content.style = defaultStyle
    state.content.size = coordinates.size.toSize()
    state.content.positionOnScreen = positionOnScreen
  }

  override fun ContentDrawScope.draw() {
    val graphicsContext = currentValueOf(LocalGraphicsContext)

    state.contentLayer?.let { graphicsContext.releaseGraphicsLayer(it) }
    state.contentLayer = null

    if (effects.isEmpty() || !useGraphicsLayers()) {
      // If we don't have any effects, or we're not using graphics layers,
      // just call drawContent and return early
      drawContent()
      return
    }

    // First we need to make sure that the effects are updated (if necessary)
    for (effect in effects) {
      effect.onPreDraw(layoutDirection, drawContext.density)
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

  override fun calculateHazeAreas(): List<HazeArea> = state.areas
}

internal expect fun HazeEffectNode.createRenderEffect(
  effect: HazeEffect,
  density: Density,
): RenderEffect?

internal expect fun HazeEffectNode.useGraphicsLayers(): Boolean

internal expect fun HazeEffectNode.drawEffect(
  drawScope: DrawScope,
  effect: HazeEffect,
  graphicsLayer: GraphicsLayer? = null,
)
