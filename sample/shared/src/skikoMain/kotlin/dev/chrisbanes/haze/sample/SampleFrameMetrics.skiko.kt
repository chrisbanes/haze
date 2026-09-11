// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

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

@Composable
internal actual fun SampleFrameMetricsCollector(
  enabled: Boolean,
  metrics: SampleFrameMetrics,
) {
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
  LaunchedEffect(metrics) { metrics.updateSource(SampleFrameMetricsSource.FrameCadence) }
  LaunchedEffect(enabled, isForeground, metrics) {
    if (!enabled || !isForeground) return@LaunchedEffect
    var previousFrameNanos: Long? = null
    while (true) {
      val frameNanos = withFrameNanos { it }
      val interval = previousFrameNanos?.let { frameNanos - it }
      previousFrameNanos = frameNanos
      // A resume/suspension interval is not a frame cadence sample.
      if (interval != null && interval in 1L..<1_000_000_000L) {
        metrics.add(timestampNanos = frameNanos, durationNanos = interval)
      }
    }
  }
}
