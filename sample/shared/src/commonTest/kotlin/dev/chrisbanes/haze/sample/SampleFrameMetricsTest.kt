// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.nanoseconds

class SampleFrameMetricsTest : ContextTest() {
  @Test
  fun summary_withoutSamples_reportsNoDurations() {
    val metrics = SampleFrameMetrics(SampleFrameMetricsSource.FrameCadence)

    val summary = metrics.summary(nowNanos = 0)

    assertThat(summary.sampleCount).isEqualTo(0)
    assertThat(summary.meanDuration).isNull()
    assertThat(summary.p95Duration).isNull()
  }

  @Test
  fun summary_evictsOldSamplesAndCalculatesP95() {
    val metrics = SampleFrameMetrics(SampleFrameMetricsSource.FrameCadence)
    metrics.add(timestampNanos = 1_000_000_000, duration = 10.nanoseconds)
    metrics.add(timestampNanos = 2_500_000_000, duration = 30.nanoseconds)
    metrics.add(timestampNanos = 2_600_000_000, duration = 20.nanoseconds)

    val summary = metrics.summary(nowNanos = 3_000_000_000)

    assertThat(summary.sampleCount).isEqualTo(2)
    assertThat(summary.meanDuration).isEqualTo(25.nanoseconds)
    assertThat(summary.p95Duration).isEqualTo(30.nanoseconds)
  }

  @Test
  fun invalidDuration_isIgnoredButReportLossIsRetained() {
    val metrics = SampleFrameMetrics(SampleFrameMetricsSource.RenderedFrameTiming)
    metrics.add(timestampNanos = 10, duration = 0.nanoseconds, droppedReports = 2)

    val summary = metrics.summary(nowNanos = 10)

    assertThat(summary.sampleCount).isEqualTo(0)
    assertThat(summary.reportLossCount).isEqualTo(2)
  }

  @Test
  fun clear_resetsSamplesAndLostReportsForModeOrDestinationChanges() {
    val metrics = SampleFrameMetrics(SampleFrameMetricsSource.FrameCadence)
    metrics.add(timestampNanos = 10, duration = 10.nanoseconds, droppedReports = 1)
    metrics.clear()

    val summary = metrics.summary(nowNanos = 10)

    assertThat(summary.sampleCount).isEqualTo(0)
    assertThat(summary.reportLossCount).isEqualTo(0)
  }
}
