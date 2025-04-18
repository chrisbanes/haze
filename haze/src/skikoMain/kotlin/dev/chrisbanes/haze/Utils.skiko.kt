// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

/**
 * We only look at window coordinates on Desktop. There's currently no external windows used
 * on Skiko platforms (AFAIK), so there's no need to look at screen coordinates.
 */
internal actual fun LayoutCoordinates.positionForHaze(): Offset = try {
  positionInWindow()
} catch (t: Throwable) {
  Offset.Unspecified
}

actual fun CompositionLocalConsumerModifierNode.getWindowId(): Any? = null
