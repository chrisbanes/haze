// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.math.sqrt

/** Resolves Glass's input scale from a normalized quality fraction. */
internal class GlassInputScalePolicy {
  @Suppress("DEPRECATION")
  fun resolve(performanceMode: HazePerformanceMode): Float = when (performanceMode) {
    HazePerformanceMode.Adaptive -> resolveFixedScale(BALANCED_QUALITY_FRACTION)
    is HazePerformanceMode.Fixed -> resolveFixedScale(performanceMode.qualityFraction)
  }

  internal companion object {
    private const val MIN_FIXED_PIXEL_FRACTION = 0.25f
    private const val MAX_FIXED_PIXEL_FRACTION = 1f
    private const val BALANCED_QUALITY_FRACTION = 0.5f

    const val FULL_RESOLUTION_SCALE: Float = 1f
  }

  private fun resolveFixedScale(qualityFraction: Float): Float =
    sqrt(MIN_FIXED_PIXEL_FRACTION + (MAX_FIXED_PIXEL_FRACTION - MIN_FIXED_PIXEL_FRACTION) * qualityFraction)
}
