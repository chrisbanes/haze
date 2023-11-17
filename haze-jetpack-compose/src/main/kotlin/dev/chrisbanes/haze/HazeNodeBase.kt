// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.unit.Dp

/**
 * On older platforms, we draw a translucent scrim over the content
 */
internal class HazeNodeBase(
  state: HazeState,
  backgroundColor: Color,
  tint: Color,
  blurRadius: Dp,
  noiseFactor: Float,
) : HazeNode(state, backgroundColor, tint, blurRadius, noiseFactor),
  DrawModifierNode,
  ObserverModifierNode,
  LayoutAwareModifierNode {

  private val path = Path()
  private var pathDirty = false
  private var boundsInRoot = Rect.Zero

  override fun onUpdate() {
    invalidateDraw()
  }

  override fun onObservedReadsChanged() {
    markPathAsDirty()
  }

  private fun markPathAsDirty() {
    pathDirty = true
    invalidateDraw()
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    val newBoundsInRoot = coordinates.boundsInRoot()
    if (boundsInRoot != newBoundsInRoot) {
      boundsInRoot = newBoundsInRoot
      markPathAsDirty()
    }
  }

  override fun ContentDrawScope.draw() {
    if (pathDirty) {
      observeReads { updatePath() }
    }

    drawContent()

    drawPath(
      path = path,
      // We need to boost the alpha as we don't have a blur effect
      color = tint.copy(alpha = (tint.alpha * 1.35f).coerceAtMost(1f)),
    )
  }

  private fun updatePath() {
    path.reset()
    for (rect in state.areasInLocal(boundsInRoot)) {
      path.addRoundRect(rect)
    }
    pathDirty = false
  }
}
