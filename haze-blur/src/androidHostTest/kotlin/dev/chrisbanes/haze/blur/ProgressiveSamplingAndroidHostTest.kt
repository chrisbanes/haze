// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "+w300dp-h400dp")
class ProgressiveSamplingAndroidHostTest : ScreenshotTest() {

  @Test
  fun opaqueRadius24_preservesAllBandsAndCorners() = checkContent("opaque24", 24f)

  @Test
  fun opaqueRadius25_preservesAllBandsAndCorners() = checkContent("opaque25", 25f)

  @Test
  fun halfAlphaRadius24_preservesAllBandsAndCorners() = checkContent("half24", 24f, alpha = 0.5f)

  @Test
  fun halfAlphaRadius25_preservesAllBandsAndCorners() = checkContent("half25", 25f, alpha = 0.5f)

  @Test
  fun zeroIntensity_patternedContent_isIdentity() = checkContent("zero", 25f, gradient = 3)

  @Test
  fun horizontalGradient_patternedContent_preservesAuthoredDirection() = checkContent("horizontal", 25f, gradient = 1)

  @Test
  fun verticalGradient_patternedContent_preservesAuthoredDirection() = checkContent("vertical", 25f, gradient = 2)

  @Test
  fun fractionalOrigin_contentCapture_preservesInteriorAndGradient() = checkContent("fractional", 25f, gradient = 2, fractional = true)

  @Test
  fun defaultMode_opaque_preservesEdgeAlpha() = checkDownscaledEdges(HazePerformanceMode.Default, alpha = 1f)

  @Test
  fun defaultMode_halfAlpha_preservesEdgeAlpha() = checkDownscaledEdges(HazePerformanceMode.Default, alpha = 0.5f)

  @Test
  fun performanceMode_opaque_preservesEdgeAlpha() = checkDownscaledEdges(HazePerformanceMode.Performance, alpha = 1f)

  @Test
  fun performanceMode_halfAlpha_preservesEdgeAlpha() = checkDownscaledEdges(HazePerformanceMode.Performance, alpha = 0.5f)

