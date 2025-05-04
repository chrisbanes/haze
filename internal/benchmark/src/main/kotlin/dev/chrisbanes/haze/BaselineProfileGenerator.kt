// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    // Force enable blurring
    device.setBlurEnabled(true)

    // Scroll down several times
    device.navigateToImagesList()
    device.repeatedScrolls("lazy_column")

    device.pressBack()
    device.waitForIdle()

    device.navigateToScaffold()
    device.repeatedScrolls("lazy_grid")

    device.pressBack()
    device.waitForIdle()

    device.navigateToCreditCard()
    device.repeatedDrags("credit_card_2")
  }
}
