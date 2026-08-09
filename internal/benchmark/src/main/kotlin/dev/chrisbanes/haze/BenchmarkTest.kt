// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val DEFAULT_ITERATIONS = 16
private const val APP_PACKAGE = "dev.chrisbanes.haze.sample.android"

@RunWith(AndroidJUnit4::class)
class BenchmarkTest {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun imagesList() {
    measureSample(
      navigate = { navigateToImagesList() },
      measure = { repeatedScrolls("lazy_column") },
    )
  }

  @Test
  fun blurStableAdaptive() = measureBlurProfilingScenario("stable_adaptive")

  @Test
  fun blurStableQuality() = measureBlurProfilingScenario("stable_quality")

  @Test
  fun blurStableBalanced() = measureBlurProfilingScenario("stable_balanced")

  @Test
  fun blurStablePerformance() = measureBlurProfilingScenario("stable_performance")

  @Test
  fun blurSourceUpdateAdaptive() = measureBlurProfilingScenario("source_update_adaptive")

  @Test
  fun blurSourceUpdateQuality() = measureBlurProfilingScenario("source_update_quality")

  @Test
  fun blurSourceUpdateBalanced() = measureBlurProfilingScenario("source_update_balanced")

  @Test
  fun blurSourceUpdatePerformance() = measureBlurProfilingScenario("source_update_performance")

  @Test
  fun creditCard() {
    measureSample(
      navigate = { navigateToCreditCard() },
      measure = { repeatedDrags("credit_card_2") },
    )
  }

  private fun measureBlurProfilingScenario(scenarioId: String) {
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = DEFAULT_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.navigateToBlurProfiling(scenarioId)
      },
    ) {
      device.runBlurProfilingScenario(scenarioId)
    }
  }

  private fun measureSample(
    iterations: Int = DEFAULT_ITERATIONS,
    navigate: UiDevice.() -> Unit,
    measure: UiDevice.() -> Unit,
  ) {
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = iterations,
      setupBlock = {
        startActivityAndWait()
        device.navigate()
      },
    ) {
      device.measure()
    }
  }
}
