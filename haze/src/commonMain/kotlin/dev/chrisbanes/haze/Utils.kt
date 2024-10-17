package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.hypot

internal fun calculateLength(
  start: Offset,
  end: Offset,
  size: Size,
): Float {
  val (startX, startY) = start
  val endX = end.x.coerceAtMost(size.width)
  val endY = end.y.coerceAtMost(size.height)
  return hypot(endX - startX, endY - startY)
}

internal fun Size.expand(expansion: Float): Size {
  return Size(width = width + expansion, height = height + expansion)
}

internal fun lerp(start: Float, stop: Float, fraction: Float): Float {
  return start + fraction * (stop - start)
}
