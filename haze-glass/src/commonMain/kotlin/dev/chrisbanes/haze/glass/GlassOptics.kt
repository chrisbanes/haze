// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeProgressive

/** Selects how Glass optical values are produced. */
@ExperimentalHazeApi
@Immutable
public sealed interface GlassOptics {

  /**
   * The built-in Haze glass material.
   *
   * Its optical response adapts to the material's size, aspect ratio, and roundness.
   * Its adaptive blur scaling is applied after the [Absolute] blur-radius cap, so its effective
   * blur radius can exceed that cap.
   */
  public data object Adaptive : GlassOptics

  /**
   * A complete optical configuration with no geometry-dependent adjustment.
   *
   * Accepted values are resolved without geometry-dependent adjustment. [refractionDisplacement]
   * and [blurRadius] use density-independent [Dp]. [refractionHeightFraction] is a unitless
   * fraction of the material's shortest side. After density conversion, the effective [blurRadius]
   * is capped at 38.5 physical pixels; this renderer quality bound does not limit accepted input
   * values.
   *
   * @param refractionStrength Strength of the refraction response, in the range `0f..1f`.
   * @param refractionDisplacement Maximum distance that refraction displaces content.
   * @param refractionHeightFraction Fraction of the material's shortest side used by the
   * refraction profile, in the range `0f..1f`.
   * @param depth Depth perception factor. Values greater than `0f` require drawing an additional
   * blurred sample for the glass content, which has a rendering cost.
   * @param blurRadius Maximum blur radius before the renderer's adaptive scale is applied.
   * @param progressive Optional progressive intensity applied to the blur.
   */
  public data class Absolute(
    val refractionStrength: Float = 0.7f,
    val refractionHeightFraction: Float = 0.25f,
    val refractionDisplacement: Dp = 15.dp,
    val depth: Float = 1f,
    val blurRadius: Dp = 14.dp,
    val progressive: HazeProgressive? = null,
  ) : GlassOptics {
    init {
      require(refractionStrength.isFinite() && refractionStrength in 0f..1f) {
        "refractionStrength must be finite and in 0f..1f"
      }
      require(
        refractionHeightFraction.isFinite() && refractionHeightFraction in 0f..1f,
      ) {
        "refractionHeightFraction must be finite and in 0f..1f"
      }
      require(
        refractionDisplacement.isSpecified &&
          refractionDisplacement.value.isFinite() &&
          refractionDisplacement >= 0.dp,
      ) {
        "refractionDisplacement must be specified, finite, and non-negative"
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
