// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal actual fun SampleFrameMetricsCollector(
  metrics: SampleFrameMetrics,
) {
  val isForeground = rememberSampleMetricsForeground(metrics)
  LaunchedEffect(metrics) { metrics.updateSource(SampleFrameMetricsSource.FrameCadence) }
  SampleFrameCadenceCollector(isForeground, metrics)
}
