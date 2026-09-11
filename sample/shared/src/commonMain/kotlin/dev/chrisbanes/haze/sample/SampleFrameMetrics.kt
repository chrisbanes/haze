// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.Poko
import kotlin.math.ceil

internal const val SAMPLE_METRICS_WINDOW_NANOS = 2_000_000_000L

@Poko
internal class SampleFrameMetricsSummary(
  val sampleCount: Int,
  val meanNanos: Long?,
  val p95Nanos: Long?,
  val deadlineMisses: Int?,
  val deadlineEligibleFrames: Int?,
  val reportLossCount: Int,
  val source: SampleFrameMetricsSource,
  val hostWindowTimingUnavailable: Boolean,
)

internal enum class SampleFrameMetricsSource {
  RenderedFrameTiming,
  FrameCadence,
}

@Poko
private class SampleFrameMetric(
  val timestampNanos: Long,
  val durationNanos: Long,
  val missedDeadline: Boolean?,
)

/** Bounded aggregation owned by the sample shell rather than Compose snapshot state. */
internal class SampleFrameMetrics(
  initialSource: SampleFrameMetricsSource,
) {
  private val samples = ArrayDeque<SampleFrameMetric>()
  private var reportLossCount = 0
  private var latestTimestampNanos = 0L
  private var source = initialSource
  private var hostWindowTimingUnavailable = false

  fun updateSource(
    source: SampleFrameMetricsSource,
    hostWindowTimingUnavailable: Boolean = false,
  ) {
    if (this.source != source) clear()
    this.source = source
    this.hostWindowTimingUnavailable = hostWindowTimingUnavailable
  }

  fun add(
    timestampNanos: Long,
    durationNanos: Long,
    missedDeadline: Boolean? = null,
    droppedReports: Int = 0,
  ) {
    reportLossCount += droppedReports.coerceAtLeast(0)
    if (timestampNanos <= 0L || durationNanos <= 0L) return
    samples.addLast(SampleFrameMetric(timestampNanos, durationNanos, missedDeadline))
    latestTimestampNanos = maxOf(latestTimestampNanos, timestampNanos)
    evict(timestampNanos)
  }

  fun clear() {
    samples.clear()
    reportLossCount = 0
    latestTimestampNanos = 0L
  }

  fun summary(nowNanos: Long = latestTimestampNanos): SampleFrameMetricsSummary {
    evict(nowNanos)
    val durations = samples.map(SampleFrameMetric::durationNanos).sorted()
    val deadlineSamples = samples.mapNotNull(SampleFrameMetric::missedDeadline)
    return SampleFrameMetricsSummary(
      sampleCount = durations.size,
      meanNanos = durations.takeIf(List<Long>::isNotEmpty)?.average()?.toLong(),
      p95Nanos = durations.takeIf(List<Long>::isNotEmpty)?.let { values ->
        values[ceil(values.size * 0.95).toInt() - 1]
      },
      deadlineMisses = deadlineSamples.takeIf(List<Boolean>::isNotEmpty)?.count { it },
      deadlineEligibleFrames = deadlineSamples.takeIf(List<Boolean>::isNotEmpty)?.size,
      reportLossCount = reportLossCount,
      source = source,
      hostWindowTimingUnavailable = hostWindowTimingUnavailable,
    )
  }

  private fun evict(nowNanos: Long) {
    while (
      samples.firstOrNull()?.timestampNanos?.let { nowNanos - it >= SAMPLE_METRICS_WINDOW_NANOS } == true
    ) {
      samples.removeFirst()
    }
  }
}

@Composable
internal expect fun SampleFrameMetricsCollector(
  enabled: Boolean,
  metrics: SampleFrameMetrics,
)
