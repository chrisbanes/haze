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

private const val MIN_LAYER_BLUR_SIGMA = 3f
private const val MAX_LAYER_BLUR_SIGMA = 4f

/**
 * The blur sigma, in its own pixels, of the reduced copy a wide Glass blur reads below
 * [HazePerformanceMode.Quality], or 0 to keep the platform's full-resolution blur chain. A lower
 * quality fraction blurs a coarser copy; 4 matches the grid the platform blur itself works at.
 */
@Suppress("DEPRECATION")
internal fun HazePerformanceMode.resolveGlassLayerBlurSigma(): Float = when (this) {
  HazePerformanceMode.Adaptive -> HazePerformanceMode.Default.resolveGlassLayerBlurSigma()
  is HazePerformanceMode.Fixed -> if (qualityFraction >= 1f) {
    0f
  } else {
    MIN_LAYER_BLUR_SIGMA + (MAX_LAYER_BLUR_SIGMA - MIN_LAYER_BLUR_SIGMA) * qualityFraction
  }
}

/**
 * Whether continuous corners keep their exact silhouette but refract and light through circular
 * corners (#1766). Only modes below [HazePerformanceMode.Balanced] trade that fidelity, and only the
 * fused Android program has the variant.
 */
@Suppress("DEPRECATION")
internal fun HazePerformanceMode.usesCircularCornerOptics(): Boolean = when (this) {
  HazePerformanceMode.Adaptive -> HazePerformanceMode.Default.usesCircularCornerOptics()
  is HazePerformanceMode.Fixed -> qualityFraction < (HazePerformanceMode.Balanced as HazePerformanceMode.Fixed).qualityFraction
}
