// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * The capture already holds the semi-transparent background, so padding a capture to a multiple of
 * 4 fills only the added strips with it (#1912). Filling the whole padded size underneath darkened
 * the interior, so a 101 px node presented darker than a 100 px one.
 */
class RenderScriptBackgroundPaddingInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val probe = Probe()
  private var sizePx by mutableStateOf(100)
  private val delegate get() = probe.effect.delegate as RenderScriptBlurVisualEffectDelegate

  @Test
  fun paddedCapture_centreAlphaMatchesUnpaddedCapture() {
    assumeTrue("RenderScript backend test requires API 30", Build.VERSION.SDK_INT == 30)
    val hazeState = HazeState()
    composeTestRule.setContent {
      val size = with(LocalDensity.current) { sizePx.toDp() }
      // A transparent source, so the capture is the background colour alone.
      Box(Modifier.size(100.dp).hazeSource(hazeState))
      Box(
        Modifier
          .testTag(TAG)
          .width(size)
          .height(size)
          .background(Color.White)
          .hazeEffect(
            factory = probe,
            input = HazeInput.Sources(hazeState),
            // Keeps the capture node-sized, so the pixel size decides whether padding applies.
            expandLayerBounds = false,
            style = BlurConfiguration(
              HazeBlurStyle {
                blurEnabled(true)
                blurRadius(4.dp)
                noiseFactor(0f)
                backgroundColor(Color.Black.copy(alpha = 0.5f))
                colorEffects(emptyList())
                fallbackColorEffect(null)
              },
              HazePerformanceMode.Quality,
            ),
          ),
      )
    }

    val unpadded = awaitStableOutput(IntSize(100, 100))
    assertThat(delegate.paddedLayer.layer).isNull()
    val capture = checkNotNull(delegate.captureLayer.layer).pixels()
    assertThat(capture[50, 50].alpha, "unpadded capture alpha").isCloseTo(0.5f, RAW_TOLERANCE)
    // The black effect at alpha 0.5 over the white node background.
    val unpaddedRed = unpadded[unpadded.width / 2, unpadded.height / 2].red
    assertThat(unpaddedRed, "unpadded output red").isCloseTo(0.5f, OUTPUT_TOLERANCE)

    composeTestRule.runOnIdle { sizePx = 101 }
    val padded = awaitStableOutput(IntSize(101, 101))
    val paddedLayer = checkNotNull(delegate.paddedLayer.layer)
    assertThat(paddedLayer.size).isEqualTo(IntSize(104, 104))
    val paddedPixels = paddedLayer.pixels()
    for ((x, y) in listOf(50 to 50, 102 to 50, 50 to 102)) {
      assertThat(paddedPixels[x, y].alpha, "padded alpha at ($x, $y)").isCloseTo(0.5f, RAW_TOLERANCE)
    }
    assertThat(padded[padded.width / 2, padded.height / 2].red, "padded output red")
      .isCloseTo(unpaddedRed, OUTPUT_TOLERANCE)
  }

  private fun GraphicsLayer.pixels(): PixelMap = runBlocking { toImageBitmap() }.toPixelMap()

  /** Waits for an output from a [captureSize] capture, then until two consecutive frames agree. */
  private fun awaitStableOutput(captureSize: IntSize): PixelMap {
    // Read on the main thread, so the check never lands between recording a capture and starting its job.
    composeTestRule.waitUntil(5_000) {
      var ready = false
      composeTestRule.runOnUiThread {
        ready = probe.effect.delegate is RenderScriptBlurVisualEffectDelegate &&
          delegate.captureLayer.layer?.size == captureSize &&
          delegate.canDrawRetainedOutput() &&
          delegate.currentJob?.isActive != true
      }
      ready
    }
    composeTestRule.waitForIdle()
    var previous = capture()
    val deadline = SystemClock.uptimeMillis() + 2_000L
    while (SystemClock.uptimeMillis() < deadline) {
      SystemClock.sleep(50L)
      composeTestRule.waitForIdle()
      val current = capture()
      if (current.buffer.contentEquals(previous.buffer)) return current
      previous = current
    }
    error("RenderScript output did not stabilize")
  }

  private fun capture(): PixelMap = composeTestRule.onNodeWithTag(TAG).captureToImage().toPixelMap()

  private class Probe : HazeEffectFactory<BlurConfiguration> {
    lateinit var effect: BlurVisualEffect
      private set

    override fun createRenderer(): HazeEffectRenderer<BlurConfiguration> =
      BlurVisualEffect().also { effect = it }
  }

  private companion object {
    const val TAG = "render_script_background_padding"
    const val RAW_TOLERANCE = 8f / 255f
    const val OUTPUT_TOLERANCE = 0.05f
  }
}
