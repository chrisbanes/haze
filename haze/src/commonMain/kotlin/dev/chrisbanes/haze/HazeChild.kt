// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
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
    node.onUpdate()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "HazeChild"
    properties["shape"] = shape
    properties["style"] = style
  }
}

private data class HazeChildNode(
  var state: HazeState,
  var shape: Shape,
  var style: HazeStyle,
) : Modifier.Node(),
  LayoutAwareModifierNode,
  CompositionLocalConsumerModifierNode {

  private val area: HazeArea by lazy {
    HazeArea(shape = shape, style = style)
  }

  private var attachedState: HazeState? = null

  override fun onAttach() {
    attachToHazeState()
  }

  fun onUpdate() {
    // Propagate any shape changes to the HazeArea
    area.shape = shape
    area.style = style

    if (state != attachedState) {
      // The provided HazeState has changed, so we need to detach from the old one,
      // and attach to the new one
      detachFromHazeState()
      attachToHazeState()
    }
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    // After we've been placed, update the state with our new bounds (in 'screen' coordinates)
    area.positionOnScreen = coordinates.positionInWindow() + calculateWindowOffset()
    area.size = coordinates.size.toSize()
  }

  override fun onDetach() {
    detachFromHazeState()
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
