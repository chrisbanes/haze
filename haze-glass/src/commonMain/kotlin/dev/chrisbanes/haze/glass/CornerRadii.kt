// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

internal data class CornerRadii(
  val topLeft: Float,
  val topRight: Float,
  val bottomRight: Float,
  val bottomLeft: Float,
) {
  fun isZero(): Boolean = this == zero

  companion object {
    val zero: CornerRadii = CornerRadii(0f, 0f, 0f, 0f)
  }
}

internal operator fun CornerRadii.times(scale: Float): CornerRadii = CornerRadii(
  topLeft = topLeft * scale,
  topRight = topRight * scale,
  bottomRight = bottomRight * scale,
  bottomLeft = bottomLeft * scale,
)

internal fun RoundedCornerShape.toCornerRadiiPx(
  layerSize: Size,
  density: Density,
  layoutDirection: LayoutDirection,
): CornerRadii {
  var topStartPx = topStart.toPx(layerSize, density)
  var topEndPx = topEnd.toPx(layerSize, density)
  var bottomEndPx = bottomEnd.toPx(layerSize, density)
  var bottomStartPx = bottomStart.toPx(layerSize, density)
  val minDimension = min(layerSize.width, layerSize.height)

  if (topStartPx + bottomStartPx > minDimension) {
    val scale = minDimension / (topStartPx + bottomStartPx)
    topStartPx *= scale
    bottomStartPx *= scale
  }
  if (topEndPx + bottomEndPx > minDimension) {
    val scale = minDimension / (topEndPx + bottomEndPx)
    topEndPx *= scale
    bottomEndPx *= scale
  }
  require(topStartPx >= 0f && topEndPx >= 0f && bottomEndPx >= 0f && bottomStartPx >= 0f) {
    "Corner size in Px can't be negative(topStart = $topStartPx, topEnd = $topEndPx, " +
      "bottomEnd = $bottomEndPx, bottomStart = $bottomStartPx)!"
  }

  val physicalRadii = if (layoutDirection == LayoutDirection.Ltr) {
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

  var scale = edgeScale(layerSize.width, physicalRadii.topLeft, physicalRadii.topRight)
  scale = min(scale, edgeScale(layerSize.height, physicalRadii.topRight, physicalRadii.bottomRight))
  scale = min(scale, edgeScale(layerSize.width, physicalRadii.bottomRight, physicalRadii.bottomLeft))
  scale = min(scale, edgeScale(layerSize.height, physicalRadii.bottomLeft, physicalRadii.topLeft))

  if (scale == 1.0) return physicalRadii

  return CornerRadii(
    topLeft = (physicalRadii.topLeft.toDouble() * scale).toFloat(),
    topRight = (physicalRadii.topRight.toDouble() * scale).toFloat(),
    bottomRight = (physicalRadii.bottomRight.toDouble() * scale).toFloat(),
    bottomLeft = (physicalRadii.bottomLeft.toDouble() * scale).toFloat(),
  )
}

private fun edgeScale(limit: Float, first: Float, second: Float): Double {
  val doubleLimit = limit.toDouble()
  val sum = first.toDouble() + second.toDouble()
  return if (sum > doubleLimit) doubleLimit / sum else 1.0
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
