// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

fun Modifier.hazeChild(
  key: Any,
  hazeState: HazeState? = null,
): Modifier = this then HazeChildNodeElement(key, hazeState)

data class HazeChildNodeElement(
  val key: Any,
  val hazeState: HazeState?,
) : ModifierNodeElement<HazeChildNode>() {
  override fun create(): HazeChildNode = HazeChildNode(key, hazeState)

  override fun update(node: HazeChildNode) {
    node.key = key
    node.hazeState = hazeState
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "HazeChild"
    properties["key"] = key
  }
}

data class HazeChildNode(
  var key: Any,
  var hazeState: HazeState?,
) : Modifier.Node(), ModifierLocalModifierNode, LayoutAwareModifierNode {

  // TODO: need to think about layer changes (translationX, Y, etc)

  override fun onPlaced(coordinates: LayoutCoordinates) {
    val state = hazeState
      ?: ModifierLocalHazeState.current
      ?: error("HazeState not found for Modifier.hazeChild. Are you using Modifier.haze?")

    state.areas[key] = RoundRect(coordinates.boundsInRoot())
  }
}
