// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.withFrameNanos
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal actual fun platformGlassAdaptiveTimingSource(
  scope: HazeEffectLifecycleScope,
  hostKey: Any,
): GlassAdaptiveTimingSource? {
  val context = scope.coroutineScope.coroutineContext
  val clock = context[MonotonicFrameClock] ?: return null
  val dispatcher = context[ContinuationInterceptor] ?: return null
  return SkikoGlassTimingSource(clock, dispatcher)
}

internal class SkikoGlassTimingSource(
  private val frameClock: MonotonicFrameClock,
  private val dispatcher: ContinuationInterceptor,
) : GlassAdaptiveTimingSource {
  override val identity: Any get() = frameClock
  private var scopeJob: Job? = null

  override fun start(onSample: (GlassFrameHealthSample, Long) -> Unit): Boolean {
    if (scopeJob != null) return false
    val cadence = SkikoGlassCadence()
    val origin = TimeSource.Monotonic.markNow()
    val owner = SupervisorJob()
    val scope = CoroutineScope(owner + dispatcher + frameClock)
    scopeJob = owner
    scope.launch {
      while (isActive) {
        withFrameNanos { }
        val nowNanos = origin.elapsedNow().inWholeNanoseconds
        cadence.record(nowNanos)?.let { onSample(it, nowNanos) }
      }
    }
    return true
  }

  override fun stop() {
    scopeJob?.cancel()
    scopeJob = null
  }
}

/** Callback-delivery cadence is lower-confidence evidence than rendered-frame completion. */
internal class SkikoGlassCadence {
  private var previousNanos: Long? = null
  private var shortestNanos = Long.MAX_VALUE
  private var longestNanos = 0L
  private var warmupIntervals = 0
  private var budgetNanos: Long? = null
  private var sequence = 0L
  private var shortIntervalNanos = 0L
  private var consecutiveShortIntervals = 0

  fun record(nowNanos: Long): GlassFrameHealthSample? {
    val previous = previousNanos
    previousNanos = nowNanos
    if (previous == null) return null
    val interval = nowNanos - previous
    if (interval !in 5_000_000L..250_000_000L) {
      resetEvidence()
      return null
    }
    val budget = budgetNanos
    if (budget == null) {
      shortestNanos = minOf(shortestNanos, interval)
      longestNanos = maxOf(longestNanos, interval)
      warmupIntervals++
      if (warmupIntervals == 8) {
        if (longestNanos <= shortestNanos * 5 / 4) {
          budgetNanos = shortestNanos
        } else {
          resetEvidence()
        }
      }
      return null
    }
    if (interval < budget * 3 / 4) {
      // One early callback can be scheduler jitter. Require a stable faster cadence before
      // discarding the current target; withheld callbacks must not count as healthy samples.
      if (shortIntervalNanos == 0L ||
        interval > shortIntervalNanos * 5 / 4 ||
        shortIntervalNanos > interval * 5 / 4
      ) {
        shortIntervalNanos = interval
        consecutiveShortIntervals = 1
      } else {
        consecutiveShortIntervals++
        if (consecutiveShortIntervals == 3) resetEvidence()
      }
      return null
    }
    shortIntervalNanos = 0L
    consecutiveShortIntervals = 0
    return GlassFrameHealthSample(
      sequence = ++sequence,
      timestampNanos = nowNanos,
      durationNanos = interval,
      // Allow callback jitter, but never learn a slower-than-60 Hz stream as a healthy target.
      // This remains callback cadence, not a rendered-frame deadline.
      budgetNanos = minOf(budget, 16_666_667L) * 3 / 2,
      // Callback cadence alone cannot distinguish a native 60 Hz target from half-rate 120 Hz.
      allowsUpwardProbe = false,
      allowsRelativeProbe = true,
    )
  }

  fun resetEvidence() {
    shortestNanos = Long.MAX_VALUE
    longestNanos = 0L
    warmupIntervals = 0
    budgetNanos = null
    sequence = 0L
    shortIntervalNanos = 0L
    consecutiveShortIntervals = 0
  }
}
