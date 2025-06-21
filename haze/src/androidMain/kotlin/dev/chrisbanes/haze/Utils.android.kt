// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView

/**
 * We use positionOnScreen on Android, to support dialogs, popup windows, etc.
 */
internal actual fun LayoutCoordinates.positionForHaze(): Offset = positionOnScreen()

internal actual fun CompositionLocalConsumerModifierNode.getWindowId(): Any? {
  return currentValueOf(LocalView).windowId
}
