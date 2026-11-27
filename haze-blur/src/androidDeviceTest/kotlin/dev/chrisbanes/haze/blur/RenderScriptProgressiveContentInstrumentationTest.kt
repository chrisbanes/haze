// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class RenderScriptProgressiveContentInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun progressiveContentInput_keepsItsClearEnd() {
    assumeTrue("RenderScript backend test requires API 30", Build.VERSION.SDK_INT == 30)
    val progressive = HazeBlurStyle {
      blurEnabled(true)
      blurRadius(16.dp)
      noiseFactor(0f)
      backgroundColor(Color.Transparent)
      colorEffects(emptyList())
      mask(null)
      progressive(
        HazeProgressive.horizontalGradient(easing = LinearEasing, startIntensity = 0f, endIntensity = 1f),
      )
    }
    var style by mutableStateOf<HazeBlurStyle?>(null)

    composeTestRule.setContent {
      Box(Modifier.size(200.dp).testTag(TAG).background(Color.White)) {
        val current = style
        Canvas(
          Modifier.fillMaxSize().then(
            if (current != null) {
              Modifier.hazeBlur(
                input = HazeInput.Content,
                style = current,
                performanceMode = HazePerformanceMode.Performance,
              )
            } else {
              Modifier
            },
          ),
        ) {
          val stripe = 2.dp.toPx()
          var x = 0f
          while (x < size.width) {
            drawRect(Color.Black, topLeft = Offset(x, 0f), size = Size(stripe, size.height))
            x += stripe * 2
          }
        }
      }
    }

    style = null
    val plain = awaitStableCapture()
    style = progressive
    val prog = awaitStableCapture()

    val all = 0.02f..0.98f
    // Below 6% intensity the residual blur stays within the tolerance; erased content shows the
    // white root instead, far above it.
    val clear = 0.02f..0.06f
    val blurred = 0.88f..0.98f

    val clearDiff = prog.meanAbsoluteDifference(plain, clear, all).also { println("RenderScript clear=$it") }
    assertThat(clearDiff, "clear end progressive vs plain").isLessThanOrEqualTo(TOLERANCE)
    val blurredDiff = prog.meanAbsoluteDifference(plain, blurred, all).also { println("RenderScript blurred=$it") }
    assertThat(blurredDiff, "blurred end progressive vs plain").isGreaterThan(SENSITIVITY)
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
    const val TAG = "render_script_progressive_content"
    const val TOLERANCE = 0.03f
    const val SENSITIVITY = 0.05f
    const val STABLE_TIMEOUT_MS = 2_000L
    const val FRAME_MS = 50L
  }
}
