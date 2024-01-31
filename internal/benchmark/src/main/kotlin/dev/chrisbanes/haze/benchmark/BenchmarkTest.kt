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
      val column = device.waitForObject(By.res("lazy_column"))

      // Set gesture margin to avoid triggering gesture navigation
      // with input events from automation.
      column.setGestureMargin(device.displayWidth / 5)

      // Scroll down several times
      repeat(5) {
        column.drag(Point(column.visibleCenter.x, column.visibleBounds.top))
      }
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
      val grid = device.waitForObject(By.res("lazy_grid"))

      // Set gesture margin to avoid triggering gesture navigation
      // with input events from automation.
      grid.setGestureMargin(device.displayWidth / 5)

      // Scroll down several times
      repeat(5) {
        grid.drag(Point(grid.visibleCenter.x, grid.visibleBounds.top))
      }
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
