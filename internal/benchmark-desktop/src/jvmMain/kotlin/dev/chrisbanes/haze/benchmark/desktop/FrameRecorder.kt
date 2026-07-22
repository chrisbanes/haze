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
  private var previousFrameEnd: Long? = null
  private var callbackCount = 0L
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
    measuring = true
  }

  internal fun stopMeasurement(): List<FrameSample> = synchronized(lock) {
    measuring = false
    samples.toList()
  }

  internal fun beforeFrameRender() = synchronized(lock) {
    frameStart = nanoTime()
  }

  internal fun afterFrameRender() = synchronized(lock) {
    val end = nanoTime()
    callbackCount++
    if (!measuring) return@synchronized
    require(end >= frameStart)
    samples += FrameSample(
      renderDurationNanos = end - frameStart,
      callbackIntervalNanos = previousFrameEnd?.let { end - it },
    )
    previousFrameEnd = end
  }

  internal fun callbackCount(): Long = synchronized(lock) { callbackCount }
}
