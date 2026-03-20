// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Stable

/**
 * Strategy for how Haze calculates positions of source and effect nodes.
 *
 * @see HazeState.positionStrategy
 */
@Stable
public sealed interface HazePositionStrategy {
  /**
   * Uses `positionInRoot()` coordinates.
   *
   * This works correctly when source and effect are in the same composition root
   * (same window). This is the most common case and handles split-window modes
   * (e.g. Huawei Parallel Space) correctly.
   *
   * Does **not** work for cross-window scenarios like dialogs or popups.
   */
  public data object Local : HazePositionStrategy

  /**
   * Uses screen-level coordinates (`positionOnScreen()` on Android,
   * `positionInWindow()` on Desktop/Skiko).
   *
   * Required when source and effect are in different windows (e.g. dialogs, popups).
   */
  public data object Screen : HazePositionStrategy

  /**
   * The default strategy. Uses [Local] coordinates, but automatically promotes to
   * [Screen] when it detects that source areas are in a different window than the
   * effect node.
   */
  public data object Auto : HazePositionStrategy
}
