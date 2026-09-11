// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlinx.coroutines.delay

@Composable
internal fun SampleMetricsOverlay(
  isCustom: Boolean = false,
  performanceMode: HazePerformanceMode,
  metrics: SampleFrameMetrics,
  modifier: Modifier = Modifier,
) {
  var summary by remember(metrics) { mutableStateOf(metrics.summary()) }
  LaunchedEffect(metrics) {
    while (true) {
      summary = metrics.summary(withFrameNanos { it })
      delay(250.milliseconds)
    }
  }
  val label = if (summary.source == SampleFrameMetricsSource.FrameCadence) {
    "Frame cadence"
  } else {
    "Host-window frame timing"
  }
  Column(
    modifier = modifier
      .background(
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
      )
      .padding(12.dp)
      .testTag("sample_metrics_overlay"),
  ) {
    Text(
      "$label · ${if (isCustom) "Custom" else performanceMode.sampleLabel}${
        if (isCustom) performanceMode.customFractionLabel() else ""
      }",
      color = MaterialTheme.colorScheme.inverseOnSurface,
    )
    if (summary.sampleCount == 0) {
      Text("Collecting…", color = MaterialTheme.colorScheme.inverseOnSurface)
    } else {
      Text(
        "${summary.sampleCount} samples · mean ${summary.meanDuration.toMillis()} · " +
          "P95 ${summary.p95Duration.toMillis()}",
        color = MaterialTheme.colorScheme.inverseOnSurface,
      )
      summary.deadlineMisses?.let { misses ->
        Text(
          "Deadline misses: $misses / ${summary.deadlineEligibleFrames}",
          color = MaterialTheme.colorScheme.inverseOnSurface,
        )
      }
      if (
        summary.source == SampleFrameMetricsSource.RenderedFrameTiming &&
        summary.deadlineMisses == null
      ) {
        Text(
          "Deadline data unavailable on this API level.",
          color = MaterialTheme.colorScheme.inverseOnSurface,
        )
      }
      if (summary.reportLossCount > 0) {
        Text(
          "Callback reports lost: ${summary.reportLossCount}",
          color = MaterialTheme.colorScheme.inverseOnSurface,
        )
      }
    }
    if (summary.hostWindowTimingUnavailable) {
      Text(
        "Host-window timing unavailable for this screen.",
        color = MaterialTheme.colorScheme.inverseOnSurface,
      )
    }
    Text(
      "Diagnostic readings affected by the observer.",
      color = MaterialTheme.colorScheme.inverseOnSurface,
    )
  }
}

private fun HazePerformanceMode.customFractionLabel(): String = when (this) {
  is HazePerformanceMode.Fixed -> " (${qualityFraction.formatFraction()})"
  HazePerformanceMode.Adaptive -> ""
}

private fun Duration?.toMillis(): String = this?.toString(DurationUnit.MILLISECONDS, decimals = 1) ?: "no data"
