// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chrisbanes.haze.Poko
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

internal val SAMPLE_METRICS_WINDOW = 2.seconds

@Poko
internal class SampleFrameMetricsSummary(
  val sampleCount: Int,
  val meanDuration: Duration?,
  val p95Duration: Duration?,
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
  val duration: Duration,
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
    duration: Duration,
    missedDeadline: Boolean? = null,
    droppedReports: Int = 0,
  ) {
    reportLossCount += droppedReports.coerceAtLeast(0)
    if (timestampNanos <= 0L || duration <= Duration.ZERO) return
    samples.addLast(SampleFrameMetric(timestampNanos, duration, missedDeadline))
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
    val durations = samples.map(SampleFrameMetric::duration).sorted()
    val deadlineSamples = samples.mapNotNull(SampleFrameMetric::missedDeadline)
    return SampleFrameMetricsSummary(
      sampleCount = durations.size,
      meanDuration = durations.takeIf(List<Duration>::isNotEmpty)?.let { it.reduce(Duration::plus) / it.size },
      p95Duration = durations.takeIf(List<Duration>::isNotEmpty)?.let { values ->
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
      samples.firstOrNull()?.timestampNanos?.let { (nowNanos - it).nanoseconds >= SAMPLE_METRICS_WINDOW } == true
    ) {
      samples.removeFirst()
    }
  }
}

@Composable
internal expect fun SampleFrameMetricsCollector(
  metrics: SampleFrameMetrics,
)

@Composable
internal fun rememberSampleMetricsForeground(metrics: SampleFrameMetrics): Boolean {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  var isForeground by remember(lifecycle) {
    mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
  }
  DisposableEffect(lifecycle, metrics) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> {
          metrics.clear()
          isForeground = true
        }
        Lifecycle.Event.ON_STOP -> {
          isForeground = false
          metrics.clear()
        }
        else -> Unit
      }
    }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }
  return isForeground
}

@Composable
internal fun SampleFrameCadenceCollector(
  isForeground: Boolean,
  metrics: SampleFrameMetrics,
) {
  LaunchedEffect(isForeground, metrics) {
    if (!isForeground) return@LaunchedEffect
    var previousFrameNanos: Long? = null
    while (true) {
      val frameNanos = withFrameNanos { it }
      val interval = previousFrameNanos?.let { (frameNanos - it).nanoseconds }
      previousFrameNanos = frameNanos
      // A resume/suspension interval is not a frame cadence sample.
      if (interval != null && interval > Duration.ZERO && interval < 1.seconds) {
        metrics.add(timestampNanos = frameNanos, duration = interval)
      }
    }
  }
}
