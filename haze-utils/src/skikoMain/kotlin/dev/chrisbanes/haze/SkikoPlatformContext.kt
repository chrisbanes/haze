// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:JvmName("PlatformContextKt")
@file:Suppress("DuplicateSourceClass")

package dev.chrisbanes.haze

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName

/** Stateless platform context used by Skiko-backed Haze renderers. */
@InternalHazeApi
public actual abstract class PlatformContext private constructor() {
  /** Provides the shared Skiko platform context. */
  public companion object {
    /** Shared Skiko platform context instance. */
    @JvmField public val INSTANCE: PlatformContext = object : PlatformContext() {}
  }
}

/** Returns the shared Skiko [PlatformContext]. */
@InternalHazeApi
public actual fun CompositionLocalConsumerModifierNode.requirePlatformContext(): PlatformContext {
  return PlatformContext.INSTANCE
}
