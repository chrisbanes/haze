// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.ui.platform.LocalView
import dev.chrisbanes.haze.HazeEffectLifecycleScope

internal actual fun platformGlassAdaptiveTimingSource(
  scope: HazeEffectLifecycleScope,
  hostKey: Any,
): GlassAdaptiveTimingSource? {
  if (Build.VERSION.SDK_INT < 24) return null
  val view = try {
    scope.currentValueOf(LocalView)
  } catch (_: IllegalStateException) {
    return null
  } catch (_: ClassCastException) {
    return null
  }
  if (view.windowToken !== hostKey) return null
  return verifiedGlassWindow(view)?.let(::AndroidGlassTimingSource)
}

internal class AndroidGlassTimingSource(
  private val window: Window,
  private val addListener: (Window.OnFrameMetricsAvailableListener) -> Unit = {
    window.addOnFrameMetricsAvailableListener(it, Handler(Looper.getMainLooper()))
  },
  private val removeListener: (Window.OnFrameMetricsAvailableListener) -> Unit = {
    window.removeOnFrameMetricsAvailableListener(it)
  },
) : GlassAdaptiveTimingSource {
  override val identity: Any get() = window

  private var listener: Window.OnFrameMetricsAvailableListener? = null
  private var sequence = 0L

  override fun start(onSample: (GlassFrameHealthSample, Long) -> Unit): Boolean {
    if (listener != null || Build.VERSION.SDK_INT < 24) return false
    sequence = 0L
    lateinit var next: Window.OnFrameMetricsAvailableListener
    next = Window.OnFrameMetricsAvailableListener { reportedWindow, frameMetrics, dropped ->
      if (listener !== next) return@OnFrameMetricsAvailableListener
      if (reportedWindow !== window) return@OnFrameMetricsAvailableListener
      // Copy primitive values now; the framework reuses FrameMetrics after this callback.
      val durationNanos = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
      val deadlineNanos = if (Build.VERSION.SDK_INT >= 31) {
        frameMetrics.getMetric(FrameMetrics.DEADLINE)
      } else {
        null
      }
      val firstDraw = frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 0L
      val now = System.nanoTime()
      sequence += dropped.toLong().coerceAtLeast(0L) + 1L
      val estimatedBudget = window.decorView.display?.refreshRate
        ?.takeIf { it > 0f }
        ?.let { (1_000_000_000.0 / it).toLong() }
        ?: 0L
      onSample(
        androidGlassFrameSample(
          sequence = sequence,
          timestampNanos = now,
          durationNanos = durationNanos,
          budgetNanos = deadlineNanos ?: estimatedBudget,
          firstDraw = firstDraw,
          droppedReports = dropped,
        ),
        now,
      )
    }
    return try {
      addListener(next)
      listener = next
      true
    } catch (_: IllegalArgumentException) {
      false
    } catch (_: IllegalStateException) {
      false
    }
  }

  override fun stop() {
    listener?.let(removeListener)
    listener = null
  }
}

internal fun androidGlassFrameSample(
  sequence: Long,
  timestampNanos: Long,
  durationNanos: Long,
  budgetNanos: Long,
  firstDraw: Boolean,
  droppedReports: Int,
): GlassFrameHealthSample = GlassFrameHealthSample(
  sequence = sequence,
  timestampNanos = timestampNanos,
  durationNanos = durationNanos,
  budgetNanos = budgetNanos,
  isValid = !firstDraw && droppedReports == 0 && durationNanos > 0L && budgetNanos > 0L,
)
