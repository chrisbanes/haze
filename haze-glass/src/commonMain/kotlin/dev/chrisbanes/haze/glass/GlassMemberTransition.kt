// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.ExperimentalHazeApi

/**
 * How a Glass member's material enters and leaves its [hazeGlassContainer].
 *
 * Members have no identifiers: a member's modifier node is its identity for as long as it stays
 * attached. Timing comes from the container's `transitionSpec` and `reducedMotionPolicy`. Outside
 * a container, the value is ignored.
 *
 * Haze animates only the glass. Callers animate member content themselves, for example by
 * wrapping the member in `AnimatedVisibility`.
 */
@ExperimentalHazeApi
public enum class GlassMemberTransition {
  /**
   * A member that attaches within the container's spacing of a compatible member grows out of it,
   * and retracts into it when it detaches. Without such a neighbour, this falls back to
   * [Materialize].
   */
  MatchedGeometry,

  /**
   * The glass fades in or out where it is. A fading member draws on its own and joins a nearby
   * member's merged shape once it is fully opaque.
   */
  Materialize,

  /** The glass appears and disappears immediately. */
  None,
}
