// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlin.jvm.JvmField

/** Experimental process-wide runtime feature switches. */
@ExperimentalHazeApi
public object HazeFeatureFlags {

  /**
   * Whether built-in effects may use a platform window-backdrop renderer.
   *
   * This value is read when a Haze effect modifier node is attached. Changing it does not alter
   * nodes that are already attached; reattach a node for it to observe the new value. Enabling the
   * flag only makes native rendering eligible. Platform, window, canvas, effect, setup, and draw
   * checks can still select the configured source fallback, which remains the portable behavior.
   * The default is `false` while the platform implementation is experimental.
   */
  @JvmField
  public var isPlatformBackdropEnabled: Boolean = false
}
