// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

fun Modifier.hazeChild(
  key: Any,
  state: HazeState,
): Modifier = this then HazeChildNodeElement(key, state)

private data class HazeChildNodeElement(
  val key: Any,
  val state: HazeState,
) : ModifierNodeElement<HazeChildNode>() {
  override fun create(): HazeChildNode = HazeChildNode(key, state)

  override fun update(node: HazeChildNode) {
    node.key = key
    node.state = state
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "HazeChild"
    properties["key"] = key
  }
}

private data class HazeChildNode(
  var key: Any,
  var state: HazeState,
) : Modifier.Node(), LayoutAwareModifierNode {
  override fun onPlaced(coordinates: LayoutCoordinates) {
    state.areas[key] = RoundRect(coordinates.boundsInRoot())
  }
}
