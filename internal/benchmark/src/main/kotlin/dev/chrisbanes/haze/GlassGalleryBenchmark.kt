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
class GlassGalleryBenchmark {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Before
  fun requireDevice() {
    requireGlassBenchmarkDevice()
  }

  @Test
  fun productPager() {
    benchmarkRule.measureRepeated(
      packageName = GLASS_TARGET_PACKAGE,
      metrics = glassMetrics(includeMemory = true),
      compilationMode = CompilationMode.Full(),
      startupMode = StartupMode.WARM,
      iterations = GLASS_BENCHMARK_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.navigateToGlassProduct()
      },
    ) {
      device.advanceGlassProduct()
    }
  }

  @Test
  fun playgroundTimeline() {
    benchmarkRule.measureRepeated(
      packageName = GLASS_TARGET_PACKAGE,
      metrics = glassMetrics(
        includeMemory = true,
        includePreparationMetrics = true,
      ),
      compilationMode = CompilationMode.Full(),
      startupMode = StartupMode.WARM,
      iterations = GLASS_BENCHMARK_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.navigateToGlassPlayground()
      },
    ) {
      device.measureFullGlassPlaygroundLoop()
    }
  }
}