  // #1772: where the mask is transparent the unblurred centre sample is the whole output, so a
  // downscaled capture must not blend its edge pixels with the transparency outside the crop.
  private fun checkDownscaledEdges(mode: HazePerformanceMode, alpha: Float) = runScreenshotTest {
    var size by mutableStateOf(IntSize(201, 185))
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(Modifier.size((size.width + 12).dp, (size.height + 12).dp).background(Color.Magenta).testTag("sampling")) {
          Canvas(
            Modifier.size(size.width.dp, size.height.dp).hazeBlur(
              input = HazeInput.Content,
              performanceMode = mode,
              expandLayerBounds = false,
              style = HazeBlurStyle {
                blurRadius(24.dp)
                noiseFactor(0f)
                colorEffects(emptyList())
                backgroundColor(Color.Transparent)
                progressive(
                  HazeProgressive.Brush(
                    Brush.verticalGradient(0f to Color.Black, 0.35f to Color.Transparent, 1f to Color.Transparent),
                  ),
                )
              },
            ),
          ) {
            drawRect(Color.Red.copy(alpha = alpha))
          }
        }
      }
    }
    for (candidate in listOf(IntSize(201, 185), IntSize(202, 186), IntSize(203, 187))) {
      composeTestRule.runOnIdle { size = candidate }
      waitForIdle()
      val pixels = captureRootPixels()
      val bounds = onNodeWithTag("sampling").fetchSemanticsNode().boundsInRoot
      val (w, h) = candidate

      // Red has no blue, so blue above (1 - alpha) is magenta backdrop showing through lost alpha.
      fun error(x: Int, y: Int) = abs(pixels[bounds.left.roundToInt() + x, bounds.top.roundToInt() + y].blue - (1f - alpha))
      val regions = linkedMapOf<String, Float>()
      fun region(name: String, xs: IntRange, ys: IntRange) {
        regions[name] = ys.maxOf { y -> xs.maxOf { x -> error(x, y) } }
      }
      region("left", 0..3, 4 until h - 4)
      region("right", w - 4 until w, 4 until h - 4)
      region("top", 4 until w - 4, 0..3)
      region("bottom", 4 until w - 4, h - 4 until h)
      region("topLeft", 0..3, 0..3)
      region("topRight", w - 4 until w, 0..3)
      region("bottomLeft", 0..3, h - 4 until h)
      region("bottomRight", w - 4 until w, h - 4 until h)
      val label = "$mode ${w}x$h alpha=$alpha"
      println("$label regions=$regions")
      // At most 3/255: errors quantize to multiples of 0.5/255, so this rejects 3.5/255.
      for ((region, error) in regions) assertThat(error, "$label $region").isLessThan(3.25f / 255f)
    }
  }

  private fun checkContent(label: String, radius: Float, alpha: Float = 1f, gradient: Int = 0, fractional: Boolean = false) = runScreenshotTest {
    val width = if (fractional) 141 else 140
    val height = if (fractional) 137 else 140
    val backdrop = if (gradient == 1 || gradient == 2) Color.Green else Color.Magenta
    var plain by mutableStateOf(false)
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(Modifier.size((width + 12).dp, (height + 12).dp).background(backdrop).testTag("sampling")) {
          Canvas(
            Modifier.size(width.dp, height.dp)
              .graphicsLayer {
                translationX = if (fractional) 2.25f else 0f
                translationY = if (fractional) 4.5f else 0f
              }
              .hazeBlur(
                input = HazeInput.Content,
                performanceMode = HazePerformanceMode.Quality,
                expandLayerBounds = false,
                style = HazeBlurStyle {
                  blurRadius(radius.dp)
                  noiseFactor(0f)
                  colorEffects(emptyList())
                  backgroundColor(Color.Transparent)
                  if (!plain) {
                    progressive(
                      HazeProgressive.Brush(
                        when (gradient) {
                          1 -> Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), endX = width.toFloat())
                          2 -> Brush.verticalGradient(listOf(Color.Black, Color.Transparent), endY = height.toFloat())
                          3 -> SolidColor(Color.Transparent)
                          else -> SolidColor(Color.Black)
                        },
                      ),
                    )
                  }
                },
              ),
          ) {
            drawRect(Color.Red.copy(alpha = alpha))
            if (gradient > 0) {
              for (x in 0 until width step 8) drawRect(Color.Black, Offset(x.toFloat(), 0f), Size(3f, height.toFloat()))
              for (y in 0 until height step 8) drawRect(Color.Blue, Offset(0f, y.toFloat()), Size(width.toFloat(), 3f))
            }
          }
        }
      }
    }
    fun read(control: Boolean): List<Color> {
      composeTestRule.runOnIdle { plain = control }
      waitForIdle()
      val pixels = captureRootPixels()
      val bounds = onNodeWithTag("sampling").fetchSemanticsNode().boundsInRoot
      val colors = (0 until height + 12).flatMap { y -> (0 until width + 12).map { x -> pixels[bounds.left.roundToInt() + x, bounds.top.roundToInt() + y] } }
      val directory = java.io.File("build/outputs/1727-sampling/host35")
      directory.mkdirs()
      val bitmap = android.graphics.Bitmap.createBitmap(colors.map { it.toArgb() }.toIntArray(), width + 12, height + 12, android.graphics.Bitmap.Config.ARGB_8888)
      java.io.File(directory, "$label-$control.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
      bitmap.recycle()
      return colors
    }
    val actual = read(false)
    val control = read(true)
    val stride = width + 12
    fun c(p: List<Color>, x: Int, y: Int) = p[y * stride + x]
    if (gradient == 0) {
      val regions = linkedMapOf<String, Float>()
      fun region(name: String, xs: IntRange, ys: IntRange) {
        regions[name] = ys.maxOf { y -> xs.maxOf { x -> abs(c(actual, x, y).blue - (1f - alpha)) } }
      }
      region("left", 0..3, 4 until height - 4)
      region("right", width - 4 until width, 4 until height - 4)
      region("top", 4 until width - 4, 0..3)
      region("bottom", 4 until width - 4, height - 4 until height)
      region("topLeft", 0..3, 0..3)
      region("topRight", width - 4 until width, 0..3)
      region("bottomLeft", 0..3, height - 4 until height)
      region("bottomRight", width - 4 until width, height - 4 until height)
      println("$label regions=$regions")
      for ((region, error) in regions) assertThat(error, "$label $region").isLessThan(0.02f)
      for (y in 0 until height) for (x in 0 until width) assertThat(abs(c(control, x, y).blue - (1f - alpha)), "$label plain($x,$y)").isLessThan(0.02f)
    } else if (gradient == 3) {
      // The plain blur is not an identity; compare the zero-mask output to the authored stripes.
      for (y in 4 until height - 4) for (x in 4 until width - 4) {
        val expected = when {
          y % 8 < 3 -> Color.Blue
          x % 8 < 3 -> Color.Black
          else -> Color.Red
        }
        val a = c(actual, x, y)
        assertThat(maxOf(abs(a.red - expected.red), abs(a.blue - expected.blue)), "zero($x,$y)").isLessThan(0.02f)
      }
    } else {
      val shiftX = if (fractional) 2 else 0
      val shiftY = if (fractional) 5 else 0
      val high = c(actual, 17 + shiftX, 17 + shiftY)
      val low = if (gradient == 1) c(actual, 129 + shiftX, 17 + shiftY) else c(actual, 17 + shiftX, 129 + shiftY)
      println("$label high=$high low=$low")
      // Both probes are on authored stripes, separated by whole periods.
      assertThat(high.red - low.red, "$label stronger blur at authored start").isGreaterThan(0.03f)
      // Authored Red/Black/Blue have no green; the green backdrop exposes lost opacity.
      val maxGreen = (8 until height - 8).maxOf { y -> (8 until width - 8).maxOf { x -> c(actual, x + shiftX, y + shiftY).green } }
      println("$label interior maxGreen=$maxGreen")
      assertThat(maxGreen, "$label opaque interior over green").isLessThan(0.02f)
    }
  }
}
