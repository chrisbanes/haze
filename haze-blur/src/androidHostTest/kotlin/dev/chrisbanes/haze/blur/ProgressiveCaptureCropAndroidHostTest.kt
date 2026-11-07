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
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ScaleFactor
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
import dev.chrisbanes.haze.createProgressiveBlurRenderEffect
import dev.chrisbanes.haze.drawScaledContent
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.test.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "+w300dp-h400dp")
class ProgressiveCaptureCropAndroidHostTest : ScreenshotTest() {

  @Test
  fun roundedDownContent_matchesReferenceAndRejectsOldCrop() = checkCrop(203)

  @Test
  fun exactContent_matchesReferenceWithPlainControl() = checkCrop(204)

  @Test
  fun expandedFractionalOrigin_matchesReference() = checkCrop(21, Size(25.2f, 23.4f), Offset(2.25f, 4.5f), patterned = true)

  @Test
  fun clippedCustomMask_preservesAuthoredOrigin() = checkCrop(21, Size(25.2f, 23.4f), Offset(-5.25f, -4.5f), patterned = true)

  private fun checkCrop(width: Int, layerSize: Size = Size(width.toFloat(), width.toFloat()), offset: Offset = Offset.Zero, patterned: Boolean = false) = runScreenshotTest {
    val content = if (patterned) Size(21f, 19f) else layerSize
    val nominal = 0.75f
    val allocation = IntSize((layerSize.width * nominal).roundToInt(), (layerSize.height * nominal).roundToInt())
    val a = ScaleFactor(allocation.width / layerSize.width, allocation.height / layerSize.height)
    val origin = Offset(offset.x * a.scaleX, offset.y * a.scaleY)
    val start = Offset(maxOf(0f, origin.x), maxOf(0f, origin.y))
    val end = Offset(minOf(allocation.width.toFloat(), (offset.x + content.width) * a.scaleX), minOf(allocation.height.toFloat(), (offset.y + content.height) * a.scaleY))
    val gradient = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), endY = content.height) as ShaderBrush
    val brush = if (patterned) {
      object : ShaderBrush() {
        override fun createShader(size: Size) = gradient.createShader(size)
      }
    } else {
      SolidColor(Color.Black)
    }
    var variant by mutableStateOf(0)
    setContent {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        val layer = rememberGraphicsLayer()
        Box(Modifier.size(content.width.dp, content.height.dp).background(Color.Magenta).testTag("crop")) {
          if (!patterned && (variant == 0 || variant == 3)) {
            Canvas(
              Modifier.size(content.width.dp, content.height.dp).hazeBlur(
                input = HazeInput.Content,
                performanceMode = HazePerformanceMode.Fixed(5f / 12f),
                style = HazeBlurStyle {
                  blurRadius(8.dp)
                  noiseFactor(0f)
                  colorEffects(emptyList())
                  backgroundColor(Color.Transparent)
                  if (variant == 0) progressive(HazeProgressive.Brush(brush))
                },
              ),
            ) { drawRect(Color.Red) }
          } else {
            Canvas(Modifier.size(content.width.dp, content.height.dp)) {
              layer.record(size = allocation) {
                scale(a.scaleX, a.scaleY, Offset.Zero) {
                  drawRect(if (patterned) Color.White else Color.Red, size = layerSize)
                  if (patterned) {
                    for (x in 0 until 40 step 4) drawRect(Color.Black, Offset(x.toFloat(), 0f), Size(2f, 40f))
                    for (y in 0 until 40 step 7) drawRect(Color.Red, Offset(0f, y.toFloat()), Size(40f, 2f))
                  }
                }
              }
              val mask = if (brush is ShaderBrush) brush.createScaledShader(content, a, Size(allocation.width.toFloat(), allocation.height.toFloat()), Offset.Zero) else (Brush.linearGradient(listOf(Color.Black, Color.Black)) as ShaderBrush).createShader(content)
              layer.renderEffect = if (variant == 0) {
                createRenderEffect(
                  composeTestRule.activity, Density(1f),
                  RenderEffectParams(
                    blurRadius = 8.dp, noiseFactor = 0f, scale = nominal,
                    contentSize = content, contentOffset = offset, captureScale = a,
                    captureSize = Size(allocation.width.toFloat(), allocation.height.toFloat()),
                    progressive = HazeProgressive.Brush(brush), blurTileMode = TileMode.Clamp,
                  ),
                ).asComposeRenderEffect()
              } else {
                val cropOrigin = if (variant == 2 && !patterned) Offset.Zero else start
                val cropSize = if (variant == 2 && !patterned) Size(ceil(content.width * nominal), ceil(content.height * nominal)) else Size(end.x - start.x, end.y - start.y)
                createProgressiveBlurRenderEffect(8f * nominal, cropSize, cropOrigin, mask, if (variant == 2 && patterned) start else origin).asComposeRenderEffect()
              }
              layer.clip = false
              drawScaledContent(-offset, a, clip = false) { drawLayer(layer) }
            }
          }
        }
      }
    }
    fun read(next: Int): List<Color> {
      composeTestRule.runOnIdle { variant = next }
      waitForIdle()
      val pixels = captureRootPixels()
      val bounds = onNodeWithTag("crop").fetchSemanticsNode().boundsInRoot
      val colors = (0 until content.height.toInt()).flatMap { y -> (0 until content.width.toInt()).map { x -> pixels[bounds.left.roundToInt() + x, bounds.top.roundToInt() + y] } }
      val directory = java.io.File("build/outputs/1728-crop/host35")
      directory.mkdirs()
      val bitmap = android.graphics.Bitmap.createBitmap(colors.map { it.toArgb() }.toIntArray(), content.width.toInt(), content.height.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
      java.io.File(directory, "crop-$width-${offset.x}-$next.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
      bitmap.recycle()
      return colors
    }
    val actual = read(0)
    val reference = read(1)
    fun residual(a: List<Color>, b: List<Color>): Float = a.indices.maxOf { maxOf(abs(a[it].red - b[it].red), abs(a[it].green - b[it].green), abs(a[it].blue - b[it].blue)) }
    val error = residual(actual, reference)
    println("crop width=$width layer=$layerSize offset=$offset patterned=$patterned residual=$error")
    assertThat(error).isLessThan(0.02f)
    if (!patterned) {
      fun edge(p: List<Color>, right: Boolean, horizontal: Boolean): Float {
        val w = content.width.toInt()
        val h = content.height.toInt()
        return (w / 4 until w * 3 / 4).maxOf { along ->
          (0 until 4).maxOf { line ->
            val x = if (horizontal) {
              if (right) w - 1 - line else line
            } else {
              along
            }
            val y = if (horizontal) {
              along
            } else {
              if (right) h - 1 - line else line
            }
            p[y * w + x].blue
          }
        }
      }
      val plain = read(3)
      assertThat(edge(plain, true, true)).isLessThan(0.02f)
      assertThat(edge(plain, true, false)).isLessThan(0.02f)
      if (width == 203) {
        val fault = read(2)
        assertThat(edge(fault, true, true) - edge(fault, false, true)).isGreaterThan(0.05f)
        assertThat(edge(fault, true, false) - edge(fault, false, false)).isGreaterThan(0.05f)
      }
    } else if (offset.x < 0f) {
      val fault = residual(actual, read(2))
      println("mask origin fault=$fault")
      assertThat(fault).isGreaterThan(0.02f)
    }
  }
}
