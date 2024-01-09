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
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.toSize

/**
 * Mark this composable as being a Haze child composable.
 *
 * This will update the given [HazeState] whenever the layout is placed, enabling any layouts using
 * [Modifier.haze] to blur any content behind the host composable.
 *
 * @param shape The shape of the content. This will affect the the bounds and outline of
 * the blurred content.
 * @param tint Color to tint the blurred content. Should be translucent, otherwise you will not see
 * the blurred content. If the provided color is [Color.Unspecified] then the default tint
 * provided to [haze] will be used.
 */
fun Modifier.hazeChild(
  state: HazeState,
  shape: Shape = RectangleShape,
  tint: Color = Color.Unspecified,
): Modifier = this then HazeChildNodeElement(state, shape, tint)

private data class HazeChildNodeElement(
  val state: HazeState,
  val shape: Shape,
  val tint: Color,
) : ModifierNodeElement<HazeChildNode>() {
  override fun create(): HazeChildNode = HazeChildNode(state, shape, tint)

  override fun update(node: HazeChildNode) {
    node.state = state
    node.shape = shape
    node.tint = tint
    node.onUpdate()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "HazeChild"
    properties["shape"] = shape
    properties["tint"] = tint
  }
}

private data class HazeChildNode(
  var state: HazeState,
  var shape: Shape,
  var tint: Color,
) : Modifier.Node(),
  LayoutAwareModifierNode,
  CompositionLocalConsumerModifierNode {

  private val area: HazeArea by lazy {
    HazeArea(shape = shape, tint = tint)
  }

  private var attachedState: HazeState? = null

  override fun onAttach() {
    attachToHazeState()
  }

  fun onUpdate() {
    // Propagate any shape changes to the HazeArea
    area.shape = shape
    area.tint = tint

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
  }

  private fun detachFromHazeState() {
    attachedState?.unregisterArea(area)
    attachedState = null
  }
}
