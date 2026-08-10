// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.Point
import android.os.SystemClock
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
  timeout: Duration = 15.seconds,
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

internal fun UiDevice.navigateToImagesList() {
  findSampleListItem(By.res("Images List")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToScaffold() {
  findSampleListItem(By.res("Scaffold")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToScaffoldWithEquivalentStyleChurn() {
  findSampleListItem(By.res("Blur — Equivalent Style Churn")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToCreditCard() {
  findSampleListItem(By.res("Credit Card")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToBlurProfiling(scenarioId: String) {
  findSampleListItem(By.res("Blur — Profiling")).click()
  waitForObject(By.res("blur_profiling_picker"))
    .apply { setGestureMarginPercentage(0.1f) }
    .scrollUntil(
      Direction.DOWN,
      Until.findObject(By.res("blur_profiling_select_$scenarioId")),
    )
    .click()
  waitForProfilingObject(
    effectName = "Blur",
    scenarioId = scenarioId,
    expectedPhase = "selected",
    selector = By.res("blur_profiling_selected_$scenarioId"),
  )
  waitForProfilingObject(
    effectName = "Blur",
    scenarioId = scenarioId,
    expectedPhase = "ready",
    selector = By.res("blur_profiling_start"),
  )
}

internal fun UiDevice.runBlurProfilingScenario(scenarioId: String) {
  waitForProfilingObject(
    effectName = "Blur",
    scenarioId = scenarioId,
    expectedPhase = "ready",
    selector = By.res("blur_profiling_start"),
  ).click()
  SystemClock.sleep(BLUR_PROFILING_MEASURE_MILLIS)
}

internal fun UiDevice.navigateToGlassProduct() {
  findSampleListItem(By.res("Glass — Product")).click()
  waitForObject(By.res("glass_product_page_0"))
}

internal fun UiDevice.advanceGlassProduct() {
  waitForObject(By.desc("Next artwork")).click()
  waitForObject(By.res("glass_product_page_1"))
}

internal fun UiDevice.navigateToGlassPlayground() {
  findSampleListItem(By.res("Glass — Playground")).click()
  waitForObject(By.res("glass_playground_loop_1"), timeout = 20.seconds)
}

internal fun UiDevice.measureFullGlassPlaygroundLoop() {
  waitForObject(By.desc("Reset demo")).click()
  waitForObject(By.res("glass_playground_loop_0"))
  waitForObject(By.res("glass_playground_loop_1"), timeout = 20.seconds)
}

internal fun UiDevice.navigateToGlassProfiling(scenarioId: String) {
  findSampleListItem(By.res("Glass — Profiling")).click()
  waitForObject(By.res("glass_profiling_picker"))
    .apply { setGestureMarginPercentage(0.1f) }
    .scrollUntil(
      Direction.DOWN,
      Until.findObject(By.res("glass_profiling_select_$scenarioId")),
    )
    .click()
  waitForProfilingObject(
    effectName = "Glass",
    scenarioId = scenarioId,
    expectedPhase = "selected",
    selector = By.res("glass_profiling_selected_$scenarioId"),
  )
  waitForProfilingObject(
    effectName = "Glass",
    scenarioId = scenarioId,
    expectedPhase = "ready",
    selector = By.res("glass_profiling_start"),
  )
}

internal fun UiDevice.runGlassProfilingScenario(scenarioId: String) {
  waitForProfilingObject(
    effectName = "Glass",
    scenarioId = scenarioId,
    expectedPhase = "ready",
    selector = By.res("glass_profiling_start"),
  ).click()
  SystemClock.sleep(GLASS_PROFILING_MEASURE_MILLIS)
}

private fun UiDevice.waitForProfilingObject(
  effectName: String,
  scenarioId: String,
  expectedPhase: String,
  selector: BySelector,
  timeout: Duration = 15.seconds,
): UiObject2 = waitForObjectOrNull(selector, timeout)
  ?: error(
    "$effectName profiling timeout: scenario=$scenarioId, phase=$expectedPhase, " +
      "selector=$selector, timeout=$timeout, visibleNodes=" +
      findObjects(By.pkg(GLASS_TARGET_PACKAGE))
        .map { node ->
          "${node.resourceName}:${node.text}:${node.contentDescription}"
        },
  )

// Scenarios run for 3 seconds; the buffer absorbs completion scheduling jitter.
private const val GLASS_PROFILING_MEASURE_MILLIS = 3_250L
private const val BLUR_PROFILING_MEASURE_MILLIS = 3_250L

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
