// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("NOTHING_TO_INLINE")

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

internal fun LayoutCoordinates.positionForHaze(
  strategy: HazePositionStrategy,
): Offset = when (strategy) {
  HazePositionStrategy.Local, HazePositionStrategy.Auto -> positionInRoot()
  HazePositionStrategy.Screen -> positionForHazeScreen()
}

/**
 * Returns screen-level coordinates. This is `expect`/`actual` because the appropriate API
 * differs by platform: Android uses `positionOnScreen()` while Skiko uses `positionInWindow()`
 * (there is no screen concept on desktop).
 */
internal expect fun LayoutCoordinates.positionForHazeScreen(): Offset

internal expect fun CompositionLocalConsumerModifierNode.getWindowId(): Any?

internal inline fun <T> T.letIf(condition: Boolean, block: (T) -> T): T {
  return if (condition) block(this) else this
}
