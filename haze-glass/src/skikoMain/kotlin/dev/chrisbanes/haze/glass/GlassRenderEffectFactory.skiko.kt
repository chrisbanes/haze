// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

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

internal actual fun createGlassRimBrushProvider(merged: Boolean): GlassRimBrushProvider? {
  val builder = runCatchingGlassRim {
    RuntimeShaderBuilder(RuntimeEffect.makeForShader(GlassShaders.buildRim(merged)))
  }.getOrNull() ?: return null
  val uniforms = builder.asUniformProvider()
  return GlassRimBrushProvider { key ->
    uniforms.setRimUniforms(key)
    runCatchingGlassRim {
      // Skia snapshots the uniforms, so each recorded rim needs its own shader.
      ShaderBrush(builder.makeShader().asComposeShader())
    }.getOrNull()
  }
}

internal actual fun DrawScope.supportsGlassRimBrush(): Boolean = true

internal actual fun createGlassMergedBackgroundShaderProvider(): GlassMergedBackgroundShaderProvider? {
  val builder = runCatchingGlassRim {
    RuntimeShaderBuilder(RuntimeEffect.makeForShader(GlassShaders.buildMergedBackground()))
  }.getOrNull() ?: return null
  val uniforms = builder.asUniformProvider()
  return GlassMergedBackgroundShaderProvider { materialOrigin, field ->
    uniforms.setFloatUniform("materialOrigin", materialOrigin.x, materialOrigin.y)
    uniforms.setMergedFieldUniforms(field, includeBackground = true)
    runCatchingGlassRim { builder.makeShader().asComposeShader() }.getOrNull()
  }
}
