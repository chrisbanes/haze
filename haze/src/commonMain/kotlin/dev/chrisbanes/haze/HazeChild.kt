// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/**
 * Mark this composable as being a Haze child composable.
 *
 * This will update the given [HazeState] whenever the layout is placed, enabling any layouts using
 * [Modifier.haze] to blur any content behind the host composable.
 */
fun Modifier.hazeChild(
  key: Any,
  state: HazeState,
  shape: Shape = RectangleShape,
): Modifier = this then HazeChildNodeElement(key, state, shape)

private data class HazeChildNodeElement(
  val key: Any,
  val state: HazeState,
  val shape: Shape,
) : ModifierNodeElement<HazeChildNode>() {
  override fun create(): HazeChildNode = HazeChildNode(key, state, shape)

  override fun update(node: HazeChildNode) {
    node.key = key
    node.state = state
    node.shape = shape
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "HazeChild"
    properties["key"] = key
    properties["shape"] = shape
  }
}

private data class HazeChildNode(
  var key: Any,
  var state: HazeState,
  var shape: Shape,
) : Modifier.Node(), LayoutAwareModifierNode {
  override fun onPlaced(coordinates: LayoutCoordinates) {
    // After we've been placed, update the state with our new bounds (in root coordinates)
    val positionInRoot = coordinates.positionInRoot()
    val size = coordinates.size
    val bounds = Rect(
      left = positionInRoot.x,
      top = positionInRoot.y,
      right = positionInRoot.x + size.width,
      bottom = positionInRoot.y + size.height,
    )
    state.updateArea(key, bounds, shape)
  }

  override fun onReset() {
    state.clearArea(key)
  }

  override fun onDetach() {
    state.clearArea(key)
  }
}
