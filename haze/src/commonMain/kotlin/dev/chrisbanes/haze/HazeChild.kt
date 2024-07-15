// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateSubtree
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize

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
): Modifier = this then HazeChildNodeElement(state, shape, style)

@Deprecated(
  "Deprecated. Replaced with new HazeStyle object",
  ReplaceWith("hazeChild(state, shape, HazeStyle(tint, blurRadius, noiseFactor))"),
)
fun Modifier.hazeChild(
  state: HazeState,
  shape: Shape = RectangleShape,
  tint: Color = Color.Unspecified,
  blurRadius: Dp = Dp.Unspecified,
  noiseFactor: Float = Float.MIN_VALUE,
): Modifier = hazeChild(state, shape, HazeStyle(tint, blurRadius, noiseFactor))

private data class HazeChildNodeElement(
  val state: HazeState,
  val shape: Shape,
  val style: HazeStyle,
) : ModifierNodeElement<HazeChildNode>() {
  override fun create(): HazeChildNode = HazeChildNode(state, shape, style)

  override fun update(node: HazeChildNode) {
    node.state = state
    node.shape = shape
    node.style = style
    node.update()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "HazeChild"
    properties["shape"] = shape
    properties["style"] = style
  }
}

private data class HazeChildNode(
  override var state: HazeState,
  var shape: Shape,
  var style: HazeStyle,
) : HazeEffectNode() {

  private val area: HazeArea by lazy {
    HazeArea(shape = shape, style = style)
  }

  private var attachedState: HazeState? = null

  override fun onAttach() {
    attachToHazeState()
    super.onAttach()
  }

  override fun update() {
    // Propagate any shape changes to the HazeArea
    area.shape = shape
    area.style = style

    if (state != attachedState) {
      // The provided HazeState has changed, so we need to detach from the old one,
      // and attach to the new one
      detachFromHazeState()
      attachToHazeState()
    }

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

  override fun onDetach() {
    super.onDetach()
    detachFromHazeState()
  }

  override fun ContentDrawScope.draw() {
    if (effects.isEmpty() || state.renderMode != RenderMode.CHILD) {
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
      drawEffectsWithScrim()
    } else {
      val contentLayer = requireNotNull(state.contentLayer)
      // Now we draw each effect
      drawEffectsWithGraphicsLayer(contentLayer)
      // Finally we draw the content
      drawContent()
    }
  }

  override fun calculateUpdatedHazeEffects(): List<HazeEffect> {
    if (state.renderMode == RenderMode.CHILD) {
      val currentEffects = effects.associateByTo(mutableMapOf(), HazeEffect::area)

      return sequenceOf(area)
        .filter { it.isValid }
        .map { area ->
          // We re-use any current effects, otherwise we need to create a new one
          currentEffects.remove(area) ?: HazeEffect(area = area)
        }
        .toList()
    }

    return emptyList()
  }

  private fun attachToHazeState() {
    state.registerArea(area)
    attachedState = state

    // We need to trigger a layout so that we get the initial size. This is important for when
    // the modifier is added after layout has settled down (such as conditional modifiers).
    // invalidateSubtree() is a big hammer, but it's the only tool we have.
    invalidateSubtree()
  }

  private fun detachFromHazeState() {
    attachedState?.unregisterArea(area)
    attachedState = null
  }
}
