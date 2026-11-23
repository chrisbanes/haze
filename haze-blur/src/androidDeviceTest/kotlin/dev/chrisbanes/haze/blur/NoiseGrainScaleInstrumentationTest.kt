// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.graphics.Matrix
import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.drawScaledContent
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.scaledContentScale
import kotlin.math.abs
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Confirms on HWUI and RenderScript that noise grain does not grow with the capture scale (#1832). */
class NoiseGrainScaleInstrumentationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun renderEffect_reducedCapture_grainIsOneCapturePixel() {
    assumeTrue("RenderEffect requires API 31", Build.VERSION.SDK_INT >= 31)
    var mode by mutableStateOf(HazePerformanceMode.Quality)
    var scale by mutableStateOf(1f)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Row {
          BlurBox(style(blurRadius = 8), mode, BLUR)
          Reference(scale, legacy = false, REFERENCE)
          Reference(scale, legacy = true, LEGACY)
        }
      }
    }
    composeTestRule.waitForIdle()
    val fullBlur = grain(capture(BLUR))
    val fullReference = grain(capture(REFERENCE))
    // Performance resolves to exactly half resolution.
    mode = HazePerformanceMode.Performance
    scale = 0.5f
    composeTestRule.waitForIdle()
    val reference = grain(capture(REFERENCE))

    assertThat(fullBlur, "Quality grain").isBetween(fullReference * 0.8f, fullReference * 1.2f)
    assertThat(grain(capture(BLUR)), "Performance grain").isBetween(reference * 0.8f, reference * 1.2f)
    assertThat(grain(capture(LEGACY)), "old-matrix control grain").isGreaterThanOrEqualTo(reference * 1.5f)
  }

  @Test
  fun renderScript_reducedAndRadiusLimitedCapture_matchesFullResolutionGrain() {
    assumeTrue("RenderScript backend test requires API 30", Build.VERSION.SDK_INT == 30)
    var mode by mutableStateOf(HazePerformanceMode.Quality)
    var style by mutableStateOf(style(blurRadius = 4))
    composeTestRule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        BlurBox(style, mode, BLUR)
      }
    }
    val full = awaitStableCapture()

    mode = HazePerformanceMode.Performance
    assertThat(maxInteriorDifference(full, awaitStableCapture()), "Performance").isLessThan(0.03f)

    // 60 px at half resolution exceeds RenderScript's 25 px radius, so the capture scale drops again.
    style = style(blurRadius = 60)
    assertThat(maxInteriorDifference(full, awaitStableCapture()), "radius-limited").isLessThan(0.03f)
  }

  private fun style(blurRadius: Int) = HazeBlurStyle {
    blurEnabled(true)
    blurRadius(blurRadius.dp)
    noiseFactor(1f)
    // The source is flat grey, so a grey background keeps RenderScript's alignment padding flat too.
    backgroundColor(GREY)
    colorEffects(emptyList())
    fallbackColorEffect(null)
  }

  /** A flat grey source under the effect. Without layer expansion the noise origin is fixed. */
  @Composable
  private fun BlurBox(style: HazeBlurStyle, mode: HazePerformanceMode, tag: String) {
    val state = rememberHazeState()
    Box(Modifier.hazeSource(state).background(GREY).padding(PADDING.dp)) {
      Box(
        Modifier.size(SIZE.dp).testTag(tag)
          .hazeBlur(HazeInput.Sources(state), style, mode, expandLayerBounds = false),
      )
    }
  }

  /** Noise drawn into a flat grey capture at [scale], then presented as the RenderEffect path does. */
  @Composable
  private fun Reference(scale: Float, legacy: Boolean, tag: String) {
    val context = LocalContext.current
    val layer = rememberGraphicsLayer()
    Box(Modifier.padding(PADDING.dp)) {
      Canvas(Modifier.size(SIZE.dp).testTag(tag)) {
        val noise = context.createNoiseShader().apply {
          if (legacy) setLocalMatrix(Matrix().apply { setScale(1f / scale, 1f / scale) })
        }
        layer.record((size * scale).roundToIntSize()) {
          drawRect(GREY)
          drawRect(ShaderBrush(noise), blendMode = BlendMode.Softlight)
        }
        drawScaledContent(Offset.Zero, scaledContentScale(size, scale)) { drawLayer(layer) }
      }
    }
  }

  private fun capture(tag: String): PixelMap = composeTestRule.onNodeWithTag(tag).captureToImage().toPixelMap()

  /** RenderScript presents asynchronously, so wait until two consecutive captures agree. */
  private fun awaitStableCapture(): PixelMap {
    composeTestRule.waitForIdle()
    var previous = capture(BLUR)
    val deadline = SystemClock.uptimeMillis() + STABLE_TIMEOUT_MS
    while (SystemClock.uptimeMillis() < deadline) {
      SystemClock.sleep(FRAME_MS)
      composeTestRule.waitForIdle()
      val current = capture(BLUR)
      if (current.buffer.contentEquals(previous.buffer)) return current
      previous = current
    }
    error("RenderScript capture did not stabilize within $STABLE_TIMEOUT_MS ms")
  }

  private companion object {
    const val BLUR = "blur"
    const val REFERENCE = "reference"
    const val LEGACY = "legacy"
    const val SIZE = 128
    const val PADDING = 16
    const val MARGIN = 16
    const val STABLE_TIMEOUT_MS = 2_000L
    const val FRAME_MS = 50L
    val GREY = Color(0.5f, 0.5f, 0.5f)

    /** Luminance of the interior, row by row. */
    fun PixelMap.interior(): List<FloatArray> = (MARGIN until height - MARGIN).map { y ->
      FloatArray(width - 2 * MARGIN) { x -> this[MARGIN + x, y].luminance() }
    }

    /** About 1 for one-pixel noise; grows with the grain size. */
    fun grain(pixels: PixelMap): Float {
      val rows = pixels.interior()
      fun meanStep(step: Int) = rows.flatMap { row -> (0 until row.size - step).map { abs(row[it + step] - row[it]) } }.average()
      return (meanStep(8) / meanStep(1)).toFloat()
    }

    fun maxInteriorDifference(a: PixelMap, b: PixelMap): Float {
      var max = 0f
      for (y in MARGIN until a.height - MARGIN) {
        for (x in MARGIN until a.width - MARGIN) {
          val first = a[x, y]
          val second = b[x, y]
          max = maxOf(max, abs(first.red - second.red), abs(first.green - second.green), abs(first.blue - second.blue))
        }
      }
      return max
    }
  }
}
