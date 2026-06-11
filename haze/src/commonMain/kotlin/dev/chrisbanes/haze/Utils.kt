// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("NOTHING_TO_INLINE")

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

/**
 * Returns [LayoutCoordinates.positionOnScreen] if it succeeds, or [Offset.Unspecified] if it
 * throws. [positionOnScreen] can throw (e.g. on certain Skiko backends or when a layout is
 * detached); callers should always route through this helper rather than catching at the call
 * site, to keep error policy consistent.
 *
 * We catch [Exception] deliberately — not [Throwable] — so that [OutOfMemoryError] and other
 * JVM errors propagate rather than being silently swallowed as an "unspecified position".
 */
internal fun LayoutCoordinates.safePositionOnScreen(): Offset = try {
  positionOnScreen()
} catch (_: Exception) {
  Offset.Unspecified
}

internal fun LayoutCoordinates.positionForHaze(
  strategy: HazePositionStrategy,
): Offset = when (strategy) {
  HazePositionStrategy.Local, HazePositionStrategy.Auto -> positionInRoot()
  HazePositionStrategy.Screen -> safePositionOnScreen()
}

internal expect fun CompositionLocalConsumerModifierNode.getWindowId(): Any?

internal inline fun <T> T.letIf(condition: Boolean, block: (T) -> T): T {
  return if (condition) block(this) else this
}
