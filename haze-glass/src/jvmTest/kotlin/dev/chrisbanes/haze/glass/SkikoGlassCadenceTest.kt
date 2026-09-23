// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.MonotonicFrameClock
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine

class SkikoGlassCadenceTest {

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
    assertThat(sample?.budgetNanos).isEqualTo(16_666_667L)
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
    assertThat(cadence.record(now)?.budgetNanos).isEqualTo(8_333_333L)
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
