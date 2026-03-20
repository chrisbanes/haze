// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

/**
 * Strategy for how Haze calculates positions of source and effect nodes.
 *
 * @see HazeState.positionStrategy
 */
public enum class HazePositionStrategy {
  /**
   * Uses `positionInRoot()` coordinates.
   *
   * This works correctly when source and effect are in the same composition root
   * (same window). This is the most common case and handles split-window modes
   * (e.g. Huawei Parallel Space) correctly.
   *
   * Does **not** work for cross-window scenarios like dialogs or popups.
   */
  Local,

  /**
   * Uses screen-level coordinates (`positionOnScreen()`) on all platforms.
   *
   * Required when source and effect are in different windows (e.g. dialogs, popups).
   */
  Screen,

  /**
   * The default strategy. Uses [Local] coordinates, but automatically promotes to
   * [Screen] when it detects that source areas are in a different window than the
   * effect node.
   */
  Auto,
}
