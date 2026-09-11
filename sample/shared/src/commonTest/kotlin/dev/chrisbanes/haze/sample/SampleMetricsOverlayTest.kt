// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.nanoseconds

@OptIn(ExperimentalTestApi::class)
class SampleMetricsOverlayTest : ContextTest() {
  @Test
  fun overlay_labelsCadenceFallback() = runComposeUiTest {
    val metrics = SampleFrameMetrics(SampleFrameMetricsSource.FrameCadence)

    setContent {
      SampleMetricsOverlay(
        performanceMode = HazePerformanceMode.Adaptive,
        metrics = metrics,
      )
    }

    onNodeWithText("Frame cadence · Adaptive").assertIsDisplayed()
  }

  @Test
  fun overlay_labelsWindowTimingAndUnavailableDeadlineData() = runComposeUiTest {
    val metrics = SampleFrameMetrics(SampleFrameMetricsSource.RenderedFrameTiming)
    metrics.add(timestampNanos = 1L, duration = 10.nanoseconds)

    setContent {
      SampleMetricsOverlay(
        isCustom = true,
        performanceMode = HazePerformanceMode.Fixed(0.7f),
        metrics = metrics,
      )
    }

    onNodeWithText("Host-window frame timing · Custom (70%)").assertIsDisplayed()
    onNodeWithText("Deadline data unavailable on this API level.").assertIsDisplayed()
  }

  @Test
  fun overlay_explainsMissingHostWindowTiming() = runComposeUiTest {
    val metrics = SampleFrameMetrics(SampleFrameMetricsSource.FrameCadence)
    metrics.updateSource(
      source = SampleFrameMetricsSource.FrameCadence,
      hostWindowTimingUnavailable = true,
    )

    setContent {
      SampleMetricsOverlay(
        performanceMode = HazePerformanceMode.Adaptive,
        metrics = metrics,
      )
    }

    onNodeWithText("Host-window timing unavailable for this screen.").assertIsDisplayed()
  }
}
