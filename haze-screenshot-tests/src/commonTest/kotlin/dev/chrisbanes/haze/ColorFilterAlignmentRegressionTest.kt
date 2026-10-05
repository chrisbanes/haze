// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.testTag
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test

abstract class ColorFilterAlignmentRegressionTest : ScreenshotTest() {
  protected open fun ScreenshotUiTest.refreshCapture() = Unit

  @Test
  fun sourceBlur_producesSoftenedPixelsAndColorFilterOutput() = runScreenshotTest {
    val fixture = installFixture()
    for (mode in modes) {
      fixture.mode = mode
      fixture.radius = 0.dp
      fixture.effects = emptyList()
      val sharp = capture()
      val bounds = effectBounds()
      sharp.assertSharp(bounds, "$mode sharp")
      fixture.radius = 12.dp
      val blurred = capture()
      blurred.assertBlurred(bounds, "$mode blurred")
      fixture.effects = listOf(swapEffect)
      capture().assertSwapped(bounds, "$mode swapped")
    }
  }

  @Test
  fun identityFilter_preservesExpandedSourcesAtAllScales() = runScreenshotTest {
    val fixture = installFixture()
    fixture.radius = 12.dp
    for (mode in modes) {
      fixture.mode = mode
      fixture.effects = emptyList()
      val reference = capture()
      val bounds = effectBounds()
      reference.assertBlurred(bounds, "$mode reference")
      fixture.effects = listOf(swapEffect)
      capture().assertSwapped(bounds, "$mode positive control")
      fixture.effects = listOf(HazeColorEffect.colorFilter(ColorFilter.colorMatrix(ColorMatrix())))
      val identity = capture()
      val density = bounds.width / 160f
      val inset = (2f * density).roundToInt()
      var maxDifference = 0f
      for (y in bounds.top.roundToInt() + inset until bounds.bottom.roundToInt() - inset) {
        for (x in bounds.left.roundToInt() + inset until bounds.right.roundToInt() - inset) {
          val first = reference[x, y]
          val second = identity[x, y]
          maxDifference = maxOf(
            maxDifference,
            abs(first.red - second.red), abs(first.green - second.green),
            abs(first.blue - second.blue), abs(first.alpha - second.alpha),
          )
        }
      }
      println("$mode identity maximum RGBA difference=$maxDifference")
      assertThat(maxDifference, "$mode identity RGBA").isLessThanOrEqualTo(3f / 255f)
      val y = (bounds.center.y - 24f * density).roundToInt()
      val xRange = (bounds.center.x - 24f * density).roundToInt()..(bounds.center.x + 24f * density).roundToInt()
      val referenceCrossing = reference.redBlueCrossing(y, xRange)
      val identityCrossing = identity.redBlueCrossing(y, xRange)
      println("$mode crossing reference=$referenceCrossing identity=$identityCrossing")
      assertThat(abs(referenceCrossing - identityCrossing), "$mode horizontal crossing displacement")
        .isLessThanOrEqualTo(1f)
      val yRange = (bounds.center.y - 24f * density).roundToInt()..(bounds.center.y + 40f * density).roundToInt()
      for (sign in listOf(-1, 1)) {
        val x = (bounds.center.x + sign * 48f * density).roundToInt()
        val referenceCentroid = reference.greenCentroid(x, yRange)
        val identityCentroid = identity.greenCentroid(x, yRange)
        println("$mode stripe x=$x reference=$referenceCentroid identity=$identityCentroid")
        assertThat(abs(referenceCentroid - identityCentroid), "$mode stripe centroid displacement at x=$x")
          .isLessThanOrEqualTo(1f)
      }
    }
  }

