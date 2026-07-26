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
  fun steadyFull() = measureScenario("steady_full")

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
  fun steadyScale60() = measureScenario("steady_scale_60")

  @Test
  fun steadyScale50() = measureScenario("steady_scale_50")

  @Test
  fun steadyScale50_9() = measureScenario("steady_scale_50_9")

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
  fun depthUpdate() = measureScenario("depth_update")

  @Test
  fun blurUpdate() = measureScenario("blur_update")

  @Test
  fun sourceUpdate() = measureScenario("source_update", includeMemory = true)

  @Test
  fun sourceUpdate9() = measureScenario("source_update_9", includeMemory = true)

  @Test
  fun sourceUpdateNoGlass() = measureScenario(
    scenarioId = "source_update_no_glass",
    requireRuntimeMarker = false,
  )

  private fun measureColdInitializationScenario(scenarioId: String) {
    measureScenario(
      scenarioId = scenarioId,
      includeMemory = true,
      includeColdInitializationMetrics = true,
    )
  }

  private fun measureScenario(
    scenarioId: String,
    includeMemory: Boolean = false,
    requireRuntimeMarker: Boolean = true,
    includeColdInitializationMetrics: Boolean = false,
  ) {
    benchmarkRule.measureRepeated(
      packageName = GLASS_TARGET_PACKAGE,
      metrics = glassMetrics(
        includeMemory = includeMemory,
        requireRuntimeMarker = requireRuntimeMarker,
        includeColdInitializationMetrics = includeColdInitializationMetrics,
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
