// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class GlassRimIntegerUniformAndroidHostTest : ScreenshotTest() {
  @Test
  fun directNativePaint_modeOneRendersGreen() {
    val shader = integerShader("mode == 1").apply { setIntUniform("mode", 1) }
    captureShader(shader, Color.Green)
  }

  @Test
  fun directNativePaint_modeZeroRendersRed() {
    val shader = integerShader("mode == 1").apply { setIntUniform("mode", 0) }
    captureShader(shader, Color.Red)
  }

  @Test
  fun unfilteredContent_rendersRed() {
    runScreenshotTest {
      setContent {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = androidx.compose.ui.Alignment.Center) {
          Box(Modifier.size(32.dp).background(Color.Red).testTag(SHADER_TAG))
        }
      }
      composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
      waitForIdle()
      assertPixel(captureRootPixels(), Color.Red)
    }
  }

  @Test fun adapter_positiveIntegerRendersGreen() = assertInteger(1)

  @Test fun adapter_negativeIntegerRendersGreen() = assertInteger(-1)

  @Test fun adapter_beyondFloatExactIntegerRendersGreen() = assertInteger(16777217)

  @Test
  fun independentAdapters_preserveTheirOwnValuesAcrossDrawUpdates() {
    val first = integerShader("mode == 1").apply { setIntUniform("mode", 1) }
    val second = integerShader("mode == -1").apply { setIntUniform("mode", -1) }
    val firstProvider = provider(first)
    val secondProvider = provider(second)
    val phases = arrayOf(mutableIntStateOf(0), mutableIntStateOf(0))
    val callbackCounts = intArrayOf(0, 0)
    val hardwareCanvasObserved = booleanArrayOf(false, false)

    runScreenshotTest {
      setContent {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = androidx.compose.ui.Alignment.Center) {
          Row {
            Box(Modifier.size(32.dp).drawBehindShader(first, phases[0], callbackCounts, hardwareCanvasObserved, 0).testTag("rim-a"))
            Box(Modifier.size(32.dp).drawBehindShader(second, phases[1], callbackCounts, hardwareCanvasObserved, 1).testTag("rim-b"))
          }
        }
      }
      composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
      waitForIdle()
      var pixels = captureRootPixels()
      assertThat(callbackCounts[0] > 0 && callbackCounts[1] > 0).isTrue()
      assertThat(hardwareCanvasObserved[0] && hardwareCanvasObserved[1]).isTrue()
      assertPixel(pixels, Color.Green, "rim-a")
      assertPixel(pixels, Color.Green, "rim-b")

      val firstCount = callbackCounts[0]
      composeTestRule.runOnIdle {
        firstProvider.setIntUniform("mode", 0)
        phases[0].intValue++
      }
      waitForIdle()
      pixels = captureRootPixels()
      assertThat(callbackCounts[0] > firstCount).isTrue()
      assertThat(hardwareCanvasObserved[0]).isTrue()
      assertPixel(pixels, Color.Red, "rim-a")
      assertPixel(pixels, Color.Green, "rim-b")

      val nextFirstCount = callbackCounts[0]
      composeTestRule.runOnIdle {
        firstProvider.setIntUniform("mode", 1)
        phases[0].intValue++
      }
      waitForIdle()
      pixels = captureRootPixels()
      assertThat(callbackCounts[0] > nextFirstCount).isTrue()
      assertThat(hardwareCanvasObserved[0]).isTrue()
      assertPixel(pixels, Color.Green, "rim-a")
      assertPixel(pixels, Color.Green, "rim-b")

      val secondCount = callbackCounts[1]
      composeTestRule.runOnIdle {
        secondProvider.setIntUniform("mode", 0)
        phases[1].intValue++
      }
      waitForIdle()
      pixels = captureRootPixels()
      assertThat(callbackCounts[1] > secondCount).isTrue()
      assertThat(hardwareCanvasObserved[1]).isTrue()
      assertPixel(pixels, Color.Green, "rim-a")
      assertPixel(pixels, Color.Red, "rim-b")
    }
  }

  @Test
  fun adapter_mixedSettersRenderTheirConfiguredValues() {
    val shader = RuntimeShader(MIXED_SOURCE)
    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
    try {
      with(provider(shader)) {
        setIntUniform("mode", 1)
        setFloatUniform("scalar", 0.25f)
        setFloatUniform("pair", 0.5f, 0.75f)
        setFloatUniform("vector", 1f, 0f, 0f, 1f)
        setColorUniform("tint", Color.Blue)
        setChildShader("aux", BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
      }
      captureShader(shader, Color.Green)
    } finally {
      bitmap.recycle()
    }
  }

  private fun assertInteger(value: Int) {
    val shader = integerShader("mode == $value")
    provider(shader).setIntUniform("mode", value)
    captureShader(shader, Color.Green)
  }

  private fun captureShader(shader: RuntimeShader, expected: Color) = runScreenshotTest {
    var callbackCount = 0
    var hardwareCanvasObserved = false
    setContent {
      Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Box(
          Modifier.size(32.dp).drawBehind {
            drawIntoCanvas { canvas ->
              val nativeCanvas = canvas.nativeCanvas
              hardwareCanvasObserved = hardwareCanvasObserved || nativeCanvas.isHardwareAccelerated
              callbackCount++
              nativeCanvas.drawRect(
                0f,
                0f,
                size.width,
                size.height,
                Paint().apply { this.shader = shader },
              )
            }
          }.testTag(SHADER_TAG),
        )
      }
    }
    composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
    waitForIdle()
    val pixels = captureRootPixels()
    assertThat(callbackCount > 0).isTrue()
    assertThat(hardwareCanvasObserved).isTrue()
    assertPixel(pixels, expected)
  }

  private fun Modifier.drawBehindShader(
    shader: RuntimeShader,
    phase: androidx.compose.runtime.MutableIntState,
    callbackCounts: IntArray,
    hardwareCanvasObserved: BooleanArray,
    index: Int,
  ) = drawBehind {
    if (phase.intValue < 0) drawRect(Color.Transparent)
    callbackCounts[index]++
    drawIntoCanvas { canvas ->
      hardwareCanvasObserved[index] = hardwareCanvasObserved[index] || canvas.nativeCanvas.isHardwareAccelerated
      canvas.nativeCanvas.drawRect(
        0f,
        0f,
        size.width,
        size.height,
        Paint().apply { this.shader = shader },
      )
    }
  }

  private fun provider(shader: RuntimeShader): RuntimeShaderUniformProvider {
    val constructor = Class.forName("dev.chrisbanes.haze.glass.GlassRimUniformProvider").getDeclaredConstructor(RuntimeShader::class.java)
    constructor.isAccessible = true
    val instance = constructor.newInstance(shader)
    assertThat(instance).isInstanceOf<RuntimeShaderUniformProvider>()
    return instance as RuntimeShaderUniformProvider
  }

  private fun ScreenshotUiTest.assertPixel(
    pixels: androidx.compose.ui.graphics.PixelMap,
    expected: Color,
    tag: String = SHADER_TAG,
  ) {
    val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    val actual = pixels[bounds.center.x.roundToInt(), bounds.center.y.roundToInt()]
    assertColor(actual, expected)
  }

  private fun assertColor(actual: Color, expected: Color) {
    assertThat(abs(actual.red - expected.red), "red: $actual").isLessThanOrEqualTo(1f / 255f)
    assertThat(abs(actual.green - expected.green), "green: $actual").isLessThanOrEqualTo(1f / 255f)
    assertThat(abs(actual.blue - expected.blue), "blue: $actual").isLessThanOrEqualTo(1f / 255f)
    assertThat(abs(actual.alpha - expected.alpha), "alpha: $actual").isLessThanOrEqualTo(1f / 255f)
  }

  private fun integerShader(predicate: String) = RuntimeShader(
    "uniform int mode; half4 main(float2 p) { return $predicate ? half4(0, 1, 0, 1) : half4(1, 0, 0, 1); }",
  )

  companion object {
    private const val SHADER_TAG = "shader-paint"
    private val MIXED_SOURCE = """
      uniform int mode; uniform float scalar; uniform float2 pair; uniform float4 vector;
      layout(color) uniform half4 tint; uniform shader aux;
      half4 main(float2 p) {
        bool matches = mode == 1 && scalar == 0.25 && pair == float2(0.5, 0.75) && vector == float4(1, 0, 0, 1) && (abs(tint.r) < 0.001 && abs(tint.g) < 0.001 && abs(tint.b - 1) < 0.001 && abs(tint.a - 1) < 0.001);
        return matches ? aux.eval(p) : half4(1, 0, 0, 1);
      }
    """.trimIndent()
  }
}
