// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView

internal actual fun CompositionLocalConsumerModifierNode.getWindowId(): Any? {
  return currentValueOf(LocalView).windowId
}
