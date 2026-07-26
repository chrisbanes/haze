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
private const val HYPOTHESIS_ITERATIONS = 5
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
  fun scaffold() {
    measureScaffold { navigateToScaffold() }
  }

  @Test
  fun scaffoldUnscaled() {
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = DEFAULT_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.setBlurEnabled(true)
        device.navigateToScaffoldUnscaled()
      },
    ) {
      device.repeatedScrolls("lazy_grid")
    }
  }

  @Test
  fun scaffoldBalanced() {
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = DEFAULT_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.setBlurEnabled(true)
        device.navigateToScaffoldBalanced()
      },
    ) {
      device.repeatedScrolls("lazy_grid")
    }
  }

  @Test
  fun scaffoldBlurDisabled() {
    measureScaffold(
      iterations = HYPOTHESIS_ITERATIONS,
      blurEnabled = false,
      navigate = { navigateToScaffold() },
    )
  }

  @Test
  fun scaffoldProgressive() {
    measureScaffold { navigateToScaffoldWithProgressive() }
  }

  @Test
  fun scaffoldProgressiveUnscaled() {
    measureScaffold { navigateToScaffoldWithProgressiveUnscaled() }
  }

  @Test
  fun scaffoldMask() {
    measureScaffold { navigateToScaffoldWithMask() }
  }

  @Test
  fun scaffoldMaskUnscaled() {
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = DEFAULT_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.setBlurEnabled(true)
        device.navigateToScaffoldWithMaskUnscaled()
      },
    ) {
      device.repeatedScrolls("lazy_grid")
    }
  }

  @Test
  fun creditCard() {
    measureSample(
      navigate = { navigateToCreditCard() },
      measure = { repeatedDrags("credit_card_2") },
    )
  }

  private fun measureScaffold(
    iterations: Int = DEFAULT_ITERATIONS,
    blurEnabled: Boolean = true,
    navigate: UiDevice.() -> Unit,
  ) {
    measureSample(
      iterations = iterations,
      blurEnabled = blurEnabled,
      navigate = navigate,
      measure = { repeatedScrolls("lazy_grid") },
    )
  }

  private fun measureSample(
    iterations: Int = DEFAULT_ITERATIONS,
    blurEnabled: Boolean = true,
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
        device.setBlurEnabled(blurEnabled)
        device.navigate()
      },
    ) {
      device.measure()
    }
  }
}
