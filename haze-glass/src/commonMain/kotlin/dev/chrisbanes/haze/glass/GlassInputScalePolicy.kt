// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.HazeInputUpdateCadence
import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.math.sqrt
import kotlin.time.TimeSource

/** Resolves Glass's validated input scale from a normalized quality fraction. */
internal class GlassInputScalePolicy(
  timeSource: TimeSource = TimeSource.Monotonic,
) {
  private var previousAdaptiveQualityFraction: Float = BALANCED_FRACTION
  private val inputUpdateCadence = HazeInputUpdateCadence(timeSource)

  /** Returns whether the weighted workload changed enough to require scale reevaluation. */
  fun observeUpdate(updateKey: Any?): Boolean = inputUpdateCadence.observeUpdate(updateKey)

  fun resolve(
    performanceMode: HazePerformanceMode,
    balancedPlan: GlassRetainedLayerPlan? = null,
  ): Float = when (performanceMode) {
    HazePerformanceMode.Adaptive -> {
      val retainedPixels = balancedPlan?.retainedPixelCountOrNull()
      val retainedPixelUpdates = retainedPixels?.saturatedTimes(inputUpdateCadence.multiplier)
      val qualityFraction = if (
        retainedPixelUpdates != null &&
        (
          retainedPixelUpdates >= AGGRESSIVE_RETAINED_PIXEL_UPDATES ||
            previousAdaptiveQualityFraction == PERFORMANCE_FRACTION &&
            retainedPixelUpdates >= AGGRESSIVE_RETAINED_PIXEL_UPDATES_EXIT
          )
      ) {
        PERFORMANCE_FRACTION
      } else {
        BALANCED_FRACTION
      }
      previousAdaptiveQualityFraction = qualityFraction
      resolveFixedScale(qualityFraction)
    }
    is HazePerformanceMode.Fixed -> resolveFixedScale(performanceMode.qualityFraction).also { reset() }
  }

  fun reset() {
    previousAdaptiveQualityFraction = BALANCED_FRACTION
    inputUpdateCadence.reset()
  }

  internal companion object {
    val BALANCED_SCALE: Float = sqrt(0.5f)
    const val AGGRESSIVE_SCALE: Float = 0.5f
    const val FULL_RESOLUTION_SCALE: Float = 1f

    const val BALANCED_FRACTION: Float = 0.5f
    const val PERFORMANCE_FRACTION: Float = 0f

    const val QUALITY_THRESHOLD: Float = 0.75f
    const val BALANCED_THRESHOLD: Float = 0.25f

    // This is a cost-rate proxy: retained pixels multiplied by up to three rapid updates.
    const val AGGRESSIVE_RETAINED_PIXEL_UPDATES: Long = 1_500_000L

    // A 12.5% exit margin absorbs geometry and cadence noise without reallocating layers.
    const val AGGRESSIVE_RETAINED_PIXEL_UPDATES_EXIT: Long = 1_312_500L
  }

  private fun resolveFixedScale(qualityFraction: Float): Float = when {
    qualityFraction >= QUALITY_THRESHOLD -> FULL_RESOLUTION_SCALE
    qualityFraction >= BALANCED_THRESHOLD -> BALANCED_SCALE
    else -> AGGRESSIVE_SCALE
  }
}

private fun Long.saturatedTimes(value: Int): Long =
  if (this > Long.MAX_VALUE / value) Long.MAX_VALUE else this * value
