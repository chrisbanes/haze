// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

internal data class CornerRadii(
  val topLeft: Float,
  val topRight: Float,
  val bottomRight: Float,
  val bottomLeft: Float,
) {
  fun isZero(): Boolean =
    topLeft == 0f &&
      topRight == 0f &&
      bottomRight == 0f &&
      bottomLeft == 0f
}

internal fun RoundedCornerShape.toCornerRadiiPx(
  layerSize: Size,
  density: Density,
  layoutDirection: LayoutDirection,
): CornerRadii {
  val topStartPx = topStart.toPx(layerSize, density)
  val topEndPx = topEnd.toPx(layerSize, density)
  val bottomEndPx = bottomEnd.toPx(layerSize, density)
  val bottomStartPx = bottomStart.toPx(layerSize, density)

  return if (layoutDirection == LayoutDirection.Ltr) {
    CornerRadii(
      topLeft = topStartPx,
      topRight = topEndPx,
      bottomRight = bottomEndPx,
      bottomLeft = bottomStartPx,
    )
  } else {
    CornerRadii(
      topLeft = topEndPx,
      topRight = topStartPx,
      bottomRight = bottomStartPx,
      bottomLeft = bottomEndPx,
    )
  }
}

internal fun CornerRadii.toRoundRect(size: Size): RoundRect = RoundRect(
  left = 0f,
  top = 0f,
  right = size.width,
  bottom = size.height,
  topLeftCornerRadius = CornerRadius(topLeft),
  topRightCornerRadius = CornerRadius(topRight),
  bottomRightCornerRadius = CornerRadius(bottomRight),
  bottomLeftCornerRadius = CornerRadius(bottomLeft),
)

internal fun CornerRadii.toPath(size: Size): Path = Path().apply { addRoundRect(toRoundRect(size)) }
