// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/**
 * Controls how refraction displaces content independently of the material's lighting.
 *
 * Future minor releases may add subtypes. Keep an `else` branch when you match exhaustively with `when`.
 */
@Immutable
public sealed interface RefractionProfile {
  /** Uses [SurfaceProfile] and [GlassOptics.refractionHeightFraction] for refraction and lighting. */
  public object Surface : RefractionProfile

  /**
   * Refraction is strongest at the boundary and decays to zero over [width].
   *
   * Rounded corners use an optical radius 1.5 times the visible radius, capped at half the material's
   * shortest side. Displacement is also capped at half that side. [SurfaceProfile] and
   * [GlassOptics.refractionHeightFraction] continue to control lighting, independently of refraction.
   *
   * In a merged cluster, a member's standalone weight falls below 1 once a neighbour is inside the
   * container's merge distance. The rest of its response takes the Edge direction from the merged
   * shape at its visible radius, not 1.5 times it, and blends the members' displacement caps.
   *
   * [width] must be specified, finite, and non-negative. A zero width disables refraction, including
   * its secondary detail pass. Simplified renderers may omit refraction entirely.
   *
   * @property width Distance from the boundary over which refraction decays to zero.
   */
  @Immutable
  public data class Edge(val width: Dp) : RefractionProfile {
    init {
      requireSpecifiedFiniteNonNegative("width", width)
    }
  }
}
