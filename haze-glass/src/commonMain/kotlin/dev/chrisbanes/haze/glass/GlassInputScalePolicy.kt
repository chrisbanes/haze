// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.math.sqrt

/** Resolves the fixed Adaptive fallback and Fixed quality scales for Glass. */
internal class GlassInputScalePolicy {
  fun resolve(performanceMode: HazePerformanceMode): Float = when (performanceMode) {
    HazePerformanceMode.Adaptive -> BALANCED_SCALE
    is HazePerformanceMode.Fixed -> resolveFixedScale(performanceMode.qualityFraction)
  }

  internal companion object {
    private const val MIN_FIXED_PIXEL_FRACTION = 0.25f
    private const val MAX_FIXED_PIXEL_FRACTION = 1f

    val BALANCED_SCALE: Float = sqrt(0.5f)
    const val AGGRESSIVE_SCALE: Float = 0.5f
    const val FULL_RESOLUTION_SCALE: Float = 1f
  }

  private fun resolveFixedScale(qualityFraction: Float): Float =
    sqrt(MIN_FIXED_PIXEL_FRACTION + (MAX_FIXED_PIXEL_FRACTION - MIN_FIXED_PIXEL_FRACTION) * qualityFraction)
}
