// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.Point
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.SearchCondition
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal fun UiDevice.waitForObject(
  selector: BySelector,
  timeout: Duration = 5.seconds,
): UiObject2 = waitForObjectOrNull(selector, timeout)
  ?: error("Object with selector [$selector] not found")

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

internal fun UiDevice.setBlurEnabled(enabled: Boolean) {
  val checkbox = findObject(By.res("blur_enabled"))
  if (checkbox.isChecked != enabled) {
    checkbox.click()
    waitForIdle()
  }
}

internal fun UiDevice.navigateToImagesList() {
  findSampleListItem(By.res("Images List")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToScaffold() {
  findSampleListItem(By.res("Scaffold")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToScaffoldScaled() {
  findSampleListItem(By.res("Scaffold (input scaled)")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToScaffoldWithProgressive() {
  findSampleListItem(By.res("Scaffold (progressive blur)")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToScaffoldWithProgressiveScaled() {
  findSampleListItem(By.res("Scaffold (progressive blur, input scaled)")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToScaffoldWithMask() {
  findSampleListItem(By.res("Scaffold (masked)")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToCreditCard() {
  findSampleListItem(By.res("Credit Card")).click()
  waitForIdle()
}

internal fun UiDevice.findSampleListItem(selector: BySelector): UiObject2 {
  return waitForObject(By.res("sample_list"))
    .apply { setGestureMarginPercentage(0.1f) }
    .scrollUntil(Direction.DOWN, Until.findObject(selector))
}

internal fun UiDevice.repeatedScrolls(
  tag: String,
  startDirection: Direction = Direction.DOWN,
  repetitions: Int = 4,
) {
  val node = waitForObject(By.res(tag))
  // Set gesture margins to avoid triggering gesture navigation
  // with input events from automation.
  val horiz = (displayWidth / 6f).roundToInt()
  val vert = (displayHeight / 8f).roundToInt()
  node.setGestureMargins(horiz, vert, horiz, vert)
  // Scroll up + down several times
  repeat(repetitions) { index ->
    val direction = when {
      index % 2 == 0 -> startDirection
      else -> startDirection.opposite()
    }
    node.scroll(direction, 0.8f)
  }
}

internal fun UiDevice.repeatedDrags(
  tag: String,
  repetitions: Int = 4,
) {
  val creditCard = waitForObject(By.res(tag))

  repeat(repetitions) {
    // Drag it up
    creditCard.drag(Point(creditCard.visibleCenter.x, (displayHeight * 0.2f).toInt()))
    // Wait for it to settle back to the middle
    waitForIdle()
    // Drag it down
    creditCard.drag(Point(creditCard.visibleCenter.x, (displayHeight * 0.8f).toInt()))
    // Wait for it to settle back to the middle
    waitForIdle()
  }
}

private fun Direction.opposite(): Direction = when (this) {
  Direction.LEFT -> Direction.RIGHT
  Direction.RIGHT -> Direction.LEFT
  Direction.DOWN -> Direction.UP
  else -> Direction.DOWN
}
