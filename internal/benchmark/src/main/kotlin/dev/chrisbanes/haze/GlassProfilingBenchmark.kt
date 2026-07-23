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
  fun effectAttach() = measureScenario("effect_attach", includeMemory = true)

  @Test
  fun retainedReuse() = measureScenario("retained_reuse")

  @Test
  fun interactionUpdate() = measureScenario("interaction_update")

  @Test
  fun opticalUpdate() = measureScenario("optical_update")

  @Test
  fun depthUpdate() = measureScenario("depth_update")

  @Test
  fun blurUpdate() = measureScenario("blur_update")

  @Test
  fun sourceUpdate() = measureScenario("source_update", includeMemory = true)

  @Test
  fun sourceUpdateNoGlass() = measureScenario(
    scenarioId = "source_update_no_glass",
    requireRuntimeMarker = false,
  )

  private fun measureScenario(
    scenarioId: String,
    includeMemory: Boolean = false,
    requireRuntimeMarker: Boolean = true,
  ) {
    benchmarkRule.measureRepeated(
      packageName = GLASS_TARGET_PACKAGE,
      metrics = glassMetrics(
        includeMemory = includeMemory,
        requireRuntimeMarker = requireRuntimeMarker,
      ),
      compilationMode = CompilationMode.Full(),
      startupMode = StartupMode.WARM,
      iterations = GLASS_BENCHMARK_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.navigateToGlassProfiling(scenarioId)
      },
    ) {
      device.runGlassProfilingScenario()
    }
  }
}
