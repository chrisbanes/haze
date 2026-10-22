// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

/**
 * Controls whether effects that sample a [hazeSource] through [HazeInput.Sources] share one
 * rendered copy of its content.
 *
 * Without sharing, each effect draws the captured content into its own layers, so the renderer
 * replays the source's display list once per effect whenever the source changes. With sharing,
 * the source also renders its content once into an offscreen layer, which every effect samples.
 * That costs one texture the size of the source, and effects no longer see anything the source
 * draws outside its layout bounds. The source itself still draws normally on screen.
 */
@ExperimentalHazeApi
public enum class HazeInputSharing {
  /**
   * Shares input on Android when two or more effects sample the source. On other platforms this
   * behaves like [Never].
   */
  Auto,

  /** Shares input whenever at least one effect samples the source, on every platform. */
  Always,

  /** Never shares input. Each effect replays the source's content. */
  Never,
}

/** Whether a source with [consumerCount] capturing effects should share its input. */
@OptIn(ExperimentalHazeApi::class)
internal fun HazeInputSharing.isActive(
  consumerCount: Int,
  autoSupported: Boolean = isAutoInputSharingSupported(),
): Boolean = when (this) {
  HazeInputSharing.Auto -> autoSupported && consumerCount >= 2
  HazeInputSharing.Always -> consumerCount >= 1
  HazeInputSharing.Never -> false
}

/** Whether [HazeInputSharing.Auto] shares input on this platform. */
internal expect fun isAutoInputSharingSupported(): Boolean
