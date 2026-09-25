// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.math.sqrt

internal const val GLASS_FULL_RESOLUTION_SCALE = 1f

private const val MIN_FIXED_PIXEL_FRACTION = 0.25f
private const val MAX_FIXED_PIXEL_FRACTION = 1f

/** Resolves Glass's input scale from a normalized quality fraction. */
@Suppress("DEPRECATION")
internal fun HazePerformanceMode.resolveGlassInputScale(): Float = when (this) {
  HazePerformanceMode.Adaptive -> HazePerformanceMode.Default.resolveGlassInputScale()
  is HazePerformanceMode.Fixed ->
    sqrt(MIN_FIXED_PIXEL_FRACTION + (MAX_FIXED_PIXEL_FRACTION - MIN_FIXED_PIXEL_FRACTION) * qualityFraction)
}
