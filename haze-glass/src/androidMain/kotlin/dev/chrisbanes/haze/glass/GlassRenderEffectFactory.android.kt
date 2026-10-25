// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.createBlendRenderEffect
import dev.chrisbanes.haze.createColorFilterRenderEffect
import dev.chrisbanes.haze.toAndroidColorLong
import dev.chrisbanes.haze.wrapRuntimeShaderConstruction

@RequiresApi(Build.VERSION_CODES.S)
internal actual fun createGlassDepthInputRenderEffect(
  sharp: PlatformRenderEffect?,
  blur: PlatformRenderEffect?,
  depth: Float,
): PlatformRenderEffect? {
  if (blur == null || depth <= 0.0001f) return null
  if (depth >= 0.9999f) return blur

  return wrapRuntimeShaderConstruction {
    fun scaledInput(scale: Float, input: PlatformRenderEffect?): PlatformRenderEffect {
      val matrix = ColorMatrix().apply {
        // Color filters operate on straight RGB and premultiply the result. Scaling both RGB and
        // alpha would therefore apply the factor twice to premultiplied color. Scale alpha only to
        // match Canvas layer alpha, which scales premultiplied RGBA once.
        setScale(1f, 1f, 1f, scale)
      }
      return createColorFilterRenderEffect(ColorMatrixColorFilter(matrix), input)
    }

    createBlendRenderEffect(
      blendMode = BlendMode.Plus,
      background = scaledInput(1f - depth, sharp),
      foreground = scaledInput(depth, blur),
    )
  }
}

internal actual val supportsFusedGlassRenderEffect: Boolean = true

internal actual fun DrawScope.supportsGlassRimBrush(): Boolean =
  drawContext.canvas.nativeCanvas.isHardwareAccelerated

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createGlassRimBrushProvider(merged: Boolean): GlassRimBrushProvider? =
  (if (merged) GlassDirectDrawPrograms.mergedRim else GlassDirectDrawPrograms.rim)
    ?.let { AndroidGlassRimBrushProvider(it.shader, it.brush) }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AndroidGlassRimBrushProvider(
  internal val shader: RuntimeShader,
  private val brush: Brush,
) : GlassRimBrushProvider {
  private val uniforms = GlassRimUniformProvider(shader)

  override fun invoke(key: GlassRimEffectKey): Brush {
    uniforms.setRimUniforms(key)
    return brush
  }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal actual fun createGlassMergedBackgroundShaderProvider(): GlassMergedBackgroundShaderProvider? =
  GlassDirectDrawPrograms.mergedBackground?.let(::AndroidGlassMergedBackgroundShaderProvider)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AndroidGlassMergedBackgroundShaderProvider(
  internal val shader: RuntimeShader,
) : GlassMergedBackgroundShaderProvider {
  private val uniforms = GlassRimUniformProvider(shader)

  override fun invoke(materialOrigin: Offset, field: GlassMergedField): Shader {
    uniforms.setFloatUniform("materialOrigin", materialOrigin.x, materialOrigin.y)
    uniforms.setMergedFieldUniforms(field, includeBackground = true)
    return shader
  }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GlassRimProgram(val shader: RuntimeShader) {
  val brush = ShaderBrush(shader)
}

// One compiled program per variant, shared by every provider. That is safe only because each
// provider is invoked inside the recorded draw that uses its result, which snapshots the uniforms,
// or passed straight to RenderEffect.createShaderEffect. Never invoke one outside such a call.
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object GlassDirectDrawPrograms {
  val rim by lazyGlassProgram { GlassRimProgram(RuntimeShader(GlassShaders.buildRim(false))) }
  val mergedRim by lazyGlassProgram { GlassRimProgram(RuntimeShader(GlassShaders.buildRim(true))) }
  val mergedBackground by lazyGlassProgram { RuntimeShader(GlassShaders.buildMergedBackground()) }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GlassRimUniformProvider(private val shader: RuntimeShader) : RuntimeShaderUniformProvider {
  override fun setColorUniform(name: String, color: Color) = shader.setColorUniform(name, color.toAndroidColorLong())

  override fun setFloatUniform(name: String, value: Float) = shader.setFloatUniform(name, value)

  override fun setFloatUniform(name: String, value1: Float, value2: Float) =
    shader.setFloatUniform(name, value1, value2)

  override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) =
    shader.setFloatUniform(name, value1, value2, value3, value4)

  override fun setFloatUniform(name: String, values: FloatArray) = shader.setFloatUniform(name, values)

  override fun setIntUniform(name: String, value: Int) = shader.setIntUniform(name, value)

  override fun setChildShader(name: String, shader: Shader) = this.shader.setInputShader(name, shader)
}
