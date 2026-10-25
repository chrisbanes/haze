// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.asUniformProvider
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

internal actual fun createGlassDepthInputRenderEffect(
  sharp: PlatformRenderEffect?,
  blur: PlatformRenderEffect?,
  depth: Float,
): PlatformRenderEffect? = null

internal actual val supportsFusedGlassRenderEffect: Boolean = false

// Compiled once per process and shared by every provider. A RuntimeEffect is immutable; each provider
// keeps its own RuntimeShaderBuilder. Never close() these: providers only drop their references.
private val GlassRimEffect by lazyGlassProgram { RuntimeEffect.makeForShader(GlassShaders.buildRim(false)) }
private val GlassMergedRimEffect by lazyGlassProgram { RuntimeEffect.makeForShader(GlassShaders.buildRim(true)) }
private val GlassMergedBackgroundEffect by lazyGlassProgram {
  RuntimeEffect.makeForShader(GlassShaders.buildMergedBackground())
}

internal actual fun createGlassRimBrushProvider(merged: Boolean): GlassRimBrushProvider? {
  val effect = (if (merged) GlassMergedRimEffect else GlassRimEffect) ?: return null
  return runCatchingGlassRim { SkiaGlassRimBrushProvider(effect) }.getOrNull()
}

internal class SkiaGlassRimBrushProvider(internal val effect: RuntimeEffect) : GlassRimBrushProvider {
  private val builder = RuntimeShaderBuilder(effect)
  private val uniforms = builder.asUniformProvider()

  override fun invoke(key: GlassRimEffectKey): Brush? {
    uniforms.setRimUniforms(key)
    return runCatchingGlassRim {
      // Skia snapshots the uniforms, so each recorded rim needs its own shader.
      ShaderBrush(builder.makeShader().asComposeShader())
    }.getOrNull()
  }
}

internal actual fun DrawScope.supportsGlassRimBrush(): Boolean = true

internal actual fun createGlassMergedBackgroundShaderProvider(): GlassMergedBackgroundShaderProvider? {
  val effect = GlassMergedBackgroundEffect ?: return null
  return runCatchingGlassRim { SkiaGlassMergedBackgroundShaderProvider(effect) }.getOrNull()
}

internal class SkiaGlassMergedBackgroundShaderProvider(
  internal val effect: RuntimeEffect,
) : GlassMergedBackgroundShaderProvider {
  private val builder = RuntimeShaderBuilder(effect)
  private val uniforms = builder.asUniformProvider()

  override fun invoke(materialOrigin: Offset, field: GlassMergedField): Shader? {
    uniforms.setFloatUniform("materialOrigin", materialOrigin.x, materialOrigin.y)
    uniforms.setMergedFieldUniforms(field, includeBackground = true)
    return runCatchingGlassRim { builder.makeShader().asComposeShader() }.getOrNull()
  }
}
