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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
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
import assertk.assertions.isCloseTo
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Checks the RenderScript tint model against the analytic expectation RenderEffect shares: an
 * opaque tint covers the blurred content, so each pixel depends only on the tint, the brush
 * geometry and the mask, which is applied once.
 */
class RenderScriptTintInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun unmaskedGradientTint_matchesNodeGeometry() {
    assumeTrue("RenderScript backend test requires API 30", Build.VERSION.SDK_INT == 30)
    val style = mutableStateOf(
      tintStyle(HazeColorEffect.tint(Brush.verticalGradient(listOf(Color.Red, Color.Blue)))),
    )
    setContent(style)

    awaitStableCapture().assertRows(listOf(0.05f, 0.5f, 0.8f)) { color, t ->
      assertThat(color.red, "red t=$t").isCloseTo(1f - t, TOLERANCE)
      assertThat(color.blue, "blue t=$t").isCloseTo(t, TOLERANCE)
    }
  }

  @Test
  fun maskedTint_appliesMaskOnce() {
    assumeTrue("RenderScript backend test requires API 30", Build.VERSION.SDK_INT == 30)
    val tint = HazeColorEffect.tint(Color.Red)
    val style = mutableStateOf(
      tintStyle(tint, mask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))),
    )
    setContent(style)

    // The opaque red tint fades by the mask once, so the white source shows through by 1 - m.
    awaitStableCapture().assertRows(listOf(0.25f, 0.5f, 0.75f)) { color, t ->
      assertThat(color.green, "mask green t=$t").isCloseTo(t, TOLERANCE)
    }

    style.value = tintStyle(
      tint,
      progressive = HazeProgressive.verticalGradient(
        easing = LinearEasing,
        startIntensity = 1f,
        endIntensity = 0f,
      ),
    )
    awaitStableCapture().assertRows(listOf(0.25f, 0.5f, 0.75f)) { color, t ->
      assertThat(color.green, "progressive green t=$t").isCloseTo(t, TOLERANCE)
    }
  }

  private fun tintStyle(
    effect: HazeColorEffect,
    mask: Brush? = null,
    progressive: HazeProgressive? = null,
  ) = HazeBlurStyle {
    blurEnabled(true)
    blurRadius(20.dp)
    noiseFactor(0f)
    backgroundColor(Color.Transparent)
    colorEffects(listOf(effect))
    mask(mask)
    progressive(progressive)
    fallbackColorEffect(null)
  }

  /**
   * A white source with the effect inset by 100dp, so the expanded layer is not clipped and the
   * RenderScript layer offset is non-zero.
   */
  private fun setContent(style: State<HazeBlurStyle>) {
    val hazeState = HazeState()
    composeTestRule.setContent {
      Box(Modifier.size(300.dp)) {
        Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
          drawRect(Color.White)
        }
        Box(
          Modifier
            .offset(100.dp, 100.dp)
            .size(100.dp)
            .testTag(TAG)
            .hazeBlur(
              input = HazeInput.Sources(hazeState),
              style = style.value,
              performanceMode = HazePerformanceMode.Performance,
            ),
        )
      }
    }
  }

  /** Samples the centre column at each [fractions] of the height, with `t` the pixel centre's fraction. */
  private fun PixelMap.assertRows(fractions: List<Float>, check: (Color, t: Float) -> Unit) {
    for (fraction in fractions) {
      val y = (height * fraction).toInt()
      check(this[width / 2, y], (y + 0.5f) / height)
    }
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
      if (current.isIdenticalTo(previous)) return current
      previous = current
    }
    error("RenderScript capture did not stabilize within $STABLE_TIMEOUT_MS ms")
  }

  private fun capture(): PixelMap = composeTestRule.onNodeWithTag(TAG).captureToImage().toPixelMap()

  private fun PixelMap.isIdenticalTo(other: PixelMap): Boolean =
    buffer.contentEquals(other.buffer)

  private companion object {
    const val TAG = "render_script_tint"
    const val TOLERANCE = 0.05f
    const val STABLE_TIMEOUT_MS = 2_000L
    const val FRAME_MS = 50L
  }
}
