// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.HazeInputUpdateCadence
import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.math.sqrt
import kotlin.time.TimeSource

/** Resolves Glass's input scale from a normalized quality fraction. */
internal class GlassInputScalePolicy(
  timeSource: TimeSource = TimeSource.Monotonic,
) {
  private var previousAdaptiveScale: Float = BALANCED_SCALE
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
      val scale = if (
        retainedPixelUpdates != null &&
        (
          retainedPixelUpdates >= AGGRESSIVE_RETAINED_PIXEL_UPDATES ||
            previousAdaptiveScale == AGGRESSIVE_SCALE &&
            retainedPixelUpdates >= AGGRESSIVE_RETAINED_PIXEL_UPDATES_EXIT
          )
      ) {
        AGGRESSIVE_SCALE
      } else {
        BALANCED_SCALE
      }
      previousAdaptiveScale = scale
      scale
    }
    is HazePerformanceMode.Fixed -> resolveFixedScale(performanceMode.qualityFraction).also { reset() }
  }

  fun reset() {
    previousAdaptiveScale = BALANCED_SCALE
    inputUpdateCadence.reset()
  }

  internal companion object {
    private const val MIN_FIXED_PIXEL_FRACTION = 0.25f
    private const val MAX_FIXED_PIXEL_FRACTION = 1f

    val BALANCED_SCALE: Float = sqrt(0.5f)
    const val AGGRESSIVE_SCALE: Float = 0.5f
    const val FULL_RESOLUTION_SCALE: Float = 1f

    // This is a cost-rate proxy: retained pixels multiplied by up to three rapid updates.
    const val AGGRESSIVE_RETAINED_PIXEL_UPDATES: Long = 1_500_000L

    // A 12.5% exit margin absorbs geometry and cadence noise without reallocating layers.
    const val AGGRESSIVE_RETAINED_PIXEL_UPDATES_EXIT: Long = 1_312_500L
  }

  private fun resolveFixedScale(qualityFraction: Float): Float =
    sqrt(MIN_FIXED_PIXEL_FRACTION + (MAX_FIXED_PIXEL_FRACTION - MIN_FIXED_PIXEL_FRACTION) * qualityFraction)
}

private fun Long.saturatedTimes(value: Int): Long =
  if (this > Long.MAX_VALUE / value) Long.MAX_VALUE else this * value
