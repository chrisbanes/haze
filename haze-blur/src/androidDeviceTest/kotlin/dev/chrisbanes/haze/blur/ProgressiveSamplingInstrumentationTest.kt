// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.activity.ComponentActivity
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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
@SdkSuppress(minSdkVersion = 33)
class ProgressiveSamplingInstrumentationTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

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

  private fun checkContent(label: String, radius: Float, alpha: Float = 1f, gradient: Int = 0, fractional: Boolean = false) = run {
    val width = if (fractional) 141 else 140
    val height = if (fractional) 137 else 140
    var plain by mutableStateOf(false)
    rule.setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(Modifier.size((width + 12).dp, (height + 12).dp).background(Color.Magenta).testTag("sampling")) {
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
      rule.runOnIdle { plain = control }
      rule.waitForIdle()
      val pixels = rule.onNodeWithTag("sampling").captureToImage().toPixelMap()
      val colors = (0 until height + 12).flatMap { y -> (0 until width + 12).map { x -> pixels[x, y] } }
      val directory = java.io.File(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: rule.activity.cacheDir.absolutePath, "1727-sampling")
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
      for (y in 8 until height - 8) for (x in 8 until width - 8) assertThat(c(actual, x + shiftX, y + shiftY).alpha, "$label opaque interior").isGreaterThan(0.98f)
    }
  }
}
