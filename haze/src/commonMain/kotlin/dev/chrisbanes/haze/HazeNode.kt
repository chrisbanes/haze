// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.withSave
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
  var renderMode: RenderMode,
) : HazeEffectNode(), DrawModifierNode {

  override fun update() {
    super.update()

    state.content.style = defaultStyle
    state.renderMode = renderMode
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    super.onPlaced(coordinates)

    state.content.style = defaultStyle
    state.content.size = coordinates.size.toSize()
    state.content.positionOnScreen = positionOnScreen
  }

  override fun ContentDrawScope.draw() {
    if (effects.isEmpty()) {
      // If we don't have any effects, just call drawContent and return early
      drawContent()
      return
    }

    // First we need to make sure that the effects are updated (if necessary)
    for (effect in effects) {
      effect.onPreDraw(layoutDirection, drawContext.density)
    }

    if (!useGraphicsLayers()) {
      // If we're not using graphics layers, our code path is much simpler.
      // We just draw the content directly to the canvas, and then draw each effect over it
      drawContent()

      if (renderMode == RenderMode.PARENT) {
        drawEffectsWithScrim()
      }
      return
    }

    val graphicsContext = currentValueOf(LocalGraphicsContext)
    val contentLayer = graphicsContext.createGraphicsLayer()

    // First we draw the composable content into a graphics layer
    contentLayer.record(size = size.roundToIntSize()) {
      this@draw.drawContent()
    }

    // Now we draw `contentNode` into the window canvas, clipping any effect areas
    // (they will be drawn on top)
    with(drawContext.canvas) {
      withSave {
        if (renderMode == RenderMode.PARENT) {
          // We add all the clip outs to the canvas (Canvas will combine them)
          for (effect in effects) {
            clipShape(effect.shape, effect.contentClipBounds, ClipOp.Difference) {
              effect.getUpdatedContentClipPath(layoutDirection, drawContext.density)
            }
          }
        }
        // Then we draw the content layer
        drawLayer(contentLayer)
      }
    }

    if (renderMode == RenderMode.PARENT) {
      drawEffectsWithGraphicsLayer(contentLayer)
      graphicsContext.releaseGraphicsLayer(contentLayer)
    } else {
      state.contentLayer?.let { old ->
        graphicsContext.releaseGraphicsLayer(old)
      }
      state.contentLayer = contentLayer
    }
  }

  override fun onDetach() {
    super.onDetach()

    state.contentLayer?.let { old ->
      currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(old)
      state.contentLayer = null
    }
  }

  override fun calculateUpdatedHazeEffects(): List<HazeEffect> {
    val currentEffects = effects.associateByTo(mutableMapOf(), HazeEffect::area)

    return state.areas.asSequence()
      .filter { it.isValid }
      .map { area ->
        // We re-use any current effects, otherwise we need to create a new one
        currentEffects.remove(area) ?: HazeEffect(area = area)
      }
      .toList()
  }
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
