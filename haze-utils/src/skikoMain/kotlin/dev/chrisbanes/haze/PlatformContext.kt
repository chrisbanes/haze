// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import kotlin.jvm.JvmField

@InternalHazeApi
public actual abstract class PlatformContext private constructor() {
  public companion object {
    @JvmField public val INSTANCE: PlatformContext = object : PlatformContext() {}
  }
}

@InternalHazeApi
public actual fun CompositionLocalConsumerModifierNode.requirePlatformContext(): PlatformContext {
  return PlatformContext.INSTANCE
}
