// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.createMutableRuntimeShaderRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.test.Test

class RuntimeShaderIntegerUniformJvmTest {
  @Test
  fun modeOne_rendersGreen() = assertThat(draw(effect(1))).isEqualTo(Color.Green)

  @Test
  fun modeZero_rendersRedContent() = assertThat(draw(effect(0))).isEqualTo(Color.Red)

  @Test
  fun exactInteger_negativeOne() {
    val value = -1
    val literal = if (value == Int.MIN_VALUE) "(-2147483647 - 1)" else value.toString()
    val source = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == $literal ? half4(0, 1, 0, 1) : content.eval(p); }"
    val effect = createRuntimeShaderRenderEffect(createRuntimeEffect(source), arrayOf("content"), arrayOf(null)) { setIntUniform("mode", value) }
    assertThat(draw(effect)).isEqualTo(Color.Green)
  }

  @Test
  fun exactInteger_negativeBeyondFloat() {
    val value = -16777217
    val literal = if (value == Int.MIN_VALUE) "(-2147483647 - 1)" else value.toString()
    val source = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == $literal ? half4(0, 1, 0, 1) : content.eval(p); }"
    val effect = createRuntimeShaderRenderEffect(createRuntimeEffect(source), arrayOf("content"), arrayOf(null)) { setIntUniform("mode", value) }
    assertThat(draw(effect)).isEqualTo(Color.Green)
  }

  @Test
  fun exactInteger_positiveBeyondFloat() {
    val value = 16777217
    val literal = if (value == Int.MIN_VALUE) "(-2147483647 - 1)" else value.toString()
    val source = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == $literal ? half4(0, 1, 0, 1) : content.eval(p); }"
    val effect = createRuntimeShaderRenderEffect(createRuntimeEffect(source), arrayOf("content"), arrayOf(null)) { setIntUniform("mode", value) }
    assertThat(draw(effect)).isEqualTo(Color.Green)
  }

  @Test
  fun exactInteger_minimum() {
    val value = Int.MIN_VALUE
    val literal = if (value == Int.MIN_VALUE) "(-2147483647 - 1)" else value.toString()
    val source = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == $literal ? half4(0, 1, 0, 1) : content.eval(p); }"
    val effect = createRuntimeShaderRenderEffect(createRuntimeEffect(source), arrayOf("content"), arrayOf(null)) { setIntUniform("mode", value) }
    assertThat(draw(effect)).isEqualTo(Color.Green)
  }

  @Test
  fun exactInteger_maximum() {
    val value = Int.MAX_VALUE
    val literal = if (value == Int.MIN_VALUE) "(-2147483647 - 1)" else value.toString()
    val source = "uniform shader content; uniform int mode; half4 main(float2 p) { return mode == $literal ? half4(0, 1, 0, 1) : content.eval(p); }"
    val effect = createRuntimeShaderRenderEffect(createRuntimeEffect(source), arrayOf("content"), arrayOf(null)) { setIntUniform("mode", value) }
    assertThat(draw(effect)).isEqualTo(Color.Green)
  }

  @Test
  fun sharedSource_resetsOmittedIntegerAndPreservesSnapshots() {
    val source = createRuntimeEffect(THREE_COLORS)
    val a = createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) { setIntUniform("mode", 1) }
    val b = createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) {}
    val c = createRuntimeShaderRenderEffect(source, arrayOf("content"), arrayOf(null)) { setIntUniform("mode", -1) }
    listOf(a, b, c, a).zip(listOf(Color.Green, Color.Red, Color.Blue, Color.Green)).forEach { (effect, expected) -> assertThat(draw(effect)).isEqualTo(expected) }
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
    listOf(old, current, independent, retained, inputs).zip(listOf(Color.Green, Color.Blue, Color.Red, Color.Blue, Color.Green)).forEach { (effect, expected) -> assertThat(draw(effect)).isEqualTo(expected) }
  }

  @Test
  fun mixedUniforms_resetIndependentEffectsAndPreserveSnapshot() {
    val childEffect = RuntimeEffect.makeForShader("half4 main(float2 p) { return half4(0, 1, 0, 1); }")
    val builder = RuntimeShaderBuilder(childEffect)
    val nativeChild = builder.makeShader()
    val child = nativeChild.asComposeShader()
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
      assertThat(draw(a)).isEqualTo(Color.Green)
      assertThat(draw(b)).isEqualTo(Color.Transparent)
      assertThat(draw(a)).isEqualTo(Color.Green)
    } finally {
      nativeChild.close()
      builder.close()
      childEffect.close()
    }
  }

  private fun effect(mode: Int) = createRuntimeShaderRenderEffect(
    createRuntimeEffect("uniform shader content; uniform int mode; half4 main(float2 p) { return mode == 1 ? half4(0, 1, 0, 1) : content.eval(p); }"),
    arrayOf("content"),
    arrayOf(null),
  ) { setIntUniform("mode", mode) }

  private fun draw(effect: PlatformRenderEffect): Color {
    val image = ImageBitmap(8, 8)
    val bounds = Rect(0f, 0f, 8f, 8f)
    with(Canvas(image)) {
      saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = effect })
      drawRect(bounds, Paint().apply { color = Color.Red })
      restore()
    }
    return image.toPixelMap()[4, 4]
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
  }
}
