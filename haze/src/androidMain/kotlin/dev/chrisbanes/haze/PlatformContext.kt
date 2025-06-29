// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.content.Context
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext

internal actual typealias PlatformContext = Context

internal actual fun CompositionLocalConsumerModifierNode.requirePlatformContext(): PlatformContext {
  return currentValueOf(LocalContext)
}
