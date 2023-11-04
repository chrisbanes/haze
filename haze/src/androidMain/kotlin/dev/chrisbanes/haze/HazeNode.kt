// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp

internal actual fun createHazeNode(
  areas: List<RoundRect>,
  backgroundColor: Color,
  tint: Color,
  blurRadius: Dp,
  noiseFactor: Float,
): HazeNode = AndroidHazeNode(
  areas = areas,
  backgroundColor = backgroundColor,
  tint = tint,
  blurRadius = blurRadius,
  noiseFactor = noiseFactor,
)

/**
 * With CMP + Android, we can't do much other than display a transparent scrim.
 * See `:haze-jetpack-compose` for a working blur on Android, but we need Compose 1.6.0 APIs,
 * which are not available in CMP (yet).
 */
private class AndroidHazeNode(
  areas: List<RoundRect>,
  backgroundColor: Color,
  tint: Color,
  blurRadius: Dp,
  noiseFactor: Float,
) : HazeNode(
  areas,
  backgroundColor,
  tint,
  blurRadius,
  noiseFactor,
),
  DrawModifierNode {
  override fun ContentDrawScope.draw() {
    drawContent()

    val path = Path()
    for (area in areas) {
      path.addRoundRect(area)
    }
    // We need to boost the alpha as we don't have a blur effect
    drawPath(
      path = path,
      color = tint.copy(alpha = (tint.alpha * 1.35f).coerceAtMost(1f)),
    )
  }
}
