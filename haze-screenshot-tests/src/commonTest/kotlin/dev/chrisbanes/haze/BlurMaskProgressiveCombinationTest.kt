// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * `mask` fades the complete effect after `progressive` varies its intensity, on every Blur path.
 * Each check compares against the same path's single-property output, so it is path-independent.
 */
class BlurMaskProgressiveCombinationTest : ScreenshotTest() {

  @Test
  fun blurMaskAndProgressive_composeOnEveryPath() = runScreenshotTest {
    assertMaskAndProgressiveCompose(blur = true)
  }

  @Test
  fun scrimMaskAndProgressive_composeOnEveryProfile() = runScreenshotTest {
    assertMaskAndProgressiveCompose(blur = false)
  }

  private fun ScreenshotUiTest.assertMaskAndProgressiveCompose(blur: Boolean) {
    val base = HazeBlurStyle {
      blurEnabled(blur)
      blurRadius(16.dp)
      noiseFactor(0f)
      backgroundColor(Color.Transparent)
      colorEffects(listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.5f))))
      mask(null)
      progressive(null)
    }
    val progressiveOnly = base.then { progressive(PROGRESSIVE) }
    val maskOnly = base.then { mask(MASK) }
    val combined = base.then {
      mask(MASK)
      progressive(PROGRESSIVE)
    }

    var style by mutableStateOf<HazeBlurStyle?>(null)
    var mode by mutableStateOf<HazePerformanceMode>(HazePerformanceMode.Quality)
    setContent {
      val state = remember { HazeState() }
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(320.dp).hazeSource(state)) {
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
          Modifier
            .size(200.dp)
            .testTag(EFFECT_TAG)
            .then(
              if (current != null) {
                Modifier.hazeBlur(HazeInput.Sources(state), style = current, performanceMode = mode)
              } else {
                Modifier
              },
            ),
        )
      }
    }

    for (performanceMode in listOf(HazePerformanceMode.Quality, HazePerformanceMode.Balanced)) {
      mode = performanceMode
      style = null
      val none = capture()
      style = progressiveOnly
      val prog = capture()
      style = combined
      val both = capture()
      style = maskOnly
      val masked = capture()
      style = combined
      val bothAgain = capture()

      val bounds = effectBounds()
      fun region(x: ClosedFloatingPointRange<Float>, y: ClosedFloatingPointRange<Float>) = IntRect(
        left = (bounds.left + bounds.width * x.start).roundToInt(),
        top = (bounds.top + bounds.height * y.start).roundToInt(),
        right = (bounds.left + bounds.width * x.endInclusive).roundToInt(),
        bottom = (bounds.top + bounds.height * y.endInclusive).roundToInt(),
      )
      val top = 0.02f..0.12f
      val bottom = 0.90f..0.98f
      val mid = 0.47f..0.53f
      val left = 0.02f..0.12f
      val right = 0.88f..0.98f
      val all = 0.02f..0.98f
      val label = "${if (blur) "blur" else "scrim"} $performanceMode"
      fun diff(a: PixelSnapshot, b: PixelSnapshot, area: IntRect, name: String): Float =
        a.crop(area).meanAbsoluteDifference(b.crop(area)).also { println("$label $name=$it") }

      // Mask transparent: nothing of the effect remains, although progressive is high there.
      assertThat(diff(both, none, region(all, bottom), "bottom combined-none"), "$label bottom combined vs none")
        .isLessThanOrEqualTo(TOLERANCE)
      assertThat(diff(prog, none, region(right, bottom), "bottom-right progressive-none"), "$label bottom-right progressive vs none")
        .isGreaterThan(SENSITIVITY)

      // Mask opaque: the progressive result shows through unchanged, low and high.
      assertThat(diff(both, prog, region(left, top), "top-left combined-progressive"), "$label top-left combined vs progressive")
        .isLessThanOrEqualTo(TOLERANCE)
      assertThat(diff(both, prog, region(right, top), "top-right combined-progressive"), "$label top-right combined vs progressive")
        .isLessThanOrEqualTo(TOLERANCE)
      assertThat(diff(both, none, region(right, top), "top-right combined-none"), "$label top-right combined vs none")
        .isGreaterThan(SENSITIVITY)

      // Mask partial: the progressive result is faded, not dropped or kept whole.
      val midRight = region(right, mid)
      val span = diff(prog, none, midRight, "mid-right progressive-none")
      val toNone = diff(both, none, midRight, "mid-right combined-none")
      val toProg = diff(both, prog, midRight, "mid-right combined-progressive")
      assertThat(toNone, "$label mid-right combined vs none").isGreaterThan(TOLERANCE)
      assertThat(toProg, "$label mid-right combined vs progressive").isGreaterThan(TOLERANCE)
      assertThat(toNone, "$label mid-right combined vs none within span").isLessThan(span)
      assertThat(toProg, "$label mid-right combined vs progressive within span").isLessThan(span)

      // Runtime updates: returning to the combined style reproduces it; mask-only differs.
      assertThat(diff(both, bothAgain, region(all, all), "combined repeat"), "$label combined repeat")
        .isLessThanOrEqualTo(REPEAT_TOLERANCE)
      assertThat(diff(masked, both, region(left, top), "top-left mask-only-combined"), "$label top-left mask only vs combined")
        .isGreaterThan(SENSITIVITY)
    }

    mode = HazePerformanceMode.Quality
    style = combined
    waitForIdle()
    captureRoot()
  }

  private fun ScreenshotUiTest.capture(): PixelSnapshot {
    waitForIdle()
    waitForIdle()
    return captureRootPixels().snapshot()
  }

  private fun ScreenshotUiTest.effectBounds(): Rect =
    onNodeWithTag(EFFECT_TAG).fetchSemanticsNode().boundsInRoot.also {
      assertThat(it.width, "effect width").isGreaterThan(0f)
    }

  private companion object {
    const val EFFECT_TAG = "mask-progressive-effect"
    const val TOLERANCE = 0.03f
    const val SENSITIVITY = 0.05f
    const val REPEAT_TOLERANCE = 0.01f
    val MASK = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
    val PROGRESSIVE = HazeProgressive.horizontalGradient(
      easing = LinearEasing,
      startIntensity = 0f,
      endIntensity = 1f,
    )
  }
}
