// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
   * Its adaptive blur scaling is applied after the [Fixed] blur-radius cap, so its effective
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
   * Invalid numeric values throw [IllegalArgumentException] when this value is constructed.
   * [progressive] is optional and retains the contract of its owning [HazeProgressive] type.
   *
   * @param refractionStrength Finite strength of the refraction response, in the inclusive range
   * `0f..1f`.
   * @param refractionDisplacement Specified, finite, non-negative maximum distance that refraction
   * displaces content. There is no authored upper limit.
   * @param refractionHeightFraction Fraction of the material's shortest side used by the
   * refraction profile, as a finite value in the inclusive range `0f..1f`.
   * @param depth Finite depth perception factor in the inclusive range `0f..1f`. Values greater
   * than `0f` require drawing an additional blurred sample for the glass content, which has a
   * rendering cost.
   * @param blurRadius Specified, finite, non-negative maximum blur radius before the renderer's
   * adaptive scale is applied. There is no authored upper limit.
   * @param progressive Optional progressive intensity applied to the blur.
   */
  public data class Fixed(
    val refractionStrength: Float = 0.7f,
    val refractionHeightFraction: Float = 0.25f,
    val refractionDisplacement: Dp = 15.dp,
    val depth: Float = 1f,
    val blurRadius: Dp = 14.dp,
    val progressive: HazeProgressive? = null,
  ) : GlassOptics {
    init {
      requireFiniteInRange("refractionStrength", refractionStrength, 0f..1f, UNIT_INTERVAL_DOMAIN)
      requireFiniteInRange(
        "refractionHeightFraction",
        refractionHeightFraction,
        0f..1f,
        UNIT_INTERVAL_DOMAIN,
      )
      requireSpecifiedFiniteNonNegative("refractionDisplacement", refractionDisplacement)
      requireFiniteInRange("depth", depth, 0f..1f, UNIT_INTERVAL_DOMAIN)
      requireSpecifiedFiniteNonNegative("blurRadius", blurRadius)
    }
  }
}
