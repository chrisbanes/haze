// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.skiaShader
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

internal actual fun createGlassDepthInputRenderEffect(
  sharp: PlatformRenderEffect?,
  blur: PlatformRenderEffect?,
  depth: Float,
): PlatformRenderEffect? = null

internal actual val supportsFusedGlassRenderEffect: Boolean = false

internal actual fun createGlassRimBrushProvider(): GlassRimBrushProvider? {
  val builder = runCatching {
    RuntimeShaderBuilder(RuntimeEffect.makeForShader(GlassShaders.buildRim()))
  }.getOrNull() ?: return null
  val uniforms = GlassRimUniformProvider(builder)
  return GlassRimBrushProvider { key ->
    uniforms.setRimUniforms(key)
    runCatching {
      // Skia snapshots the uniforms, so each recorded rim needs its own shader.
      ShaderBrush(builder.makeShader().asComposeShader())
    }.getOrNull()
  }
}

internal actual fun DrawScope.drawGlassRimWithBrush(brush: Brush): Boolean =
  // Only this optional built-in brush draw is guarded; caller content is drawn separately.
  runCatching { drawRect(brush) }.isSuccess

private class GlassRimUniformProvider(private val builder: RuntimeShaderBuilder) : RuntimeShaderUniformProvider {
  override fun setFloatUniform(name: String, value: Float) = builder.uniform(name, value)

  override fun setFloatUniform(name: String, value1: Float, value2: Float) = builder.uniform(name, value1, value2)

  override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) =
    builder.uniform(name, value1, value2, value3, value4)

  override fun setIntUniform(name: String, value: Int) = builder.uniform(name, value)

  override fun setChildShader(name: String, shader: Shader) = builder.child(name, shader.skiaShader)
}
