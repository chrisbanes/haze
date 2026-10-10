// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

/**
 * Probes whether `uniform float4 rects[2]` can be set with Skiko's
 * `RuntimeShaderBuilder.uniform(String, FloatArray)` and read back by the shader.
 */
class UniformArrayProbeJvmTest {
  @Test
  fun floatArrayUniform_isReadByShader() {
    // 8 floats: rects[0] = (3, 0, 0, 0), rects[1] = (0, 0, 7, 0)
    val matching = floatArrayOf(3f, 0f, 0f, 0f, 0f, 0f, 7f, 0f)
    val wrong = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 2f, 0f)

    val effect = RuntimeEffect.makeForShader(SOURCE)
    assertThat(draw(effect, matching)).isEqualTo(GREEN)
    assertThat(draw(effect, wrong)).isEqualTo(RED)
  }

  private fun draw(effect: RuntimeEffect, rects: FloatArray): Int {
    val content = Shader.makeColor(RED)
    val builder = RuntimeShaderBuilder(effect)
    assertThat(runCatching { builder.uniform("rects", rects) }.exceptionOrNull()).isNull()
    builder.child("content", content)
    val shader = builder.makeShader()
    val bitmap = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(8, 8)) }
    Canvas(bitmap).use { it.drawPaint(Paint().apply { this.shader = shader }) }
    val pixel = bitmap.getColor(4, 4)
    shader.close()
    builder.close()
    content.close()
    bitmap.close()
    return pixel
  }

  companion object {
    private val RED = 0xFFFF0000.toInt()
    private val GREEN = 0xFF00FF00.toInt()
    private const val SOURCE = "uniform shader content; uniform float4 rects[2]; " +
      "half4 main(float2 p) { return rects[1].z == 7.0 && rects[0].x == 3.0 ? half4(0, 1, 0, 1) : content.eval(p); }"
  }
}
