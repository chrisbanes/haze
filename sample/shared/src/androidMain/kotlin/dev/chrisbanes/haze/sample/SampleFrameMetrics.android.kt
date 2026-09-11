// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

@Composable
internal actual fun SampleFrameMetricsCollector(
  metrics: SampleFrameMetrics,
) {
  val activity = LocalContext.current as? Activity
  val isForeground = rememberSampleMetricsForeground(metrics)
  if (androidFrameMetricsSourceForSdk(Build.VERSION.SDK_INT) == SampleFrameMetricsSource.RenderedFrameTiming && activity != null) {
    LaunchedEffect(metrics) { metrics.updateSource(SampleFrameMetricsSource.RenderedFrameTiming) }
    AndroidFrameMetricsCollector(activity, isForeground, metrics)
  } else {
    LaunchedEffect(metrics, activity) {
      metrics.updateSource(
        source = SampleFrameMetricsSource.FrameCadence,
        hostWindowTimingUnavailable = Build.VERSION.SDK_INT >= 24 && activity == null,
      )
    }
    SampleFrameCadenceCollector(isForeground, metrics)
  }
}

@Composable
private fun AndroidFrameMetricsCollector(
  activity: Activity,
  isForeground: Boolean,
  metrics: SampleFrameMetrics,
) {
  val listener = remember(activity.window, metrics) {
    Window.OnFrameMetricsAvailableListener { _, frameMetrics, droppedReports ->
      metrics.recordAndroidFrame(
        // System.nanoTime and Compose frame timestamps share Android's monotonic time base.
        timestampNanos = System.nanoTime(),
        durationNanos = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION),
        deadlineNanos = androidDeadlineNanos(
          sdkInt = Build.VERSION.SDK_INT,
          readDeadlineNanos = { frameMetrics.getMetric(FrameMetrics.DEADLINE) },
        ),
        droppedReports = droppedReports,
        firstDraw = frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 0L,
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
  DisposableEffect(activity.window, isForeground, metrics) {
    registration.update(isForeground = isForeground)
    onDispose { registration.detach() }
  }
}

internal class AndroidFrameMetricsRegistration(
  private val onAttach: () -> Unit,
  private val onDetach: () -> Unit,
) {
  private var attached = false

  fun update(isForeground: Boolean) {
    if (isForeground) attach() else detach()
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
    if (droppedReports > 0) add(timestampNanos, Duration.ZERO, droppedReports = droppedReports)
  } else {
    add(
      timestampNanos = timestampNanos,
      duration = durationNanos.nanoseconds,
      missedDeadline = deadlineNanos?.let { durationNanos > it },
      droppedReports = droppedReports,
    )
  }
}

internal fun androidFrameMetricsSourceForSdk(sdkInt: Int): SampleFrameMetricsSource =
  if (sdkInt >= 24) SampleFrameMetricsSource.RenderedFrameTiming else SampleFrameMetricsSource.FrameCadence

internal fun androidDeadlineNanos(
  sdkInt: Int,
  readDeadlineNanos: () -> Long,
): Long? = if (sdkInt >= 31) readDeadlineNanos().takeIf { it > 0L } else null
