// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassProfilingBenchmark {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Before
  fun requireDevice() {
    requireGlassBenchmarkDevice()
  }

  @Test
  fun effectAttach() = measureColdInitializationScenario("effect_attach")

  @Test
  fun effectAttach3() = measureColdInitializationScenario("effect_attach_3")

  @Test
  fun effectAttach9() = measureColdInitializationScenario("effect_attach_9")

  @Test
  fun effectReattach() = measureColdInitializationScenario("effect_reattach")

  @Test
  fun stableAdaptive() = measureCalibrationScenario("stable_adaptive")

  @Test
  fun stableQuality() = measureBackdropComparisonScenario("stable_quality")

  @Test
  fun backdropStableQuality() {
    requireBackdropBenchmarkDevice()
    measureBackdropComparisonScenario("backdrop_stable_quality", requireBackdropDraw = true)
  }

  @Test
  fun stableBalanced() = measureCalibrationScenario("stable_balanced")

  @Test
  fun stablePerformance() = measureCalibrationScenario("stable_performance")

  @Test
  fun sourceUpdateAdaptive() = measureCalibrationScenario("source_update_adaptive")

  @Test
  fun sourceUpdateQuality() = measureBackdropComparisonScenario("source_update_quality")

  @Test
  fun backdropSourceUpdateQuality() {
    requireBackdropBenchmarkDevice()
    measureBackdropComparisonScenario(
      "backdrop_source_update_quality",
      requireBackdropDraw = true,
    )
  }

  @Test
  fun sourceUpdateBalanced() = measureCalibrationScenario("source_update_balanced")

  @Test
  fun sourceUpdatePerformance() = measureCalibrationScenario("source_update_performance")

  @Test
  fun steadyFull3() = measureScenario("steady_full_3")

  @Test
  fun steadyFull9() = measureScenario("steady_full_9")

  @Test
  fun steadyProgressive() = measureScenario("steady_progressive")

  @Test
  fun steadyProgressive9() = measureScenario("steady_progressive_9")

  @Test
  fun steadyFullChroma() = measureScenario("steady_full_chroma")

  @Test
  fun steadyFullChroma9() = measureScenario("steady_full_chroma_9")

  @Test
  fun steadyNoRim() = measureScenario("steady_no_rim")

  @Test
  fun steadyNoRim9() = measureScenario("steady_no_rim_9")

  @Test
  fun steadyNoRefraction() = measureScenario("steady_no_refraction")

  @Test
  fun steadyNoRefraction9() = measureScenario("steady_no_refraction_9")

  @Test
  fun steadyNoBlur() = measureScenario("steady_no_blur")

  @Test
  fun steadyNoBlur9() = measureScenario("steady_no_blur_9")

  @Test
  fun steadyDepth50() = measureScenario("steady_depth_50")

  @Test
  fun steadyPerformance9() = measureScenario("steady_performance_9")

  @Test
  fun steadyNoGlass() = measureScenario(
    scenarioId = "steady_no_glass",
    requireRuntimeMarker = false,
  )

  @Test
  fun retainedReuse() = measureScenario("retained_reuse")

  @Test
  fun interactionUpdate() = measureScenario("interaction_update")

  @Test
  fun interactionUpdate9() = measureScenario("interaction_update_9")

  @Test
  fun opticalUpdate() = measureScenario("optical_update")

  @Test
  fun depthUpdate() = measureScenario(
    scenarioId = "depth_update",
    includePreparationMetrics = true,
  )

  @Test
  fun blurUpdate() = measureScenario("blur_update")

  @Test
  fun sourceUpdate9() = measureBackdropComparisonScenario("source_update_9")

  @Test
  fun backdropSourceUpdate9() {
    requireBackdropBenchmarkDevice()
    measureBackdropComparisonScenario("backdrop_source_update_9", requireBackdropDraw = true)
  }

  @Test
  fun sourceUpdateNoGlass() = measureScenario(
    scenarioId = "source_update_no_glass",
    requireRuntimeMarker = false,
  )

  private fun measureColdInitializationScenario(scenarioId: String) {
    measureScenario(
      scenarioId = scenarioId,
      includeMemory = true,
      includePreparationMetrics = true,
    )
  }

  private fun measureCalibrationScenario(scenarioId: String) {
    measureScenario(scenarioId = scenarioId, includeMemory = true)
  }

  private fun measureBackdropComparisonScenario(
    scenarioId: String,
    requireBackdropDraw: Boolean = false,
  ) {
    measureScenario(
      scenarioId = scenarioId,
      includeMemory = true,
      includeBackdropComparisonMetrics = true,
      requireBackdropDraw = requireBackdropDraw,
    )
  }

  private fun measureScenario(
    scenarioId: String,
    includeMemory: Boolean = false,
    requireRuntimeMarker: Boolean = true,
    includePreparationMetrics: Boolean = false,
    includeBackdropComparisonMetrics: Boolean = false,
    requireBackdropDraw: Boolean = false,
  ) {
    benchmarkRule.measureRepeated(
      packageName = GLASS_TARGET_PACKAGE,
      metrics = glassMetrics(
        includeMemory = includeMemory,
        requireRuntimeMarker = requireRuntimeMarker,
        includePreparationMetrics = includePreparationMetrics,
        includeBackdropComparisonMetrics = includeBackdropComparisonMetrics,
        requireBackdropDraw = requireBackdropDraw,
      ),
      compilationMode = CompilationMode.Full(),
      startupMode = StartupMode.WARM,
      iterations = GLASS_BENCHMARK_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.navigateToGlassProfiling(scenarioId)
      },
    ) {
      device.runGlassProfilingScenario(scenarioId)
    }
  }
}
