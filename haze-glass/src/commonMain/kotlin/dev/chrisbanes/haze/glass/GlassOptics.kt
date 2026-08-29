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
 * [depth] and [blurRadius] resolve independently. [SizeValue.Fixed] applies one value at every
 * material size. [SizeValue.Responsive] clamps to its first or last point outside the authored
 * range and smoothly interpolates between adjacent points using the material's shortest dimension.
 *
 * Invalid numeric values throw [IllegalArgumentException] during construction.
 */
@ExperimentalHazeApi
@Immutable
public data class GlassOptics(
  /** Refraction strength in the inclusive range `0f..1f`. */
  val refractionStrength: Float = 0.7f,
  /** Fraction of the material's shortest side used by the refraction profile. */
  val refractionHeightFraction: Float = 0.25f,
  /** Maximum distance that refraction displaces content. */
  val refractionDisplacement: Dp = 15.dp,
  /** Depth perception factor in the inclusive range `0f..1f`. */
  val depth: SizeValue<Float> = SizeValue.Fixed(1f),
  /** Maximum blur radius before the renderer quality cap is applied. */
  val blurRadius: SizeValue<Dp> = SizeValue.Fixed(14.dp),
  /** Optional progressive intensity applied to the blur. */
  val progressive: HazeProgressive? = null,
  /** Strength of the inverted edge-refraction fold, in the inclusive range `0f..1f`. */
  val refractionFoldStrength: Float = 0f,
  /** Intensity of refraction detail, in the inclusive range `0f..1f`. */
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
  value: SizeValue<T>,
  validate: (T) -> Unit,
) {
  when (value) {
    is SizeValue.Fixed -> validate(value.value)
    is SizeValue.Responsive -> value.points.forEach { validate(it.value) }
  }
}
