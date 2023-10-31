// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp

/**
 * On older platforms, we draw a translucent scrim over the content
 */
internal class HazeNodeBase(
  private var areas: List<Rect>,
  private var backgroundColor: Color,
  private var tint: Color,
  private var blurRadius: Dp,
) : HazeNode(), DrawModifierNode {

  override fun update(
    areas: List<Rect>,
    backgroundColor: Color,
    tint: Color,
    blurRadius: Dp,
  ) {
    this.areas = areas
    this.backgroundColor = backgroundColor
    this.tint = tint
    this.blurRadius = blurRadius
  }

  override fun ContentDrawScope.draw() {
    drawContent()

    for (area in areas) {
      // We need to boost the alpha as we don't have a blur effect
      drawRect(
        color = tint.copy(alpha = (tint.alpha * 1.35f).coerceAtMost(1f)),
        topLeft = area.topLeft,
        size = area.size,
      )
    }
  }
}
