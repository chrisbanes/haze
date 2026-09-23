// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Point
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.SearchCondition
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal const val FORCE_BLUR_EXTRA = "dev.chrisbanes.haze.sample.android.FORCE_BLUR"
private const val BENCHMARK_SAMPLE_ROUTE_EXTRA =
  "dev.chrisbanes.haze.sample.android.BENCHMARK_SAMPLE_ROUTE"
private const val BENCHMARK_SAMPLE_EFFECT_EXTRA =
  "dev.chrisbanes.haze.sample.android.BENCHMARK_SAMPLE_EFFECT"
private const val BENCHMARK_SCENARIO_EXTRA =
  "dev.chrisbanes.haze.sample.android.BENCHMARK_SCENARIO"

internal fun Intent.selectBenchmarkSample(
  route: String,
  effect: String,
  scenarioId: String? = null,
) {
  putExtra(BENCHMARK_SAMPLE_ROUTE_EXTRA, route)
  putExtra(BENCHMARK_SAMPLE_EFFECT_EXTRA, effect)
  scenarioId?.let { putExtra(BENCHMARK_SCENARIO_EXTRA, it) }
}

internal inline fun <T> withoutUiAutomatorIdleWait(
  enabled: Boolean = true,
  block: () -> T,
): T {
  if (!enabled) return block()
  val configurator = Configurator.getInstance()
  val previousTimeout = configurator.waitForIdleTimeout
  configurator.setWaitForIdleTimeout(0)
  return try {
    block()
  } finally {
    configurator.setWaitForIdleTimeout(previousTimeout)
  }
}

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

internal fun UiDevice.waitForImagesList() = waitForObject(By.res("lazy_column"))

internal fun UiDevice.waitForScaffoldWithEquivalentStyleChurn() = waitForObject(By.res("lazy_grid"))

internal fun UiDevice.waitForCreditCard() = waitForObject(By.res("credit_card_2"))

internal fun UiDevice.navigateToImagesList() {
  findBlurSampleListItem(By.res("Images List")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToScaffold() {
  findBlurSampleListItem(By.res("Scaffold")).click()
  waitForIdle()
}

internal fun UiDevice.navigateToCreditCard() {
  findBlurSampleListItem(By.res("Credit Card")).click()
  waitForIdle()
}

internal fun UiDevice.waitForBlurProfilingScenario(scenarioId: String) {
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

internal fun UiDevice.waitForGlassProduct() = waitForObject(By.res("glass_product_page_0"))

internal fun UiDevice.navigateToGlassProduct() {
  findGlassSampleListItem(By.res("Glass — Product")).click()
  waitForGlassProduct()
}

internal fun UiDevice.advanceGlassProduct() {
  waitForObject(By.desc("Next artwork")).click()
  waitForObject(By.res("glass_product_page_1"))
}

internal fun UiDevice.waitForGlassPlayground() =
  waitForObject(By.res("glass_playground_loop_1"), timeout = 20.seconds)

internal fun UiDevice.navigateToGlassPlayground() {
  findGlassSampleListItem(By.res("Glass — Playground")).click()
  waitForGlassPlayground()
}

internal fun UiDevice.measureFullGlassPlaygroundLoop() {
  waitForObject(By.desc("Reset demo")).click()
  waitForObject(By.res("glass_playground_loop_0"))
  waitForObject(By.res("glass_playground_loop_1"), timeout = 20.seconds)
}

internal fun UiDevice.waitForGlassProfilingScenario(scenarioId: String) {
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

/** Captures two known points in the same animation and checks pixels inside the Glass surface. */
internal data class GlassDiagnosticPixels(val changed: Int, val sampled: Int)

internal fun UiDevice.runGlassProfilingScenarioDiagnostic(scenarioId: String): GlassDiagnosticPixels {
  val surface = waitForObject(By.res("glass_profiling_surface_0"))
  val bounds = surface.visibleBounds
  val insetX = bounds.width() / 6
  val insetY = bounds.height() / 6
  val left = bounds.left + insetX
  val top = bounds.top + insetY
  val right = bounds.right - insetX
  val bottom = bounds.bottom - insetY
  require(right > left && bottom > top)

  waitForObject(By.res("glass_profiling_start")).click()
  val start = SystemClock.uptimeMillis()
  SystemClock.sleep(600)
  val early = checkNotNull(takeScreenshot()) { "Early Glass screenshot unavailable" }
  val earlyMillis = SystemClock.uptimeMillis() - start
  SystemClock.sleep((2_200 - (SystemClock.uptimeMillis() - start)).coerceAtLeast(0))
  val late = checkNotNull(takeScreenshot()) { "Late Glass screenshot unavailable" }
  val lateMillis = SystemClock.uptimeMillis() - start

  val prefix = "cb10-$scenarioId-$start"
  val savedEarly = saveGlassDiagnosticScreenshot(early, "$prefix-early.png")
  val savedLate = saveGlassDiagnosticScreenshot(late, "$prefix-late.png")

  var sampled = 0
  var changed = 0
  for (y in top until bottom step 4) {
    for (x in left until right step 4) {
      val before = early.getPixel(x, y)
      val after = late.getPixel(x, y)
      sampled++
      if (
        kotlin.math.abs(android.graphics.Color.red(before) - android.graphics.Color.red(after)) > 16 ||
        kotlin.math.abs(android.graphics.Color.green(before) - android.graphics.Color.green(after)) > 16 ||
        kotlin.math.abs(android.graphics.Color.blue(before) - android.graphics.Color.blue(after)) > 16
      ) {
        changed++
      }
    }
  }
  Log.i(
    "CB10BenchmarkDiagnostic",
    "scenario=$scenarioId earlyMs=$earlyMillis lateMs=$lateMillis " +
      "changedPixels=$changed sampledPixels=$sampled bounds=$bounds " +
      "early=$savedEarly late=$savedLate",
  )
  SystemClock.sleep(
    (GLASS_PROFILING_MEASURE_MILLIS - (SystemClock.uptimeMillis() - start))
      .coerceAtLeast(0),
  )
  return GlassDiagnosticPixels(changed, sampled)
}

private fun saveGlassDiagnosticScreenshot(bitmap: Bitmap, name: String): String {
  val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
  val values = ContentValues().apply {
    put(MediaStore.Images.Media.DISPLAY_NAME, name)
    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CB10")
  }
  val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
  checkNotNull(resolver.openOutputStream(uri)).use { output ->
    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
  }
  return uri.toString()
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

private fun UiDevice.findBlurSampleListItem(selector: BySelector): UiObject2 =
  findSampleListItem(effect = "blur", selector = selector)

private fun UiDevice.findGlassSampleListItem(selector: BySelector): UiObject2 =
  findSampleListItem(effect = "glass", selector = selector)

private fun UiDevice.findSampleListItem(effect: String, selector: BySelector): UiObject2 {
  waitForObject(By.res("sample_effect_$effect")).click()
  return waitForObject(By.res("sample_list"))
    .apply { setGestureMarginPercentage(0.1f) }
    .scrollUntil(Direction.DOWN, Until.findObject(selector))
}

internal fun UiDevice.repeatedScrolls(
  tag: String,
  startDirection: Direction = Direction.DOWN,
  repetitions: Int = 4,
) {
  // Scroll up + down several times
  repeat(repetitions) { index ->
    val node = waitForObject(By.res(tag))
    // Set gesture margins to avoid triggering gesture navigation
    // with input events from automation.
    val horiz = (displayWidth / 6f).roundToInt()
    val vert = (displayHeight / 8f).roundToInt()
    node.setGestureMargins(horiz, vert, horiz, vert)
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
