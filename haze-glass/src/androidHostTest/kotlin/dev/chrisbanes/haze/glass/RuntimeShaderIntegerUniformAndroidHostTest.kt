// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotNull
import dev.chrisbanes.haze.asComposeRenderEffect
import dev.chrisbanes.haze.createMutableRuntimeShaderRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class RuntimeShaderIntegerUniformAndroidHostTest : ScreenshotTest() {
  @Test
  @Config(sdk = [35])
  fun nativeSetter_acceptsIntegerAndRejectsFloat() {
    val shader = RuntimeShader(MODE_SOURCE)
    shader.setIntUniform("mode", 1)
    assertThat(runCatching { shader.setFloatUniform("mode", 1f) }.exceptionOrNull()).isNotNull().isInstanceOf<IllegalArgumentException>()
  }

  @Test
  @Config(sdk = [35])
  fun hazeFactory_acceptsInteger() {
    hazeEffect(1)
  }

  @Test
  fun nativeModeOne_rendersGreen() = assertEffectColor(nativeEffect(1), Color.Green)

  @Test
  fun nativeModeZero_rendersRedContent() = assertEffectColor(nativeEffect(0), Color.Red)

  @Test
  fun unfilteredContent_rendersRed() = assertEffectColor(null, Color.Red)

  @Test
  fun nativeTransparentEffect_revealsMagentaUnderlay() {
    val shader = RuntimeShader(TRANSPARENT_SOURCE).apply { setIntUniform("mode", 0) }
    assertEffectColor(RenderEffect.createRuntimeShaderEffect(shader, "content"), Color.Magenta)
  }

  @Test
  fun unfilteredMagenta_rendersMagenta() = assertEffectColor(null, Color.Magenta, Color.Magenta)

  @Test
  fun hazeModeOne_rendersGreen() = assertEffectColor(hazeEffect(1), Color.Green)

  @Test
  fun exactInteger_negativeOne() {
    val value = -1
    val literal = if (value == Int.MIN_VALUE) "(-2147483647 - 1)" else value.toString()
    val source = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == $literal ? half4(0, 1, 0, 1) : content.eval(p); }"
    val effect = createRuntimeShaderRenderEffect(createRuntimeEffect(source), arrayOf("content"), arrayOf(null)) { setIntUniform("mode", value) }
    assertEffectColor(effect, Color.Green)
  }

  @Test
  fun exactInteger_negativeBeyondFloat() {
    val value = -16777217
    val literal = if (value == Int.MIN_VALUE) "(-2147483647 - 1)" else value.toString()
    val source = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == $literal ? half4(0, 1, 0, 1) : content.eval(p); }"
    val effect = createRuntimeShaderRenderEffect(createRuntimeEffect(source), arrayOf("content"), arrayOf(null)) { setIntUniform("mode", value) }
    assertEffectColor(effect, Color.Green)
  }

  @Test
  fun exactInteger_positiveBeyondFloat() {
    val value = 16777217
    val literal = if (value == Int.MIN_VALUE) "(-2147483647 - 1)" else value.toString()
    val source = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == $literal ? half4(0, 1, 0, 1) : content.eval(p); }"
    val effect = createRuntimeShaderRenderEffect(createRuntimeEffect(source), arrayOf("content"), arrayOf(null)) { setIntUniform("mode", value) }
    assertEffectColor(effect, Color.Green)
  }

  @Test
  fun exactInteger_minimum() {
    val value = Int.MIN_VALUE
    val literal = if (value == Int.MIN_VALUE) "(-2147483647 - 1)" else value.toString()
    val source = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == $literal ? half4(0, 1, 0, 1) : content.eval(p); }"
    val effect = createRuntimeShaderRenderEffect(createRuntimeEffect(source), arrayOf("content"), arrayOf(null)) { setIntUniform("mode", value) }
    assertEffectColor(effect, Color.Green)
  }

  @Test
  fun exactInteger_maximum() {
    val value = Int.MAX_VALUE
    val literal = if (value == Int.MIN_VALUE) "(-2147483647 - 1)" else value.toString()
    val source = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == $literal ? half4(0, 1, 0, 1) : content.eval(p); }"
    val effect = createRuntimeShaderRenderEffect(createRuntimeEffect(source), arrayOf("content"), arrayOf(null)) { setIntUniform("mode", value) }
    assertEffectColor(effect, Color.Green)
  }

  @Test
  fun sharedSource_resetsOmittedIntegerAndPreservesSnapshots() {
    val source = createRuntimeEffect(THREE_COLORS)
    val a = createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) { setIntUniform("mode", 1) }
    val b = createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) {}
    val c = createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) { setIntUniform("mode", -1) }
    assertEffectsColors(listOf(a, b, c, a), listOf(Color.Green, Color.Red, Color.Blue, Color.Green))
  }

  @Test
  fun mutableEffects_retainUpdatesAndIndependentSnapshots() {
    val source = createRuntimeEffect(THREE_COLORS)
    val a = createMutableRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null))
    val b = createMutableRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null))
    val old = a.updateUniforms { setIntUniform("mode", 1) }
    val current = a.updateUniforms { setIntUniform("mode", -1) }
    val independent = b.updateUniforms {}
    val retained = a.updateUniforms {}
    val inputs = a.updateInputs(arrayOf(null)) { setIntUniform("mode", 1) }
    assertEffectsColors(listOf(old, current, independent, retained, inputs), listOf(Color.Green, Color.Blue, Color.Red, Color.Blue, Color.Green))
  }

  @Test
  fun mixedUniforms_resetIndependentEffectsAndPreserveSnapshot() {
    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
    val child = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    try {
      val source = createRuntimeEffect(MIXED_SOURCE)
      val a = createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) {
        setIntUniform("mode", 1)
        setFloatUniform("scalar", 0.25f)
        setFloatUniform("pair", 0.5f, 0.75f)
        setFloatUniform("vector", 1f, 0f, 0f, 1f)
        setColorUniform("tint", Color.Blue)
        setChildShader("aux", child)
      }
      val b = createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) {}
      assertEffectsColors(listOf(a, b, a), listOf(Color.Green, Color.Magenta, Color.Green))
    } finally {
      bitmap.recycle()
    }
  }

  private fun hazeEffect(mode: Int): RenderEffect = createRuntimeShaderRenderEffect(
    createRuntimeEffect(MODE_SOURCE),
    arrayOf("content"),
    arrayOf(null),
  ) { setIntUniform("mode", mode) }

  private fun nativeEffect(mode: Int): RenderEffect = RenderEffect.createRuntimeShaderEffect(
    RuntimeShader(MODE_SOURCE).apply { setIntUniform("mode", mode) },
    "content",
  )

  private fun assertEffectColor(effect: RenderEffect?, expected: Color, content: Color = Color.Red) =
    assertEffectsColors(listOf(effect), listOf(expected), content)

  private fun assertEffectsColors(effects: List<RenderEffect?>, expected: List<Color>, content: Color = Color.Red) = runScreenshotTest {
    setContent {
      Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Row {
          effects.forEachIndexed { index, effect ->
            Box(Modifier.size(32.dp)) {
              Box(Modifier.fillMaxSize().background(Color.Magenta))
              Box(Modifier.fillMaxSize().testTag("filtered$index").graphicsLayer { clip = true; renderEffect = effect?.asComposeRenderEffect() }.background(content))
            }
          }
        }
      }
    }
    composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
    waitForIdle()
    val pixels = captureRootPixels()
    expected.forEachIndexed { index, color ->
      val bounds = onNodeWithTag("filtered$index").fetchSemanticsNode().boundsInRoot
      val actual = pixels[bounds.center.x.roundToInt(), bounds.center.y.roundToInt()]
      assertColor(actual, color)
    }
  }

  private fun assertColor(actual: Color, expected: Color) {
    assertThat(abs(actual.red - expected.red), "red: $actual").isLessThanOrEqualTo(1f / 255f)
    assertThat(abs(actual.green - expected.green), "green: $actual").isLessThanOrEqualTo(1f / 255f)
    assertThat(abs(actual.blue - expected.blue), "blue: $actual").isLessThanOrEqualTo(1f / 255f)
    assertThat(abs(actual.alpha - expected.alpha), "alpha: $actual").isLessThanOrEqualTo(1f / 255f)
  }

  companion object {
    private val MIXED_SOURCE = """
uniform shader content; uniform shader aux;
uniform int mode; uniform float scalar; uniform float2 pair; uniform float4 vector; layout(color) uniform half4 tint;
half4 main(float2 p) {
  bool configured = mode == 1 && scalar == 0.25 && pair == float2(0.5, 0.75) && vector == float4(1, 0, 0, 1) && (abs(tint.r) < 0.001 && abs(tint.g) < 0.001 && abs(tint.b - 1) < 0.001 && abs(tint.a - 1) < 0.001);
  bool omitted = mode == 0 && scalar == 0 && pair == float2(0) && vector == float4(0) && tint == half4(0);
  return configured || omitted ? aux.eval(p) : content.eval(p);
}
    """.trimIndent()
    private const val THREE_COLORS = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == 1 ? half4(0, 1, 0, 1) : mode == -1 ? half4(0, 0, 1, 1) : content.eval(p); }"
    private const val MODE_SOURCE = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == 1 ? half4(0, 1, 0, 1) : content.eval(p); }"
    private const val TRANSPARENT_SOURCE = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == 0 ? half4(0) : content.eval(p); }"
  }
}
