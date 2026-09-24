// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.math.sqrt

/** Resolves the built-in Blur input scale from a normalized quality fraction. */
internal class BlurInputScalePolicy {
  @Suppress("DEPRECATION")
  fun resolve(performanceMode: HazePerformanceMode): Float = when (performanceMode) {
    HazePerformanceMode.Adaptive -> resolveFixedProfile(BALANCED_QUALITY_FRACTION)
    is HazePerformanceMode.Fixed -> resolveFixedProfile(performanceMode.qualityFraction)
  }

  private fun resolveFixedProfile(qualityFraction: Float): Float =
    sqrt(MIN_FIXED_PIXEL_FRACTION + (MAX_FIXED_PIXEL_FRACTION - MIN_FIXED_PIXEL_FRACTION) * qualityFraction)

  internal companion object {
    private const val MIN_FIXED_PIXEL_FRACTION = 0.25f
    private const val MAX_FIXED_PIXEL_FRACTION = 1f
    private const val BALANCED_QUALITY_FRACTION = 0.5f

    const val NONE_SCALE = 1f
  }
}
