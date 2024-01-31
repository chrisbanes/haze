// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.testutils

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.SearchCondition
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal fun UiDevice.waitForObject(
  selector: BySelector,
  timeout: Duration = 5.seconds,
): UiObject2 {
  if (wait(Until.hasObject(selector), timeout)) {
    return findObject(selector)
  }

  error("Object with selector [$selector] not found")
}

internal fun <R> UiDevice.wait(condition: SearchCondition<R>, timeout: Duration): R {
  return wait(condition, timeout.inWholeMilliseconds)
}

internal fun UiDevice.navigateToImagesList() {
  waitForObject(By.res("Images List")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToScaffold() {
  waitForObject(By.res("Scaffold")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToCreditCard() {
  waitForObject(By.res("Credit Card")).click()
  waitForIdle()
}
