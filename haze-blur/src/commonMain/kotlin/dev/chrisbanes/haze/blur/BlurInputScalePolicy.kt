// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Size
import dev.chrisbanes.haze.HazeInputScale

/**
 * Resolves automatic blur input scaling while retaining the previous automatic tier for
 * hysteresis. Thresholds are expressed in physical pixels so the policy is density-independent.
 */
internal class BlurInputScalePolicy {
  private var previousAutomaticScale: Float = NONE_SCALE
  private var previousProgressive: Boolean? = null

  fun resolve(
    requestedScale: HazeInputScale,
    blurRadiusPx: Float,
    layerSize: Size,
    progressive: Boolean = false,
  ): Float {
    return when {
      requestedScale === HazeInputScale.Default || requestedScale === HazeInputScale.Auto -> {
        if (previousProgressive != progressive) {
          previousAutomaticScale = NONE_SCALE
        }
        resolveAutomatic(
          blurRadiusPx = blurRadiusPx,
          areaPx = layerSize.width * layerSize.height,
          progressive = progressive,
        ).also {
          previousAutomaticScale = it
          previousProgressive = progressive
        }
      }

      requestedScale is HazeInputScale.Fixed -> {
        reset()
        requestedScale.scale
      }

      else -> {
        reset()
        NONE_SCALE
      }
    }
  }

  fun reset() {
    previousAutomaticScale = NONE_SCALE
    previousProgressive = null
  }

  private fun resolveAutomatic(
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
          areaPx >= BALANCED_AREA_EXIT_PX -> BALANCED_SCALE

        blurRadiusPx >= BALANCED_RADIUS_PX && areaPx >= BALANCED_AREA_PX -> BALANCED_SCALE
        else -> NONE_SCALE
      }
    }

    return when (previousAutomaticScale) {
      AGGRESSIVE_SCALE -> when {
        blurRadiusPx < AGGRESSIVE_RADIUS_EXIT_PX || areaPx < AGGRESSIVE_AREA_EXIT_PX ->
          resolveWithoutAggressiveTier(blurRadiusPx, areaPx)

        else -> AGGRESSIVE_SCALE
      }

      BALANCED_SCALE -> when {
        blurRadiusPx >= AGGRESSIVE_RADIUS_PX && areaPx >= AGGRESSIVE_AREA_PX -> AGGRESSIVE_SCALE
        blurRadiusPx < BALANCED_RADIUS_EXIT_PX || areaPx < BALANCED_AREA_EXIT_PX -> NONE_SCALE
        else -> BALANCED_SCALE
      }

      else -> when {
        blurRadiusPx >= AGGRESSIVE_RADIUS_PX && areaPx >= AGGRESSIVE_AREA_PX -> AGGRESSIVE_SCALE
        blurRadiusPx >= BALANCED_RADIUS_PX && areaPx >= BALANCED_AREA_PX -> BALANCED_SCALE
        else -> NONE_SCALE
      }
    }
  }

  private fun resolveWithoutAggressiveTier(blurRadiusPx: Float, areaPx: Float): Float {
    return when {
      blurRadiusPx >= BALANCED_RADIUS_EXIT_PX && areaPx >= BALANCED_AREA_EXIT_PX -> BALANCED_SCALE
      else -> NONE_SCALE
    }
  }

  internal companion object {
    const val BALANCED_SCALE = 0.8f
    const val AGGRESSIVE_SCALE = 0.5f
    const val NONE_SCALE = 1f

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
