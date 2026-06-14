// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.uikit.LocalUIView
import platform.UIKit.UIView

internal actual fun CompositionLocalConsumerModifierNode.getWindowId(): Any? {
  return uikitWindowId(currentValueOf(LocalUIView))
}

internal fun uikitWindowId(view: UIView): Any = view
