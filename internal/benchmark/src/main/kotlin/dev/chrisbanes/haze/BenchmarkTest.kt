// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.SystemClock
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val DEFAULT_ITERATIONS = 8
private const val APP_PACKAGE = "dev.chrisbanes.haze.sample.android"

@RunWith(AndroidJUnit4::class)
class BenchmarkTest {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Test
  fun imagesList() {
    measureSample(
      sampleRoute = "images-list",
      awaitReady = { waitForImagesList() },
      measure = { repeatedScrolls("lazy_column") },
    )
  }

  @Test
  fun blurStableAdaptive() = measureBlurProfilingScenario("stable_adaptive")

  @Test
  fun blurStableQuality() = measureBlurProfilingScenario(
    scenarioId = "stable_quality",
    includeBackdropComparisonMetrics = true,
  )

  @Test
  fun blurBackdropStableQuality() {
    requireBackdropBenchmarkDevice()
    measureBlurProfilingScenario(
      scenarioId = "backdrop_stable_quality",
      includeBackdropComparisonMetrics = true,
      requireBackdropDraw = true,
    )
  }

  @Test
  fun blurStableBalanced() = measureBlurProfilingScenario("stable_balanced")

  @Test
  fun blurStablePerformance() = measureBlurProfilingScenario("stable_performance")

  @Test
  fun blurProgressiveQuality() = measureBlurProfilingScenario("progressive_quality")

  @Test
  fun blurProgressiveBalanced() = measureBlurProfilingScenario("progressive_balanced")

  @Test
  fun blurSourceUpdateAdaptive() = measureBlurProfilingScenario("source_update_adaptive")

  @Test
  fun scaffoldEquivalentStyleChurn() {
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = DEFAULT_ITERATIONS,
      setupBlock = {
        startActivityAndWait { intent ->
          intent.putExtra(FORCE_BLUR_EXTRA, true)
          intent.selectBenchmarkSample(route = "blur-style-churn", effect = "blur")
        }
        device.waitForScaffoldWithEquivalentStyleChurn()
      },
    ) {
      SystemClock.sleep(STYLE_CHURN_MEASURE_MILLIS)
    }
  }

  @Test
  fun blurSourceUpdateQuality() = measureBlurProfilingScenario(
    scenarioId = "source_update_quality",
    includeBackdropComparisonMetrics = true,
  )

  @Test
  fun blurBackdropSourceUpdateQuality() {
    requireBackdropBenchmarkDevice()
    measureBlurProfilingScenario(
      scenarioId = "backdrop_source_update_quality",
      includeBackdropComparisonMetrics = true,
      requireBackdropDraw = true,
    )
  }

  @Test
  fun blurSourceUpdateBalanced() = measureBlurProfilingScenario("source_update_balanced")

  @Test
  fun blurSourceUpdatePerformance() = measureBlurProfilingScenario("source_update_performance")

  @Test
  fun creditCard() {
    measureSample(
      sampleRoute = "credit-card",
      awaitReady = { waitForCreditCard() },
      measure = { repeatedDrags("credit_card_2") },
    )
  }

  private fun measureBlurProfilingScenario(
    scenarioId: String,
    includeBackdropComparisonMetrics: Boolean = false,
    requireBackdropDraw: Boolean = false,
  ) {
    withoutUiAutomatorIdleWait {
      benchmarkRule.measureRepeated(
        packageName = APP_PACKAGE,
        metrics = if (includeBackdropComparisonMetrics) {
          backdropComparisonMetrics(requireBackdropDraw)
        } else {
          listOf(FrameTimingMetric())
        },
        startupMode = StartupMode.WARM,
        iterations = DEFAULT_ITERATIONS,
        setupBlock = {
          startActivityAndWait { intent ->
            intent.selectBenchmarkSample(
              route = "blur-profiling",
              effect = "blur",
              scenarioId = scenarioId,
            )
          }
          device.waitForBlurProfilingScenario(scenarioId)
        },
      ) {
        device.runBlurProfilingScenario(scenarioId)
      }
    }
  }

  private fun measureSample(
    sampleRoute: String,
    iterations: Int = DEFAULT_ITERATIONS,
    awaitReady: UiDevice.() -> Unit,
    measure: UiDevice.() -> Unit,
  ) {
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = iterations,
      setupBlock = {
        startActivityAndWait { intent ->
          intent.selectBenchmarkSample(route = sampleRoute, effect = "blur")
        }
        device.awaitReady()
      },
    ) {
      device.measure()
    }
  }
}

private const val STYLE_CHURN_MEASURE_MILLIS = 3_250L
