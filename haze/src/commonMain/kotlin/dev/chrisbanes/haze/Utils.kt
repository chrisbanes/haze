// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("NOTHING_TO_INLINE")

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

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

internal fun Size.expand(expansionWidth: Float, expansionHeight: Float): Size {
  return Size(width = width + expansionWidth, height = height + expansionHeight)
}

internal fun lerp(start: Float, stop: Float, fraction: Float): Float {
  return start + fraction * (stop - start)
}

internal inline val Offset.orZero: Offset get() = takeOrElse { Offset.Zero }

internal inline fun <T> unsynchronizedLazy(noinline initializer: () -> T): Lazy<T> {
  return lazy(mode = LazyThreadSafetyMode.NONE, initializer)
}

internal expect fun LayoutCoordinates.positionForHaze(): Offset

internal expect fun CompositionLocalConsumerModifierNode.getWindowId(): Any?

internal fun ceil(size: Size): Size = Size(width = ceil(size.width), height = ceil(size.height))
internal fun Offset.round(): Offset = Offset(x.roundToInt().toFloat(), y.roundToInt().toFloat())

internal inline fun <T> T.letIf(condition: Boolean, block: (T) -> T): T {
  return if (condition) block(this) else this
}
