// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

class AndroidSampleFrameMetricsTest : ContextTest() {
  @Test
  fun renderedFrames_excludeFirstDrawAndKeepCallbackLossSeparate() {
    val metrics = SampleFrameMetrics(SampleFrameMetricsSource.RenderedFrameTiming)
    metrics.recordAndroidFrame(10, 20, deadlineNanos = 16, droppedReports = 2, firstDraw = true)
    metrics.recordAndroidFrame(20, 20, deadlineNanos = 16, droppedReports = 1, firstDraw = false)

    val summary = metrics.summary(nowNanos = 20)

    assertThat(summary.sampleCount).isEqualTo(1)
    assertThat(summary.deadlineMisses).isEqualTo(1)
    assertThat(summary.deadlineEligibleFrames).isEqualTo(1)
    assertThat(summary.reportLossCount).isEqualTo(3)
  }

  @Test
  fun preDeadlineApi_readingHasNoDeadlineResult() {
    val metrics = SampleFrameMetrics(SampleFrameMetricsSource.RenderedFrameTiming)
    metrics.recordAndroidFrame(10, 20, deadlineNanos = null, droppedReports = 0, firstDraw = false)

    val summary = metrics.summary(nowNanos = 10)

    assertThat(summary.deadlineMisses).isNull()
    assertThat(summary.deadlineEligibleFrames).isNull()
  }

  @Test
  fun api23_usesCadenceFallbackAndApi24To30HideDeadlines() {
    assertThat(androidFrameMetricsSourceForSdk(23)).isEqualTo(SampleFrameMetricsSource.FrameCadence)
    assertThat(androidFrameMetricsSourceForSdk(24)).isEqualTo(SampleFrameMetricsSource.RenderedFrameTiming)
    assertThat(androidDeadlineNanos(24, 16)).isNull()
    assertThat(androidDeadlineNanos(30, 16)).isNull()
  }

  @Test
  fun api31_exposesOnlyValidDeadlines() {
    assertThat(androidDeadlineNanos(31, 16)).isEqualTo(16)
    assertThat(androidDeadlineNanos(31, 0)).isNull()
  }

  @Test
  fun registration_detachesWhenDisabledOrBackgrounded() {
    val events = mutableListOf<String>()
    val registration = AndroidFrameMetricsRegistration(
      onAttach = { events += "attach" },
      onDetach = { events += "detach" },
    )

    registration.update(enabled = true, isForeground = true)
    registration.update(enabled = false, isForeground = true)
    registration.update(enabled = true, isForeground = true)
    registration.update(enabled = true, isForeground = false)
    registration.detach()

    assertThat(events).isEqualTo(listOf("attach", "detach", "attach", "detach"))
  }
}
