// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ComposeDesktopBenchmarkHostTest {
  @Test
  fun singleMeasuredCallback_isRejected() {
    val failure = assertFailsWith<IllegalStateException> {
      validateMeasuredSamples(listOf(FrameSample(renderDurationNanos = 1)))
    }
    assertThat(failure.message).isEqualTo(
      "Desktop benchmark requires at least 2 measured render callbacks for callback-interval " +
        "metrics but recorded 1",
    )
  }

  @Test
  fun twoMeasuredCallbacks_areAccepted() {
    validateMeasuredSamples(
      listOf(
        FrameSample(renderDurationNanos = 1),
        FrameSample(renderDurationNanos = 1, callbackIntervalNanos = 2),
      ),
    )
  }
}
