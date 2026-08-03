// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Tracks short bursts of distinct effect-input updates for adaptive sampling policies. */
@InternalHazeApi
public class HazeInputUpdateCadence(
  private val timeSource: TimeSource = TimeSource.Monotonic,
) {
  private var previousUpdateKey: Any? = UnsetUpdateKey
  private var lastUpdateMark: TimeMark? = null

  public var multiplier: Int = 1
    private set

  /** Returns whether [multiplier] changed. Repeated observations of the same input do not count. */
  public fun observeUpdate(updateKey: Any?): Boolean {
    if (previousUpdateKey == updateKey) {
      val burstExpired = lastUpdateMark?.elapsedNow()?.let { it > UPDATE_BURST_WINDOW } == true
      if (burstExpired && multiplier != 1) {
        multiplier = 1
        return true
      }
      return false
    }

    val rapidUpdate = lastUpdateMark?.elapsedNow()?.let { it <= UPDATE_BURST_WINDOW } == true
    val previousMultiplier = multiplier
    multiplier = if (rapidUpdate) {
      (multiplier + 1).coerceAtMost(MAX_UPDATE_BURST_SIZE)
    } else {
      1
    }
    previousUpdateKey = updateKey
    lastUpdateMark = timeSource.markNow()
    return multiplier != previousMultiplier
  }

  public fun reset() {
    previousUpdateKey = UnsetUpdateKey
    lastUpdateMark = null
    multiplier = 1
  }
}

private object UnsetUpdateKey

private const val MAX_UPDATE_BURST_SIZE: Int = 3
private val UPDATE_BURST_WINDOW = 100.milliseconds
