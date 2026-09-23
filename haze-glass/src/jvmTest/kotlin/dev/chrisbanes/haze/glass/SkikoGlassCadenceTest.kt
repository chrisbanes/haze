// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.MonotonicFrameClock
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine

class SkikoGlassCadenceTest {

  @Test
  fun jitteredSixtyHertzCadence_neverDowngradesOrProbesWithoutKnownRefreshTarget() {
    val trace = AdaptiveCadenceTrace()
    val intervals = longArrayOf(
      16_000_000L,
      16_800_000L,
      16_700_000L,
      17_000_000L,
      16_700_000L,
      16_900_000L,
      16_600_000L,
      18_100_000L,
    )

    repeat(450) { index -> trace.frame(intervals[index % intervals.size]) }

    assertThat(trace.lowTierSeen).isFalse()
    assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.BALANCED)
  }

  @Test
  fun steadyHalfRateCadence_neverProbesAndThirtyFpsDowngrades() {
    val sixtyHertzAtHalfRate = AdaptiveCadenceTrace()
    repeat(300) { sixtyHertzAtHalfRate.frame(33_333_334L) }
    assertThat(sixtyHertzAtHalfRate.controller.tier).isEqualTo(GlassAdaptiveTier.AGGRESSIVE)

    val unknownOneTwentyHertzAtHalfRate = AdaptiveCadenceTrace()
    repeat(500) { unknownOneTwentyHertzAtHalfRate.frame(16_666_667L) }
    assertThat(unknownOneTwentyHertzAtHalfRate.controller.tier)
      .isEqualTo(GlassAdaptiveTier.BALANCED)
  }

  @Test
  fun observedHighRefreshThenSteadyHalfRate_downgrades() {
    for (intervalNanos in longArrayOf(8_333_333L, 6_944_444L)) {
      val trace = AdaptiveCadenceTrace()
      repeat(40) { trace.frame(intervalNanos) }
      repeat(180) { trace.frame(intervalNanos * 2) }

      assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.AGGRESSIVE)
    }
  }

  @Test
  fun repeatedSkippedCallbacks_downgradeThroughTheHostController() {
    val trace = AdaptiveCadenceTrace()
    repeat(40) { trace.frame(16_666_667L) }
    repeat(90) { trace.frame(33_333_334L) }

    assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.AGGRESSIVE)
  }

  @Test
  fun staleCadenceGap_usesFallbackUntilFreshSamplesArrive() {
    val trace = AdaptiveCadenceTrace()
    repeat(40) { trace.frame(16_666_667L) }
    assertThat(trace.host.decision.timingTier).isEqualTo(GlassAdaptiveTier.BALANCED)

    Thread.sleep(300)
    assertThat(trace.host.decision.timingTier).isNull()
    trace.frame(2_000_000_000L)
    repeat(8) { trace.frame(16_666_667L) }
    assertThat(trace.host.decision.timingTier).isNull()

    trace.frame(16_666_667L)
    assertThat(trace.host.decision.timingTier).isEqualTo(GlassAdaptiveTier.BALANCED)
  }

  @Test
  fun stableActiveCadence_emitsAfterBudgetWarmup() {
    val cadence = SkikoGlassCadence()
    var now = 0L
    repeat(9) {
      assertThat(cadence.record(now)).isNull()
      now += 16_666_667L
    }

    val sample = cadence.record(now)
    assertThat(sample).isNotNull()
    assertThat(sample?.durationNanos).isEqualTo(16_666_667L)
    assertThat(sample?.budgetNanos).isEqualTo(25_000_000L)
  }

  @Test
  fun backgroundGap_discardsOldBudgetAndWaitsForFreshCadence() {
    val cadence = SkikoGlassCadence()
    var now = 0L
    repeat(10) { cadence.record(now).also { now += 16_666_667L } }
    assertThat(cadence.record(now)).isNotNull()

    now += 2_000_000_000L
    assertThat(cadence.record(now)).isNull()
    now += 16_666_667L
    assertThat(cadence.record(now)).isNull()
  }

  @Test
  fun unstableWarmup_withholdsSamplesWhenTargetCadenceIsUnknown() {
    val cadence = SkikoGlassCadence()
    var now = 0L
    assertThat(cadence.record(now)).isNull()
    repeat(16) { index ->
      now += if (index % 2 == 0) 16_666_667L else 33_333_334L
      assertThat(cadence.record(now)).isNull()
    }
  }

  @Test
  fun fasterRefresh_discardsOldBudgetBeforeProducingSamples() {
    val cadence = SkikoGlassCadence()
    var now = 0L
    repeat(10) { cadence.record(now).also { now += 16_666_667L } }
    assertThat(cadence.record(now)).isNotNull()

    now += 8_333_333L
    assertThat(cadence.record(now)).isNull()
    repeat(8) {
      now += 8_333_333L
      assertThat(cadence.record(now)).isNull()
    }
    now += 8_333_333L
    assertThat(cadence.record(now)?.budgetNanos).isEqualTo(12_499_999L)
  }

  @Test
  fun observerStopsRequestingFramesWhenDemandEnds() {
    val clock = SuspendedFrameClock()
    val source = SkikoGlassTimingSource(clock, Dispatchers.Unconfined)

    assertThat(source.start { _, _ -> }).isEqualTo(true)
    assertThat(clock.requests).isEqualTo(1)
    source.stop()
    assertThat(clock.cancellations).isEqualTo(1)
    assertThat(clock.requests).isEqualTo(1)
  }
}

private class AdaptiveCadenceTrace {
  val host = GlassAdaptiveHost(Any())
  val controller: GlassAdaptiveTierController get() = host.controller
  private val cadence = SkikoGlassCadence()
  private var nowNanos = 0L
  var lowTierSeen = false
    private set

  init {
    cadence.record(nowNanos)
  }

  fun frame(intervalNanos: Long) {
    nowNanos += intervalNanos
    cadence.record(nowNanos)?.let { host.recordSample(it, nowNanos) }
    if (controller.tier == GlassAdaptiveTier.AGGRESSIVE) lowTierSeen = true
  }
}

private class SuspendedFrameClock : MonotonicFrameClock {
  var requests = 0
  var cancellations = 0

  override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
    requests++
    return suspendCancellableCoroutine { continuation ->
      continuation.invokeOnCancellation { cancellations++ }
    }
  }
}
