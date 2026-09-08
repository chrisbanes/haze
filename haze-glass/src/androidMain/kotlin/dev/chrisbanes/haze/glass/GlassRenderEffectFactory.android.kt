// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import android.graphics.BlendMode
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.RuntimeShaderUniformProvider

@RequiresApi(Build.VERSION_CODES.S)
internal actual fun createGlassDepthInputRenderEffect(
  sharp: PlatformRenderEffect?,
  blur: PlatformRenderEffect?,
  depth: Float,
): PlatformRenderEffect? {
  if (blur == null || depth <= 0.0001f) return null
  if (depth >= 0.9999f) return blur

  return wrapGlassRuntimeEffectConstruction {
    fun scaledInput(scale: Float, input: RenderEffect? = null): RenderEffect {
      val matrix = ColorMatrix().apply {
        // Color filters operate on straight RGB and premultiply the result. Scaling both RGB and
        // alpha would therefore apply the factor twice to premultiplied color. Scale alpha only to
        // match Canvas layer alpha, which scales premultiplied RGBA once.
        setScale(1f, 1f, 1f, scale)
      }
      val filter = ColorMatrixColorFilter(matrix)
      return if (input != null) {
        RenderEffect.createColorFilterEffect(filter, input)
      } else {
        RenderEffect.createColorFilterEffect(filter)
      }
    }

    RenderEffect.createBlendModeEffect(
      scaledInput(1f - depth, sharp),
      scaledInput(depth, blur),
      BlendMode.PLUS,
    )
  }
}

internal actual val supportsFusedGlassRenderEffect: Boolean = true

internal actual fun DrawScope.drawGlassRimWithBrush(brush: Brush): Boolean {
  if (!drawContext.canvas.nativeCanvas.isHardwareAccelerated) return false
  return try {
    drawRect(brush)
    true
  } catch (_: RuntimeException) {
    // Only this optional built-in brush draw is guarded; caller content is drawn separately.
    false
  }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createGlassRimBrushProvider(): ((GlassRimEffectKey) -> Brush)? {
  val shader = try {
    RuntimeShader(GlassShaders.buildRim())
  } catch (_: RuntimeException) {
    return null
  }
  val brush = ShaderBrush(shader)
  val uniforms = GlassRimUniformProvider(shader)
  return { key ->
    uniforms.setRimUniforms(key)
    brush
  }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GlassRimUniformProvider(private val shader: RuntimeShader) : RuntimeShaderUniformProvider {
  override fun setFloatUniform(name: String, value: Float) = shader.setFloatUniform(name, value)

  override fun setFloatUniform(name: String, value1: Float, value2: Float) =
    shader.setFloatUniform(name, value1, value2)

  override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) =
    shader.setFloatUniform(name, value1, value2, value3, value4)

  override fun setIntUniform(name: String, value: Int) = shader.setFloatUniform(name, value.toFloat())

  override fun setChildShader(name: String, shader: Shader) = this.shader.setInputShader(name, shader)
}
