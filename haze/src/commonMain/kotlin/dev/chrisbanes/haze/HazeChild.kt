// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.launch

/**
 * Mark this composable as being a Haze child composable.
 *
 * This will update the given [HazeState] whenever the layout is placed, enabling any layouts using
 * [Modifier.haze] to blur any content behind the host composable.
 *
 * @param shape The shape of the content. This will affect the the bounds and outline of
 * the content. Please be aware that using non-rectangular shapes has an effect on performance,
 * since we need to use path clipping.
 * @param style The [HazeStyle] to use on this content. Any specified values in the given
 * style will override that value from the default style, provided to [haze].
 */
fun Modifier.hazeChild(
  state: HazeState,
  shape: Shape = RectangleShape,
  style: HazeStyle = HazeStyle.Unspecified,
  mask: Brush? = null,
): Modifier = this then HazeChildNodeElement(state, shape, style, mask)

private data class HazeChildNodeElement(
  val state: HazeState,
  val shape: Shape,
  val style: HazeStyle,
  val mask: Brush?,
) : ModifierNodeElement<HazeChildNode>() {
  override fun create(): HazeChildNode = HazeChildNode(state, shape, style, mask)

  override fun update(node: HazeChildNode) {
    node.state = state
    node.shape = shape
    node.style = style
    node.mask = mask
    node.update()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "HazeChild"
    properties["shape"] = shape
    properties["style"] = style
    properties["mask"] = mask
  }
}

private class HazeChildNode(
  override var state: HazeState,
  var shape: Shape,
  var style: HazeStyle,
  var mask: Brush?,
) : HazeEffectNode() {

  private val area: HazeArea by lazy {
    HazeArea(shape = shape, style = style, mask = mask)
  }

  private var drawWithoutContentLayerCount = 0

  override fun update() {
    // Propagate any changes to the HazeArea
    area.shape = shape
    area.style = style
    area.mask = mask

    super.update()
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    super.onPlaced(coordinates)

    // After we've been placed, update the state with our new bounds (in 'screen' coordinates)
    area.positionOnScreen = positionOnScreen
    area.size = coordinates.size.toSize()
  }

  override fun onReset() {
    area.reset()
  }

  override fun ContentDrawScope.draw() {
    if (effects.isEmpty()) {
      // If we don't have any effects, just call drawContent and return early
      drawContent()
      return
    }

    // First we need to make sure that the effects are updated (if necessary)
    for (effect in effects) {
      effect.onPreDraw(drawContext.density)
    }

    if (USE_GRAPHICS_LAYERS) {
      val contentLayer = state.contentLayer
      if (contentLayer != null) {
        drawWithoutContentLayerCount = 0
        drawEffectsWithGraphicsLayer(contentLayer)
      } else {
        // The content layer has not have been drawn yet (draw order matters here). If it hasn't
        // there's not much we do other than invalidate and wait for the next frame.
        // We only want to force a few frames, otherwise we're causing a draw loop.
        if (++drawWithoutContentLayerCount <= 2) {
          coroutineScope.launch { invalidateDraw() }
        }
      }
    } else {
      drawEffectsWithScrim()
    }

    // Finally we draw the content
    drawContent()
  }

  override fun calculateHazeAreas(): Sequence<HazeArea> = sequenceOf(area)
}