  private fun ScreenshotUiTest.installFixture(): Fixture {
    val fixture = Fixture()
    val state = HazeState()
    setContent {
      val currentRadius = fixture.radius
      val currentEffects = fixture.effects
      val currentMode = fixture.mode
      val style = HazeBlurStyle {
        blurEnabled(true)
        blurRadius(currentRadius)
        backgroundColor(Color.Transparent)
        alpha(1f)
        noiseFactor(0f)
        colorEffects(currentEffects)
        progressive(null)
        mask(null)
        fallbackColorEffect(null)
      }
      Box(Modifier.fillMaxSize().background(Color.Magenta), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(240.dp, 160.dp).hazeSource(state)) {
          drawRect(Color.Red, size = Size(size.width / 2f, size.height))
          drawRect(Color.Blue, topLeft = Offset(size.width / 2f, 0f), size = Size(size.width / 2f, size.height))
          drawRect(Color.Green, topLeft = Offset(0f, 84.dp.toPx()), size = Size(size.width, 8.dp.toPx()))
        }
        Box(
          Modifier.size(160.dp, 96.dp).testTag("alignment-effect").hazeBlur(
            input = HazeInput.Sources(state),
            style = style,
            performanceMode = currentMode,
            expandLayerBounds = true,
          ),
        )
      }
    }
    return fixture
  }

  private fun ScreenshotUiTest.capture(): PixelSnapshot {
    waitForIdle()
    refreshCapture()
    waitForIdle()
    return captureRootPixels().snapshot()
  }

  private fun ScreenshotUiTest.effectBounds(): Rect =
    onNodeWithTag("alignment-effect").fetchSemanticsNode().boundsInRoot.also {
      assertThat(it.width, "effect width").isGreaterThan(0f)
      assertThat(it.height, "effect height").isGreaterThan(0f)
    }

  private fun PixelSnapshot.edgePixels(bounds: Rect, distanceDp: Float): Pair<Color, Color> {
    val density = bounds.width / 160f
    val y = (bounds.center.y - 24f * density).roundToInt()
    return this[(bounds.center.x - distanceDp * density).roundToInt(), y] to
      this[(bounds.center.x + distanceDp * density).roundToInt(), y]
  }

  private fun PixelSnapshot.assertSharp(bounds: Rect, label: String) {
    val (left, right) = edgePixels(bounds, 3f)
    println("$label left=$left right=$right")
    assertThat(left.red, "$label left red").isGreaterThan(0.95f)
    assertThat(left.blue, "$label left blue").isLessThan(0.05f)
    assertThat(right.blue, "$label right blue").isGreaterThan(0.95f)
    assertThat(right.red, "$label right red").isLessThan(0.05f)
  }

  private fun PixelSnapshot.assertBlurred(bounds: Rect, label: String) {
    val (left, right) = edgePixels(bounds, 3f)
    val (farLeft, farRight) = edgePixels(bounds, 48f)
    println("$label nearLeft=$left nearRight=$right farLeft=$farLeft farRight=$farRight")
    assertThat(left.blue, "$label left opposite channel").isGreaterThan(0.05f)
    assertThat(right.red, "$label right opposite channel").isGreaterThan(0.05f)
    assertThat(farLeft.red, "$label left interior").isGreaterThan(0.9f)
    assertThat(farRight.blue, "$label right interior").isGreaterThan(0.9f)
  }

  private fun PixelSnapshot.assertSwapped(bounds: Rect, label: String) {
    val (left, right) = edgePixels(bounds, 48f)
    println("$label left=$left right=$right")
    assertThat(left.blue, "$label left blue").isGreaterThan(0.9f)
    assertThat(left.red, "$label left red").isLessThan(0.1f)
    assertThat(right.red, "$label right red").isGreaterThan(0.9f)
    assertThat(right.blue, "$label right blue").isLessThan(0.1f)
  }

  private fun PixelSnapshot.redBlueCrossing(y: Int, range: IntRange): Float {
    for (x in range.first until range.last) {
      val first = this[x, y].let { it.red - it.blue }
      val next = this[x + 1, y].let { it.red - it.blue }
      if (first >= 0f && next < 0f) return x + first / (first - next)
    }
    error("Missing red/blue crossing at y=$y in $range")
  }

  private fun PixelSnapshot.greenCentroid(x: Int, range: IntRange): Float {
    val peak = range.maxOf { this[x, it].green }
    val sum = range.sumOf { this[x, it].green.toDouble() }
    assertThat(peak, "stripe green peak at x=$x").isGreaterThan(0.05f)
    assertThat(sum, "stripe green sum at x=$x").isGreaterThan(0.0)
    return (range.sumOf { it * this[x, it].green.toDouble() } / sum).toFloat()
  }

  private class Fixture {
    var radius by mutableStateOf(0.dp)
    var effects by mutableStateOf<List<HazeColorEffect>>(emptyList())
    var mode by mutableStateOf<HazePerformanceMode>(HazePerformanceMode.Quality)
  }

  private companion object {
    val modes = listOf(HazePerformanceMode.Quality, HazePerformanceMode.Balanced, HazePerformanceMode.Performance)
    val swapEffect = HazeColorEffect.colorFilter(
      ColorFilter.colorMatrix(
        ColorMatrix(
          floatArrayOf(
            0f, 0f, 1f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
          ),
        ),
      ),
      BlendMode.SrcOver,
    )
  }
}
