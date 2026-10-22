// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Four separate Glass controls over one busy feed source (issue #1611), with and without
 * `HazeFeatureFlags.isOffscreenSourceEnabled`, against the same controls drawn without Haze.
 *
 * `sourceReplay` counts HWUI's `alpha caused saveLayer` slices: the feed fades every fourth card,
 * so each replay of a faded card on the RenderThread adds one. Divide by `frameCount`.
 */
@RunWith(AndroidJUnit4::class)
class GlassFeedBenchmark {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Before
  fun requireDevice() {
    requireGlassBenchmarkDevice()
  }

  @Test
  fun feedVideoGlass() = measure("feed_video_glass")

  @Test
  fun feedVideoGlassOffscreen() = measure("feed_video_glass_offscreen")

  @Test
  fun feedVideoPlain() = measure("feed_video_plain")

  @Test
  fun feedScrollGlass() = measure("feed_scroll_glass")

  @Test
  fun feedScrollGlassOffscreen() = measure("feed_scroll_glass_offscreen")

  @Test
  fun feedScrollPlain() = measure("feed_scroll_plain")

  @OptIn(ExperimentalMetricApi::class)
  private fun measure(scenarioId: String) {
    withoutUiAutomatorIdleWait {
      benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
          FrameTimingMetric(),
          MemoryUsageMetric(
            mode = MemoryUsageMetric.Mode.Max,
            subMetrics = listOf(
              MemoryUsageMetric.SubMetric.Gpu,
              MemoryUsageMetric.SubMetric.RssAnon,
            ),
          ),
          TraceSectionMetric(
            sectionName = "alpha caused saveLayer%",
            mode = TraceSectionMetric.Mode.Count,
            label = "sourceReplay",
          ),
          TraceSectionMetric(
            sectionName = "DrawFrames%",
            mode = TraceSectionMetric.Mode.Average,
            label = "drawFrames",
          ),
          TraceSectionMetric(
            sectionName = HWUI_GRAPHICS_LAYER_DRAW_SECTION,
            mode = TraceSectionMetric.Mode.Count,
            label = "graphicsLayerDraw",
          ),
        ),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.WARM,
        iterations = BENCHMARK_ITERATIONS,
        setupBlock = {
          startActivityAndWait { intent ->
            intent.selectBenchmarkSample(
              route = "glass-feed-profiling",
              effect = "glass",
              scenarioId = scenarioId,
            )
          }
          device.waitForGlassProfilingScenario(scenarioId)
        },
      ) {
        device.runGlassProfilingScenario(scenarioId)
      }
    }
  }
}
