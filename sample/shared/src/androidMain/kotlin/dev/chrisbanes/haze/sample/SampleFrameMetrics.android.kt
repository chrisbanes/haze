// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal actual fun SampleFrameMetricsCollector(
  enabled: Boolean,
  metrics: SampleFrameMetrics,
) {
  val activity = LocalContext.current as? Activity
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
  if (androidFrameMetricsSourceForSdk(Build.VERSION.SDK_INT) == SampleFrameMetricsSource.RenderedFrameTiming && activity != null) {
    LaunchedEffect(metrics) { metrics.updateSource(SampleFrameMetricsSource.RenderedFrameTiming) }
    AndroidFrameMetricsCollector(enabled, activity, isForeground, metrics)
  } else {
    LaunchedEffect(metrics, activity) {
      metrics.updateSource(
        source = SampleFrameMetricsSource.FrameCadence,
        hostWindowTimingUnavailable = Build.VERSION.SDK_INT >= 24 && activity == null,
      )
    }
    CadenceFallbackCollector(enabled, isForeground, metrics)
  }
}

@Composable
private fun CadenceFallbackCollector(
  enabled: Boolean,
  isForeground: Boolean,
  metrics: SampleFrameMetrics,
) {
  LaunchedEffect(enabled, isForeground, metrics) {
    if (!enabled || !isForeground) return@LaunchedEffect
    var previousFrameNanos: Long? = null
    while (true) {
      val frameNanos = withFrameNanos { it }
      val interval = previousFrameNanos?.let { frameNanos - it }
      previousFrameNanos = frameNanos
      if (interval != null && interval in 1L..<1_000_000_000L) {
        metrics.add(timestampNanos = frameNanos, durationNanos = interval)
      }
    }
  }
}

@Composable
private fun AndroidFrameMetricsCollector(
  enabled: Boolean,
  activity: Activity,
  isForeground: Boolean,
  metrics: SampleFrameMetrics,
) {
  val listener = remember(activity.window, metrics) {
    WindowFrameMetricsListener { durationNanos, deadlineNanos, droppedReports, firstDraw ->
      metrics.recordAndroidFrame(
        // System.nanoTime and Compose frame timestamps share Android's monotonic time base.
        timestampNanos = System.nanoTime(),
        durationNanos = durationNanos,
        deadlineNanos = deadlineNanos,
        droppedReports = droppedReports,
        firstDraw = firstDraw,
      )
    }
  }
  val registration = remember(activity.window, listener) {
    AndroidFrameMetricsRegistration(
      onAttach = {
        activity.window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
      },
      onDetach = { activity.window.removeOnFrameMetricsAvailableListener(listener) },
    )
  }
  DisposableEffect(enabled, activity.window, isForeground, metrics) {
    registration.update(enabled = enabled, isForeground = isForeground)
    onDispose { registration.detach() }
  }
}

internal class AndroidFrameMetricsRegistration(
  private val onAttach: () -> Unit,
  private val onDetach: () -> Unit,
) {
  private var attached = false

  fun update(enabled: Boolean, isForeground: Boolean) {
    if (enabled && isForeground) attach() else detach()
  }

  fun detach() {
    if (attached) {
      onDetach()
      attached = false
    }
  }

  private fun attach() {
    if (!attached) {
      onAttach()
      attached = true
    }
  }
}

internal fun SampleFrameMetrics.recordAndroidFrame(
  timestampNanos: Long,
  durationNanos: Long,
  deadlineNanos: Long?,
  droppedReports: Int,
  firstDraw: Boolean,
) {
  if (firstDraw) {
    if (droppedReports > 0) add(timestampNanos, 0, droppedReports = droppedReports)
  } else {
    add(
      timestampNanos = timestampNanos,
      durationNanos = durationNanos,
      missedDeadline = deadlineNanos?.let { durationNanos > it },
      droppedReports = droppedReports,
    )
  }
}

private class WindowFrameMetricsListener(
  private val onFrame: (
    durationNanos: Long,
    deadlineNanos: Long?,
    droppedReports: Int,
    firstDraw: Boolean,
  ) -> Unit,
) : android.view.Window.OnFrameMetricsAvailableListener {
  override fun onFrameMetricsAvailable(
    window: android.view.Window,
    frameMetrics: FrameMetrics,
    dropCountSinceLastInvocation: Int,
  ) {
    val durationNanos = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
    val deadlineNanos = androidDeadlineNanos(
      sdkInt = Build.VERSION.SDK_INT,
      readDeadlineNanos = { frameMetrics.getMetric(FrameMetrics.DEADLINE) },
    )
    onFrame(
      durationNanos,
      deadlineNanos,
      dropCountSinceLastInvocation,
      frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 0L,
    )
  }
}

internal fun androidFrameMetricsSourceForSdk(sdkInt: Int): SampleFrameMetricsSource =
  if (sdkInt >= 24) SampleFrameMetricsSource.RenderedFrameTiming else SampleFrameMetricsSource.FrameCadence

internal fun androidDeadlineNanos(
  sdkInt: Int,
  readDeadlineNanos: () -> Long,
): Long? = if (sdkInt >= 31) readDeadlineNanos().takeIf { it > 0L } else null
