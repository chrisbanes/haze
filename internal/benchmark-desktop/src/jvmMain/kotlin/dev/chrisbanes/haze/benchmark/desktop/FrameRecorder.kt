// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import org.jetbrains.skiko.ExperimentalSkikoApi
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.SkiaLayerAnalytics

@OptIn(ExperimentalSkikoApi::class)
internal class FrameRecorder(
  private val nanoTime: () -> Long = System::nanoTime,
) : SkiaLayerAnalytics {
  private val lock = Any()
  private var measuring = false
  private var frameStart = 0L
  private var frameStartedWhileMeasuring = false
  private var previousFrameEnd: Long? = null
  private var startedFrameGeneration = 0L
  private var activeFrameGeneration = 0L
  private var completedFrameGeneration = 0L
  private val samples = mutableListOf<FrameSample>()

  private val deviceAnalytics = object : SkiaLayerAnalytics.DeviceAnalytics {
    override fun beforeFirstFrameRender() = this@FrameRecorder.beforeFrameRender()

    override fun afterFirstFrameRender() = this@FrameRecorder.afterFrameRender()

    override fun beforeFrameRender() = this@FrameRecorder.beforeFrameRender()

    override fun afterFrameRender() = this@FrameRecorder.afterFrameRender()
  }

  override fun device(
    skikoVersion: String,
    os: OS,
    api: GraphicsApi,
    deviceName: String?,
  ): SkiaLayerAnalytics.DeviceAnalytics = deviceAnalytics

  internal fun startMeasurement() = synchronized(lock) {
    samples.clear()
    previousFrameEnd = null
    frameStartedWhileMeasuring = false
    measuring = true
  }

  internal fun stopMeasurement(): List<FrameSample> = synchronized(lock) {
    measuring = false
    samples.toList()
  }

  internal fun beforeFrameRender() = synchronized(lock) {
    frameStart = nanoTime()
    frameStartedWhileMeasuring = measuring
    activeFrameGeneration = ++startedFrameGeneration
  }

  internal fun afterFrameRender() = synchronized(lock) {
    val end = nanoTime()
    completedFrameGeneration = maxOf(completedFrameGeneration, activeFrameGeneration)
    val shouldRecord = measuring && frameStartedWhileMeasuring
    frameStartedWhileMeasuring = false
    if (!shouldRecord) return@synchronized
    require(end >= frameStart)
    samples += FrameSample(
      renderDurationNanos = end - frameStart,
      callbackIntervalNanos = previousFrameEnd?.let { end - it },
    )
    previousFrameEnd = end
  }

  internal fun armNextFrameCompletion(): FrameCompletionToken = synchronized(lock) {
    FrameCompletionToken(startedFrameGeneration + 1)
  }

  internal fun isFrameCompleted(token: FrameCompletionToken): Boolean = synchronized(lock) {
    completedFrameGeneration >= token.generation
  }
}

internal data class FrameCompletionToken(val generation: Long)
