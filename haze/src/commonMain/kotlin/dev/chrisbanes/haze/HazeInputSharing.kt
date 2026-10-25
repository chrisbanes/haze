// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

/**
 * Controls whether a [hazeSource] renders once into an offscreen layer that it draws on screen
 * and that every effect sampling it through [HazeInput.Sources] shares.
 *
 * Without sharing, each effect draws the captured content into its own layers, so the renderer
 * replays the source's display list once per effect whenever the source changes. On Android, with
 * sharing, the source renders its content once into an offscreen layer, which it draws on screen
 * and every effect samples.
 *
 * Only Android reuses a render. On Desktop, iOS and Web, [Always] applies the clipping and the
 * offscreen pass described below, but the layer keeps no texture between draws, so the source is
 * still replayed for each consumer and it can cost more than [Never]. Haze has not measured this
 * path on those platforms.
 *
 * Sharing costs memory. On a Pixel 8a, each active full-screen source added about 25-33 MiB of
 * peak GPU memory, which is more than one source-sized RGBA8 buffer. The benchmark did not isolate
 * the cause, so budget well above one source-sized buffer per shared source.
 *
 * While sharing is active, the source's drawing is clipped to its layout bounds, expanded by the
 * `inputSharingOutsets` passed to [hazeSource]: anything drawn beyond them is no longer shown on
 * screen or in effects. Outsets do not preserve arbitrary overflow, so declare enough for what the
 * source draws outside its bounds, such as shadows. When sharing becomes inactive, the source draws
 * normally again, including any overflow.
 *
 * Sharing reduces the CPU cost of replaying an expensive source on a busy RenderThread, but the
 * offscreen layer is re-rendered whenever the source changes, which adds GPU work while it scrolls.
 * Sources default to [Never]; measure before opting a source in.
 *
 * Future minor releases may add entries. Keep an `else` branch when you match exhaustively with `when`.
 */
@ExperimentalHazeApi
public enum class HazeInputSharing {
  /**
   * Shares input on Android when two or more effects sample the source. On other platforms this
   * behaves like [Never].
   *
   * The source re-decides this on every draw. As effects come and go, crossing between one and two
   * consumers allocates or frees the source-sized layer and turns the source's overflow clipping on
   * or off. Use [Always] for a source that draws outside its bounds.
   *
   * Each member of a Glass container counts as its own consumer, even when its cluster samples the
   * source once. Two members over one source are therefore enough to share it.
   */
  Auto,

  /**
   * Shares input whenever at least one effect samples the source. Only Android reuses a render. On
   * Desktop, iOS and Web this clips the source and adds an offscreen pass without reuse, and can
   * cost more than [Never].
   */
  Always,

  /** Never shares input. Each effect replays the source's content. This is the default. */
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
