// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
@SdkSuppress(minSdkVersion = 31, maxSdkVersion = 32)
class ProgressiveBlurBoundsInstrumentationTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun reverseHorizontal_matchesFiniteReference() = verifyRendering(Offset(Float.POSITIVE_INFINITY, 0f))

  @Test
  fun reverseVertical_matchesFiniteReference() = verifyRendering(Offset(0f, Float.POSITIVE_INFINITY))

  @Test
  fun reverseDiagonal_matchesFiniteReference() = verifyRendering(Offset.Infinite)

  private fun verifyRendering(infiniteStart: Offset) {
    assertThat(Build.VERSION.SDK_INT in 31..32).isTrue()
    val reverse = gradient(infiniteStart, Offset.Zero)
    // Run on the instrumentation thread BEFORE setContent. Broken code aborts synchronously,
    // so it can never install a runaway layer loop on the UI thread.
    var callbacks = 0
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(ImageBitmap(64, 64)), Size(64f, 64f)) {
      drawProgressiveWithMultipleLayers(reverse) { _, _ ->
        callbacks++
        check(callbacks < 100) { "Abort runaway layered gradient before UI installation" }
      }
    }
    assertThat(callbacks).isEqualTo(3)
    assertThat(shouldDrawProgressiveWithLayers(reverse, 1f)).isTrue()

    val current = mutableStateOf(reverse)
    var nodeSize = IntSize.Zero
    rule.setContent {
      Box(
        Modifier
          .size(64.dp)
          .testTag(TAG)
          .onSizeChanged { nodeSize = it }
          .hazeBlur(
            input = HazeInput.Content,
            performanceMode = HazePerformanceMode.Quality,
            style = HazeBlurStyle {
              blurEnabled(true)
              blurRadius(8.dp)
              noiseFactor(0f)
              backgroundColor(Color.Transparent)
              colorEffects(emptyList())
              fallbackColorEffect(null)
              progressive(current.value)
            },
          )
          .drawBehind {
            val tile = size.width / 8
            for (x in 0..7) {
              for (y in 0..7) {
                drawRect(
                  color = if ((x + y) % 2 == 0) Color.Black else Color.White,
                  topLeft = Offset(x * tile, y * tile),
                  size = Size(tile, tile),
                )
              }
            }
          },
      )
    }
    rule.waitForIdle()
    val actual = capture()
    assertThat(nodeSize.width).isGreaterThan(0)
    assertThat(nodeSize.height).isGreaterThan(0)
    val finiteStart = Offset(
      if (infiniteStart.x == Float.POSITIVE_INFINITY) nodeSize.width.toFloat() else infiniteStart.x,
      if (infiniteStart.y == Float.POSITIVE_INFINITY) nodeSize.height.toFloat() else infiniteStart.y,
    )
    rule.runOnIdle { current.value = gradient(finiteStart, Offset.Zero) }
    rule.waitForIdle()
    val reference = capture()
    rule.runOnIdle { current.value = gradient(Offset.Zero, finiteStart) }
    rule.waitForIdle()
    val forward = capture()

    // Quarter/centre/three-quarter probes on either side of the checker edges.
    val positions = listOf(0.25f, 0.5f, 0.75f)
    var differsFromForward = false
    var darkest = 1f
    var lightest = 0f
    for (fx in positions) {
      for (fy in positions) {
        for (dx in listOf(-2, 0, 2)) {
          for (dy in listOf(-2, 0, 2)) {
            val x = (actual.width * fx).toInt() + dx
            val y = (actual.height * fy).toInt() + dy
            val a = actual[x, y]
            val r = reference[x, y]
            assertThat(a.red, "red at $x,$y").isCloseTo(r.red, 2f / 255)
            assertThat(a.green, "green at $x,$y").isCloseTo(r.green, 2f / 255)
            assertThat(a.blue, "blue at $x,$y").isCloseTo(r.blue, 2f / 255)
            assertThat(a.alpha, "alpha at $x,$y").isCloseTo(r.alpha, 2f / 255)
            assertThat(a.alpha, "opaque checker at $x,$y").isGreaterThan(0.95f)
            darkest = minOf(darkest, a.red)
            lightest = maxOf(lightest, a.red)
            if (abs(a.red - forward[x, y].red) > 2f / 255) differsFromForward = true
          }
        }
      }
    }
    assertThat(lightest - darkest, "nonempty checker contrast").isGreaterThan(0.05f)
    assertThat(differsFromForward, "reverse and forward blur must differ at an edge").isTrue()
  }

  private fun gradient(start: Offset, end: Offset) = HazeProgressive.LinearGradient(
    start = start,
    end = end,
    startIntensity = 0f,
    endIntensity = 1f,
    easing = LinearEasing,
  )

  private fun capture(): PixelMap = rule.onNodeWithTag(TAG).captureToImage().toPixelMap()

  private companion object {
    const val TAG = "progressive_blur_bounds"
  }
}
