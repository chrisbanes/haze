// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.test.Test

class GlassAdaptiveTierControllerTest {
  @Test
  fun sustainedMisses_downgradeOnlyAfterWarmUpAndSustainedWindow() {
    val trace = Trace()

    repeat(25) {
      trace.frame(missed = true)
      assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.BALANCED)
    }
    repeat(70) { trace.frame(missed = true) }

    assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.AGGRESSIVE)
  }

  @Test
  fun healthyFrames_probeBackUpOneTierAfterLongStableWindow() {
    val trace = Trace()
    trace.downgradeToAggressive()

    repeat(180) {
      trace.frame(missed = false)
      assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.AGGRESSIVE)
    }
    repeat(40) { trace.frame(missed = false) }

    assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.BALANCED)
  }

  @Test
  fun failedProbes_rollBackAndIncreaseRetryBackoff() {
    val trace = Trace()
    trace.downgradeToAggressive()
    trace.feedHealthyFrames(220)
    assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.BALANCED)
    trace.feedMissedFrames(70)
    assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.AGGRESSIVE)

    trace.feedHealthyFrames(220)
    assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.BALANCED)
    trace.feedMissedFrames(70)
    assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.AGGRESSIVE)

    trace.feedHealthyFrames(180)
    assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.AGGRESSIVE)
    trace.feedHealthyFrames(100)
    assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.BALANCED)
  }

  @Test
  fun staleAndLostSamples_clearEvidenceWithoutChangingTier() {
    val trace = Trace()
    trace.feedMissedFrames(80)
    val lostSequence = trace.nextSequence + 1
    trace.frame(missed = true, sequence = lostSequence)
    trace.feedMissedFrames(70)

    assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.BALANCED)

    trace.frame(
      missed = true,
      timestampNanos = trace.timestampNanos + FRAME_INTERVAL_NANOS,
      nowNanos = trace.timestampNanos + FRAME_INTERVAL_NANOS + 300_000_000L,
    )
    trace.feedMissedFrames(70)

    assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.BALANCED)
    trace.feedMissedFrames(30)
    assertThat(trace.controller.tier).isEqualTo(GlassAdaptiveTier.AGGRESSIVE)
  }

  @Test
  fun fixedModes_keepTheirExactLegacyScaleMapping() {
    val policy = GlassInputScalePolicy()

    assertThat(policy.resolve(HazePerformanceMode.Fixed(0f))).isEqualTo(0.5f)
    assertThat(policy.resolve(HazePerformanceMode.Fixed(0.25f))).isEqualTo(0.6614378f)
    assertThat(policy.resolve(HazePerformanceMode.Fixed(0.5f))).isEqualTo(0.7905694f)
    assertThat(policy.resolve(HazePerformanceMode.Fixed(0.75f))).isEqualTo(0.9013878f)
    assertThat(policy.resolve(HazePerformanceMode.Fixed(1f))).isEqualTo(1f)
  }

  private class Trace {
    val controller = GlassAdaptiveTierController()
    var nextSequence = 0L
      private set
    var timestampNanos = 0L
      private set

    fun frame(
      missed: Boolean,
      sequence: Long = nextSequence,
      timestampNanos: Long = this.timestampNanos + FRAME_INTERVAL_NANOS,
      nowNanos: Long = timestampNanos,
    ) {
      this.nextSequence = sequence + 1
      this.timestampNanos = timestampNanos
      controller.observe(
        GlassFrameHealthSample(
          sequence = sequence,
          timestampNanos = timestampNanos,
          durationNanos = if (missed) 20_000_000L else 12_000_000L,
          budgetNanos = 16_000_000L,
        ),
        nowNanos = nowNanos,
      )
    }

    fun feedMissedFrames(count: Int) = repeat(count) { frame(missed = true) }
    fun feedHealthyFrames(count: Int) = repeat(count) { frame(missed = false) }

    fun downgradeToAggressive() {
      feedMissedFrames(95)
      assertThat(controller.tier).isEqualTo(GlassAdaptiveTier.AGGRESSIVE)
    }
  }

  private companion object {
    const val FRAME_INTERVAL_NANOS = 16_666_667L
  }
}
