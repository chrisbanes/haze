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
   *
   * Once physical Android 37.2 acceptance is complete, a later release may flip the default while
   * retaining a temporary `false` escape hatch for regressions, then remove this experimental flag.
   */
  @JvmField
  public var isPlatformBackdropEnabled: Boolean = false

  /**
   * Whether `hazeSource` renders its captured content into an offscreen layer.
   *
   * Effects using [HazeInput.Sources] draw the captured content into their own layers. Without an
   * offscreen layer, Android's renderer replays the source's display list once for each effect
   * whenever the source changes. With one, the source renders once per frame and each effect
   * samples that result, at the cost of a source-sized texture.
   *
   * Measure before enabling it: on a Pixel 8a with four Glass effects over one source, it removed
   * most replays but did not reduce frame time, and used 18–36 MB more GPU memory. The value is
   * read each time a source records its content. The default is `false`.
   */
  @JvmField
  public var isOffscreenSourceEnabled: Boolean = false
}
