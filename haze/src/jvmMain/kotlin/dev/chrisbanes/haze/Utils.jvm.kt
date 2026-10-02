// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun CompositionLocalConsumerModifierNode.getWindowId(): Any? {
  return try {
    currentValueOf(LocalAwtWindow)
  } catch (_: IllegalStateException) {
    null
  }
}
