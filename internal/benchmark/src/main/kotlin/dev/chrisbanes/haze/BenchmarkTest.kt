// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.Point
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
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
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = DEFAULT_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.setBlurEnabled(true)
        device.navigateToImagesList()
      },
    ) {
      device.repeatedScrolls("lazy_column")
    }
  }

  @Test
  fun scaffold() {
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = DEFAULT_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.setBlurEnabled(true)
        device.navigateToScaffold()
      },
    ) {
      device.repeatedScrolls("lazy_grid")
    }
  }

  @Test
  fun scaffoldScaled() {
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = DEFAULT_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.setBlurEnabled(true)
        device.navigateToScaffoldScaled()
      },
    ) {
      device.repeatedScrolls("lazy_grid")
    }
  }

  @Test
  fun scaffoldProgressive() {
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = DEFAULT_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.setBlurEnabled(true)
        device.navigateToScaffoldWithProgressive()
      },
    ) {
      device.repeatedScrolls("lazy_grid")
    }
  }

  @Test
  fun scaffoldProgressiveScaled() {
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = DEFAULT_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.setBlurEnabled(true)
        device.navigateToScaffoldWithProgressiveScaled()
      },
    ) {
      device.repeatedScrolls("lazy_grid")
    }
  }

  @Test
  fun scaffoldMask() {
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = DEFAULT_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.setBlurEnabled(true)
        device.navigateToScaffoldWithMask()
      },
    ) {
      device.repeatedScrolls("lazy_grid")
    }
  }

  @Test
  fun creditCard() {
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = DEFAULT_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.setBlurEnabled(true)
        device.navigateToCreditCard()
      },
    ) {
      val creditCard = device.waitForObject(By.res("credit_card_2"))

      repeat(3) {
        // Drag it up
        creditCard.drag(Point(creditCard.visibleCenter.x, (device.displayHeight * 0.2f).toInt()))
        // Wait for it to settle back to the middle
        device.waitForIdle()
        // Drag it down
        creditCard.drag(Point(creditCard.visibleCenter.x, (device.displayHeight * 0.8f).toInt()))
        // Wait for it to settle back to the middle
        device.waitForIdle()
      }
    }
  }
}
