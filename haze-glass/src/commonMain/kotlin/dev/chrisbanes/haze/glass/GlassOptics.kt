// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeProgressive

/**
 * The optical response used to render a Glass material.
 *
 * [depth] and [blurRadius] resolve independently. [OpticalSizeValue.Fixed] applies one value at every
 * material size. [OpticalSizeValue.Responsive] clamps to its first or last point outside the authored
 * range and smoothly interpolates between adjacent points using the material's shortest dimension.
 *
 * Responsive values require at least two points with positive, finite, strictly increasing
 * dimensions. Invalid numeric values throw [IllegalArgumentException] during construction.
 */
@ExperimentalHazeApi
@Immutable
public data class GlassOptics(
  /** Finite refraction strength in the inclusive range `0f..1f`. */
  val refractionStrength: Float = 0.7f,
  /** Finite fraction of the material's shortest side used by the refraction profile, in `0f..1f`. */
  val refractionHeightFraction: Float = 0.25f,
  /** Specified, finite, non-negative maximum distance that refraction displaces content. */
  val refractionDisplacement: Dp = 15.dp,
  /**
   * Depth perception factor whose values must be finite and in `0f..1f`.
   *
   * Values greater than `0f` require an additional blurred content sample on full renderers.
   */
  val depth: OpticalSizeValue<Float> = OpticalSizeValue.Fixed(1f),
  /**
   * Maximum blur radius whose values must be specified, finite, and non-negative.
   *
   * There is no authored upper limit. The renderer quality cap is applied after resolution.
   */
  val blurRadius: OpticalSizeValue<Dp> = OpticalSizeValue.Fixed(14.dp),
  /** Optional progressive intensity, retaining the contract of [HazeProgressive]. */
  val progressive: HazeProgressive? = null,
  /** Finite strength of the inverted edge-refraction fold, in the inclusive range `0f..1f`. */
  val refractionFoldStrength: Float = 0f,
  /**
   * Finite intensity of secondary edge-refraction detail, in the inclusive range `0f..1f`.
   *
   * `0f` disables the detail pass. Non-zero values may retain additional rendering layers on full
   * renderers.
   */
  val refractionDetailIntensity: Float = 0.76f,
) {
  init {
    requireFiniteInRange(
      "refractionStrength",
      refractionStrength,
      0f..1f,
      UNIT_INTERVAL_DOMAIN,
    )
    requireFiniteInRange(
      "refractionFoldStrength",
      refractionFoldStrength,
      0f..1f,
      UNIT_INTERVAL_DOMAIN,
    )
    requireFiniteInRange(
      "refractionHeightFraction",
      refractionHeightFraction,
      0f..1f,
      UNIT_INTERVAL_DOMAIN,
    )
    requireSpecifiedFiniteNonNegative("refractionDisplacement", refractionDisplacement)
    requireFiniteInRange(
      "refractionDetailIntensity",
      refractionDetailIntensity,
      0f..1f,
      UNIT_INTERVAL_DOMAIN,
    )
    requireSizeValue(depth) { value ->
      requireFiniteInRange("depth", value, 0f..1f, UNIT_INTERVAL_DOMAIN)
    }
    requireSizeValue(blurRadius) { value ->
      requireSpecifiedFiniteNonNegative("blurRadius", value)
    }
  }
}

private inline fun <T> requireSizeValue(
  value: OpticalSizeValue<T>,
  validate: (T) -> Unit,
) {
  when (value) {
    is OpticalSizeValue.Fixed -> validate(value.value)
    is OpticalSizeValue.Responsive -> value.points.forEach { validate(it.value) }
  }
}
