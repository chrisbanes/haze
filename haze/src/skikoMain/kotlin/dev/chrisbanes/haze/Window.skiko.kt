// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

internal actual fun CompositionLocalConsumerModifierNode.calculateWindowOffset(): Offset {
  // The Skiko-backed platforms don't use native windows for dialogs, etc
  return Offset.Zero
}
