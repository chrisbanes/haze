// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark

import android.graphics.Point
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import dev.chrisbanes.haze.testutils.navigateToCreditCard
import dev.chrisbanes.haze.testutils.navigateToImagesList
import dev.chrisbanes.haze.testutils.navigateToScaffold
import dev.chrisbanes.haze.testutils.navigateToScaffoldWithProgressive
import dev.chrisbanes.haze.testutils.scroll
import dev.chrisbanes.haze.testutils.waitForObject
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
    benchmarkRule.measureRepeated(
      packageName = APP_PACKAGE,
      metrics = listOf(FrameTimingMetric()),
      startupMode = StartupMode.WARM,
      iterations = DEFAULT_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.navigateToImagesList()
      },
    ) {
      device.scroll("lazy_column")
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
        device.navigateToScaffold()
      },
    ) {
      device.scroll("lazy_grid")
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
        device.navigateToScaffoldWithProgressive()
      },
    ) {
      device.scroll("lazy_grid")
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
        device.navigateToCreditCard()
      },
    ) {
      val creditCard = device.waitForObject(By.res("credit_card"))

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
