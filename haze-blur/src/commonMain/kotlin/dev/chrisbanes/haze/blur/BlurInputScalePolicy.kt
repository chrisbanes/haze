// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Size
import dev.chrisbanes.haze.HazeInputUpdateCadence
import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.time.TimeSource

/**
 * Resolves automatic blur input scaling while retaining the previous automatic tier for
 * hysteresis. Thresholds are expressed in physical pixels so the policy is density-independent.
 */
internal class BlurInputScalePolicy(
  timeSource: TimeSource = TimeSource.Monotonic,
) {
  private var previousAutomaticScale: Float = NONE_SCALE
  private var previousProgressive: Boolean? = null
  private val inputUpdateCadence = HazeInputUpdateCadence(timeSource)

  fun observeUpdate(updateKey: Any?): Boolean = inputUpdateCadence.observeUpdate(updateKey)

  fun resolve(
    performanceMode: HazePerformanceMode,
    blurRadiusPx: Float,
    layerSize: Size,
    progressive: Boolean = false,
  ): Float {
    return when (performanceMode) {
      HazePerformanceMode.Adaptive -> {
        if (previousProgressive != null && previousProgressive != progressive) {
          previousAutomaticScale = NONE_SCALE
          inputUpdateCadence.reset()
        }
        resolveFixedProfile(
          resolveAutomaticQualityFraction(
            blurRadiusPx = blurRadiusPx,
            areaPx = layerSize.width * layerSize.height * inputUpdateCadence.multiplier,
            progressive = progressive,
          ),
        ).also {
          previousAutomaticScale = it
          previousProgressive = progressive
        }
      }

      is HazePerformanceMode.Fixed -> {
        reset()
        resolveFixedProfile(performanceMode.qualityFraction)
      }
    }
  }

  fun reset() {
    previousAutomaticScale = NONE_SCALE
    previousProgressive = null
    inputUpdateCadence.reset()
  }

  private fun resolveAutomaticQualityFraction(
    blurRadiusPx: Float,
    areaPx: Float,
    progressive: Boolean,
  ): Float {
    if (!blurRadiusPx.isFinite() || !areaPx.isFinite() || blurRadiusPx <= 0f || areaPx <= 0f) {
      return NONE_SCALE
    }

    if (progressive) {
      return when {
        previousAutomaticScale <= BALANCED_SCALE &&
          blurRadiusPx >= BALANCED_RADIUS_EXIT_PX &&
          areaPx >= BALANCED_AREA_EXIT_PX -> BALANCED_FRACTION

        blurRadiusPx >= BALANCED_RADIUS_PX && areaPx >= BALANCED_AREA_PX -> BALANCED_FRACTION
        else -> QUALITY_FRACTION
      }
    }

    return when (previousAutomaticScale) {
      AGGRESSIVE_SCALE -> when {
        blurRadiusPx < AGGRESSIVE_RADIUS_EXIT_PX || areaPx < AGGRESSIVE_AREA_EXIT_PX ->
          resolveAdaptiveQualityFractionWithoutAggressiveTier(blurRadiusPx, areaPx)

        else -> PERFORMANCE_FRACTION
      }

      BALANCED_SCALE -> when {
        blurRadiusPx >= AGGRESSIVE_RADIUS_PX && areaPx >= AGGRESSIVE_AREA_PX -> PERFORMANCE_FRACTION
        blurRadiusPx < BALANCED_RADIUS_EXIT_PX || areaPx < BALANCED_AREA_EXIT_PX -> QUALITY_FRACTION
        else -> BALANCED_FRACTION
      }

      else -> when {
        blurRadiusPx >= AGGRESSIVE_RADIUS_PX && areaPx >= AGGRESSIVE_AREA_PX -> PERFORMANCE_FRACTION
        blurRadiusPx >= BALANCED_RADIUS_PX && areaPx >= BALANCED_AREA_PX -> BALANCED_FRACTION
        else -> QUALITY_FRACTION
      }
    }
  }

  private fun resolveAdaptiveQualityFractionWithoutAggressiveTier(
    blurRadiusPx: Float,
    areaPx: Float,
  ): Float {
    return when {
      blurRadiusPx >= BALANCED_RADIUS_EXIT_PX && areaPx >= BALANCED_AREA_EXIT_PX -> BALANCED_FRACTION
      else -> QUALITY_FRACTION
    }
  }

  private fun resolveFixedProfile(qualityFraction: Float): Float = when {
    qualityFraction >= QUALITY_THRESHOLD -> NONE_SCALE
    qualityFraction >= BALANCED_THRESHOLD -> BALANCED_SCALE
    else -> AGGRESSIVE_SCALE
  }

  internal companion object {
    const val BALANCED_SCALE = 0.8f
    const val AGGRESSIVE_SCALE = 0.5f
    const val NONE_SCALE = 1f

    const val QUALITY_FRACTION = 1f
    const val BALANCED_FRACTION = 0.5f
    const val PERFORMANCE_FRACTION = 0f

    const val QUALITY_THRESHOLD = 0.75f
    const val BALANCED_THRESHOLD = 0.25f

    // At these boundaries, the downsampled blur kernel remains at least 25.6px / 30px.
    const val BALANCED_RADIUS_PX = 32f
    const val AGGRESSIVE_RADIUS_PX = 60f

    // These boundaries avoid reallocating for savings below roughly 0.1M / 0.375M pixels.
    const val BALANCED_AREA_PX = 300_000f
    const val AGGRESSIVE_AREA_PX = 500_000f

    // A 12.5% exit margin absorbs common one-frame layout and radius animation jitter.
    const val BALANCED_RADIUS_EXIT_PX = 28f
    const val AGGRESSIVE_RADIUS_EXIT_PX = 52.5f
    const val BALANCED_AREA_EXIT_PX = 262_500f
    const val AGGRESSIVE_AREA_EXIT_PX = 437_500f
  }
}
