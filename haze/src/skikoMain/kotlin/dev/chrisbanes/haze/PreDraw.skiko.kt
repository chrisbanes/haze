// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import kotlinx.coroutines.DisposableHandle

internal actual fun CompositionLocalConsumerModifierNode.doOnPreDraw(
  listener: OnPreDrawListener,
): DisposableHandle {
  return DisposableHandle {
    // no-op
  }
}
