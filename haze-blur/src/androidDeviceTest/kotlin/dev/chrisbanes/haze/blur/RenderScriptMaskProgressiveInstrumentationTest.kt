// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class RenderScriptMaskProgressiveInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun maskAndProgressive_compose() {
    assumeTrue("RenderScript backend test requires API 30", Build.VERSION.SDK_INT == 30)
    val base = HazeBlurStyle {
      blurEnabled(true)
      blurRadius(16.dp)
      noiseFactor(0f)
      backgroundColor(Color.Transparent)
      colorEffects(listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.5f))))
      mask(null)
      progressive(null)
    }
    val mask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
    val progressive = HazeProgressive.horizontalGradient(
      easing = LinearEasing,
      startIntensity = 0f,
      endIntensity = 1f,
    )
    var style by mutableStateOf<HazeBlurStyle?>(null)
    val hazeState = HazeState()

    composeTestRule.setContent {
      Box(Modifier.size(200.dp).testTag(TAG)) {
        Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
          drawRect(Color.White)
          val stripe = 2.dp.toPx()
          var x = 0f
          while (x < size.width) {
            drawRect(Color.Black, topLeft = Offset(x, 0f), size = Size(stripe, size.height))
            x += stripe * 2
          }
        }
        val current = style
        Box(
          Modifier.fillMaxSize().then(
            if (current != null) {
              Modifier.hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = current,
                performanceMode = HazePerformanceMode.Performance,
              )
            } else {
              Modifier
            },
          ),
        )
      }
    }

    style = null
    val none = awaitStableCapture()
    style = base.then { progressive(progressive) }
    val prog = awaitStableCapture()
    style = base.then {
      mask(mask)
      progressive(progressive)
    }
    val both = awaitStableCapture()

    fun diff(a: PixelMap, b: PixelMap, x: ClosedFloatingPointRange<Float>, y: ClosedFloatingPointRange<Float>, name: String): Float =
      a.meanAbsoluteDifference(b, x, y).also { println("RenderScript $name=$it") }

    val top = 0.02f..0.12f
    val bottom = 0.90f..0.98f
    val mid = 0.47f..0.53f
    val left = 0.02f..0.12f
    val right = 0.88f..0.98f
    val all = 0.02f..0.98f

    assertThat(diff(both, none, all, bottom, "bottom combined-none"), "bottom combined vs none")
      .isLessThanOrEqualTo(TOLERANCE)
    assertThat(diff(prog, none, right, bottom, "bottom-right progressive-none"), "bottom-right progressive vs none")
      .isGreaterThan(SENSITIVITY)
    assertThat(diff(both, prog, left, top, "top-left combined-progressive"), "top-left combined vs progressive")
      .isLessThanOrEqualTo(TOLERANCE)
    assertThat(diff(both, prog, right, top, "top-right combined-progressive"), "top-right combined vs progressive")
      .isLessThanOrEqualTo(TOLERANCE)
    assertThat(diff(both, none, right, top, "top-right combined-none"), "top-right combined vs none")
      .isGreaterThan(SENSITIVITY)

    val span = diff(prog, none, right, mid, "mid-right progressive-none")
    val toNone = diff(both, none, right, mid, "mid-right combined-none")
    val toProg = diff(both, prog, right, mid, "mid-right combined-progressive")
    assertThat(toNone, "mid-right combined vs none").isGreaterThan(TOLERANCE)
    assertThat(toProg, "mid-right combined vs progressive").isGreaterThan(TOLERANCE)
    assertThat(toNone, "mid-right combined vs none within span").isLessThan(span)
    assertThat(toProg, "mid-right combined vs progressive within span").isLessThan(span)

    style = base.then { mask(mask) }
    val masked = awaitStableCapture()
    assertThat(diff(masked, both, left, top, "top-left mask-only-combined"), "top-left mask only vs combined")
      .isGreaterThan(SENSITIVITY)
  }

  /** RenderScript presents asynchronously, so wait until two consecutive captures agree. */
  private fun awaitStableCapture(): PixelMap {
    composeTestRule.waitForIdle()
    var previous = capture()
    val deadline = SystemClock.uptimeMillis() + STABLE_TIMEOUT_MS
    while (SystemClock.uptimeMillis() < deadline) {
      SystemClock.sleep(FRAME_MS)
      composeTestRule.waitForIdle()
      val current = capture()
      if (current.meanAbsoluteDifference(previous, 0f..1f, 0f..1f) == 0f) return current
      previous = current
    }
    error("RenderScript capture did not stabilize within $STABLE_TIMEOUT_MS ms")
  }

  private fun capture(): PixelMap = composeTestRule.onNodeWithTag(TAG).captureToImage().toPixelMap()

  private fun PixelMap.meanAbsoluteDifference(
    other: PixelMap,
    x: ClosedFloatingPointRange<Float>,
    y: ClosedFloatingPointRange<Float>,
  ): Float {
    val xs = (width * x.start).roundToInt() until (width * x.endInclusive).roundToInt()
    val ys = (height * y.start).roundToInt() until (height * y.endInclusive).roundToInt()
    var total = 0f
    for (py in ys) {
      for (px in xs) {
        val a = this[px, py]
        val b = other[px, py]
        total += abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) + abs(a.alpha - b.alpha)
      }
    }
    return total / (xs.count() * ys.count() * 4)
  }

  private companion object {
    const val TAG = "render_script_mask_progressive"
    const val TOLERANCE = 0.03f
    const val SENSITIVITY = 0.05f
    const val STABLE_TIMEOUT_MS = 2_000L
    const val FRAME_MS = 50L
  }
}
