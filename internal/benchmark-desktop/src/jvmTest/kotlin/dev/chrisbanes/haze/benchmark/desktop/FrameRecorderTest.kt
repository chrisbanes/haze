// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import assertk.assertThat
import assertk.assertions.containsExactly
import kotlin.test.Test

class FrameRecorderTest {
  @Test
  fun warmupFrames_areExcluded() {
    val clock = FakeNanoClock(100, 110, 120, 135, 150)
    val recorder = FrameRecorder(clock::next)
    recorder.beforeFrameRender()
    recorder.afterFrameRender()
    recorder.startMeasurement()
    recorder.beforeFrameRender()
    recorder.afterFrameRender()
    assertThat(recorder.stopMeasurement()).containsExactly(
      FrameSample(renderDurationNanos = 15, callbackIntervalNanos = null),
    )
  }

  @Test
  fun intervalStartsWithSecondMeasuredFrame() {
    val recorder = recorderForTwoMeasuredFrames()
    assertThat(recorder.stopMeasurement().map { it.callbackIntervalNanos })
      .containsExactly(null, 20L)
  }
}

private class FakeNanoClock(vararg values: Long) {
  private val iterator = values.iterator()

  fun next(): Long = iterator.nextLong()
}

private fun recorderForTwoMeasuredFrames(): FrameRecorder {
  val clock = FakeNanoClock(100, 110, 115, 130)
  return FrameRecorder(clock::next).apply {
    startMeasurement()
    beforeFrameRender()
    afterFrameRender()
    beforeFrameRender()
    afterFrameRender()
  }
}
