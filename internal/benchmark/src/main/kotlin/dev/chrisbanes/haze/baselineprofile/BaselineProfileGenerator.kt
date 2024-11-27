// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import dev.chrisbanes.haze.testutils.navigateToImagesList
import dev.chrisbanes.haze.testutils.navigateToScaffold
import dev.chrisbanes.haze.testutils.navigateToScaffoldWithProgressive
import dev.chrisbanes.haze.testutils.repeatedScrolls
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

    // Scroll down several times
    device.navigateToImagesList()
    device.repeatedScrolls("lazy_column")

    device.findObject(By.res("back")).click()
    device.waitForIdle()

    device.navigateToScaffoldWithProgressive()
    device.repeatedScrolls("lazy_grid")

    device.findObject(By.res("back")).click()
    device.waitForIdle()

    device.navigateToScaffold()
    device.repeatedScrolls("lazy_grid")
  }
}
