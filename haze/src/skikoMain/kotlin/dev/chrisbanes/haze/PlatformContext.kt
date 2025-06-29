// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import kotlin.jvm.JvmField

internal actual abstract class PlatformContext private constructor() {
  companion object {
    @JvmField val INSTANCE = object : PlatformContext() {}
  }
}

internal actual fun CompositionLocalConsumerModifierNode.requirePlatformContext(): PlatformContext {
  return PlatformContext.INSTANCE
}
