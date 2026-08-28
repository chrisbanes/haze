// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeProgressive
import kotlin.jvm.JvmInline

/** The optical response used to render a Glass material. */
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

  /** A value which is either constant or resolved from the material's shortest dimension. */
  @ExperimentalHazeApi
  @Immutable
  public sealed interface SizeValue<T> {
    /** A constant value. */
    @JvmInline
    public value class Fixed<T>(
      /** The constant value. */
      public val value: T,
    ) : SizeValue<T>

    /** A value smoothly interpolated from shortest-dimension points. */
    @JvmInline
    public value class Interpolated<T> private constructor(
      /** The ordered interpolation points. */
      public val points: List<SizePoint<T>>,
    ) : SizeValue<T> {
      /** Factory methods for [Interpolated]. */
      public companion object {
        /** Creates an immutable interpolated value from at least two ordered points. */
        public operator fun <T> invoke(points: List<SizePoint<T>>): Interpolated<T> {
          val snapshot = points.toList()
          require(snapshot.size >= 2) { "points must contain at least two values" }
          snapshot.forEachIndexed { index, point ->
            require(point.shortestDimension.value.isFinite()) {
              "points[$index].shortestDimension must be finite"
            }
            require(point.shortestDimension > 0.dp) {
              "points[$index].shortestDimension must be positive"
            }
            if (index > 0) {
              require(point.shortestDimension > snapshot[index - 1].shortestDimension) {
                "points shortest dimensions must be strictly increasing"
              }
            }
          }
          return Interpolated(snapshot)
        }
      }
    }
  }

  /**
   * Associates a value with a positive shortest dimension.
   *
   * @param shortestDimension The positive shortest dimension at which [value] applies.
   * @param value The value to resolve at [shortestDimension].
   */
  @ExperimentalHazeApi
  @Immutable
  public data class SizePoint<T>(
    val shortestDimension: Dp,
    val value: T,
  ) {
    init {
      require(shortestDimension.value.isFinite()) { "shortestDimension must be finite" }
      require(shortestDimension > 0.dp) { "shortestDimension must be positive" }
    }
  }
}

private inline fun <T> requireSizeValue(
  value: GlassOptics.SizeValue<T>,
  validate: (T) -> Unit,
) {
  when (value) {
    is GlassOptics.SizeValue.Fixed -> validate(value.value)
    is GlassOptics.SizeValue.Interpolated -> value.points.forEach { validate(it.value) }
  }
}
