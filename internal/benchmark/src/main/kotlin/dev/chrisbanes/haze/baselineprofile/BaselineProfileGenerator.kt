// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.baselineprofile

import android.graphics.Point
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import dev.chrisbanes.haze.testutils.navigateToImagesList
import dev.chrisbanes.haze.testutils.waitForObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
  @get:Rule
  val baselineProfileRule = BaselineProfileRule()

  @Test
  fun generate() = baselineProfileRule.collect(
    packageName = "dev.chrisbanes.haze.sample.android",
  ) {
    pressHome()
    startActivityAndWait()

    device.navigateToImagesList()

    // Scroll down several times
    repeat(5) {
      val column = device.waitForObject(By.res("lazy_column"))

      // Set gesture margin to avoid triggering gesture navigation
      // with input events from automation.
      column.setGestureMargin(device.displayWidth / 5)

      column.drag(Point(column.visibleCenter.x, column.visibleBounds.top))
    }
  }
}
