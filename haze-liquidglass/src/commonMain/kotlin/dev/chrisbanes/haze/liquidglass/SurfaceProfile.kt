// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

/**
 * Describes the cross-sectional surface shape of the liquid glass bezel.
 *
 * The profile controls how light bends at the transition from the edge to the flat interior,
 * producing different refraction characteristics.
 */
public enum class SurfaceProfile {
  /**
   * Spherical dome profile. Light rays bend sharply near the edge, creating a pronounced
   * refraction rim that softens toward the flat interior.
   */
  Circle,

  /**
   * Squircle profile with a gentler flat-to-curve transition. Produces smoother refraction
   * gradients even when the shape is stretched into non-square rectangles.
   */
  Squircle,

  /**
   * Inverted dome that causes light rays to diverge outward, displacing them beyond the
   * glass boundaries for a concave lens look.
   */
  Concave,

  /**
   * Blends a raised convex rim with a shallow concave center dip. Useful for creating
   * compound lens effects such as magnifying or recessed glass.
   */
  Lip,
}
