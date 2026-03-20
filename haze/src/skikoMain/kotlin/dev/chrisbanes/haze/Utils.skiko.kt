// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

internal actual fun LayoutCoordinates.positionForHazeScreen(): Offset = try {
  positionInWindow()
} catch (t: Throwable) {
  Offset.Unspecified
}

internal actual fun CompositionLocalConsumerModifierNode.getWindowId(): Any? = null
