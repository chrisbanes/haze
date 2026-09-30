// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

internal fun calculateLength(
  start: Offset,
  end: Offset,
  size: Size,
): Float {
  // Match Compose brushes: only positive infinity is relative to the drawing bounds.
  val startX = if (start.x == Float.POSITIVE_INFINITY) size.width else start.x
  val startY = if (start.y == Float.POSITIVE_INFINITY) size.height else start.y
  val endX = if (end.x == Float.POSITIVE_INFINITY) size.width else end.x
  val endY = if (end.y == Float.POSITIVE_INFINITY) size.height else end.y
  return hypot(endX - startX, endY - startY)
}

internal fun Size.expand(expansionWidth: Float, expansionHeight: Float): Size {
  return Size(width = width + expansionWidth, height = height + expansionHeight)
}

internal fun ceil(size: Size): Size = Size(width = ceil(size.width), height = ceil(size.height))

internal fun Offset.round(): Offset = Offset(x.roundToInt().toFloat(), y.roundToInt().toFloat())
