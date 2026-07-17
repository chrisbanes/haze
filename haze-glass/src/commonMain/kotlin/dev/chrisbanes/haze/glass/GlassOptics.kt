// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeProgressive

private const val MAX_ABSOLUTE_REFRACTION_SCALE_PX: Float = 16_384f

/** Selects how Glass optical values are produced. */
@ExperimentalHazeApi
@Immutable
public sealed interface GlassOptics {

  /**
   * The built-in Haze glass material.
   *
   * Its optical response adapts to the material's size, aspect ratio, and roundness.
   */
  public data object Adaptive : GlassOptics

  /**
   * A complete optical configuration with no geometry-dependent adjustment.
   *
   * Accepted values are resolved without geometry-dependent adjustment. [blurRadius] uses
   * density-independent [Dp]. [refractionHeight] is a fraction of the material's shortest side.
   * [refractionScale] is a raw displacement in full-resolution effect pixels; it is not converted
   * through density and is scaled only when [dev.chrisbanes.haze.HazeInputScale] renders the
   * effect at reduced resolution.
   *
   * @param refractionScale Full-resolution displacement in effect pixels, in the range
   * `0f..16384f`.
   * @param depth Depth perception factor. Values greater than `0f` require drawing an additional
   * blurred sample for the glass content, which has a rendering cost.
   */
  public data class Absolute(
    val refractionStrength: Float = 0.7f,
    val refractionHeight: Float = 0.25f,
    val refractionScale: Float = 15f,
    val depth: Float = 1f,
    val blurRadius: Dp = 14.dp,
    val progressive: HazeProgressive? = null,
  ) : GlassOptics {
    init {
      require(refractionStrength.isFinite() && refractionStrength in 0f..1f) {
        "refractionStrength must be finite and in 0f..1f"
      }
      require(refractionHeight.isFinite() && refractionHeight in 0f..1f) {
        "refractionHeight must be finite and in 0f..1f"
      }
      require(
        refractionScale.isFinite() && refractionScale in 0f..MAX_ABSOLUTE_REFRACTION_SCALE_PX,
      ) {
        "refractionScale must be finite and in 0f..$MAX_ABSOLUTE_REFRACTION_SCALE_PX"
      }
      require(depth.isFinite() && depth in 0f..1f) {
        "depth must be finite and in 0f..1f"
      }
      require(blurRadius.isSpecified && blurRadius.value.isFinite() && blurRadius >= 0.dp) {
        "blurRadius must be specified, finite, and non-negative"
      }
    }
  }
}
