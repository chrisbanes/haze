// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.testutils

import android.graphics.Point
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
  return waitForObjectOrNull(selector, timeout)
    ?: error("Object with selector [$selector] not found")
}

internal fun UiDevice.waitForObjectOrNull(
  selector: BySelector,
  timeout: Duration = 5.seconds,
): UiObject2? {
  if (wait(Until.hasObject(selector), timeout)) {
    return findObject(selector)
  }
  return null
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

internal fun UiDevice.navigateToScaffoldWithProgressive() {
  waitForObject(By.res("Scaffold (with progressive blur)")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToCreditCard() {
  waitForObject(By.res("Credit Card")).click()
  waitForIdle()
}

internal fun UiDevice.scroll(tag: String, scrolls: Int = 5) {
  val grid = waitForObject(By.res(tag))
  // Set gesture margin to avoid triggering gesture navigation
  // with input events from automation.
  grid.setGestureMargin(displayWidth / 5)
  // Scroll down several times
  repeat(scrolls) {
    grid.drag(Point(grid.visibleCenter.x, grid.visibleBounds.top))
  }
}
