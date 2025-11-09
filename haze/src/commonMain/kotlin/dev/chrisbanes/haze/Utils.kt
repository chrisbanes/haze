// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("NOTHING_TO_INLINE")

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import kotlin.math.ceil
import kotlin.math.roundToInt

internal expect fun LayoutCoordinates.positionForHaze(): Offset

internal expect fun CompositionLocalConsumerModifierNode.getWindowId(): Any?

@InternalHazeApi
public fun ceil(size: Size): Size = Size(width = ceil(size.width), height = ceil(size.height))

@InternalHazeApi
public fun Offset.round(): Offset = Offset(x.roundToInt().toFloat(), y.roundToInt().toFloat())

internal inline fun <T> T.letIf(condition: Boolean, block: (T) -> T): T {
  return if (condition) block(this) else this
}
