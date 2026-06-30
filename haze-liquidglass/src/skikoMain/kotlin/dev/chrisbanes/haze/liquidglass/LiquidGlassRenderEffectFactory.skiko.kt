// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TileMode
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.createBlurRenderEffect
import dev.chrisbanes.haze.createProgressiveBlurRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect

@InternalHazeApi
internal actual fun createLiquidGlassRenderEffect(
  params: RuntimeShaderLiquidGlassDelegate.RenderParams,
  uniforms: RuntimeShaderUniformProvider.() -> Unit,
): PlatformRenderEffect {
  val blurEffect = params.createBlurRenderEffect()

  return createRuntimeShaderRenderEffect(
    effect = LIQUID_GLASS_RUNTIME_EFFECT,
    shaderNames = arrayOf("content", "blurredContent"),
    inputs = arrayOf(null, blurEffect),
    uniforms = uniforms,
  )
}

private fun RuntimeShaderLiquidGlassDelegate.RenderParams.createBlurRenderEffect(): PlatformRenderEffect? {
  val progressiveShader = progressive?.toShader(layerSize)
  return if (progressiveShader != null) {
    createProgressiveBlurRenderEffect(
      blurRadiusPx = blurRadiusPx,
      size = layerSize,
      offset = Offset.Zero,
      mask = progressiveShader,
    )
  } else {
    createBlurRenderEffect(
      radiusX = blurRadiusPx,
      radiusY = blurRadiusPx,
      tileMode = TileMode.Clamp,
    )
  }
}

private val LIQUID_GLASS_RUNTIME_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(LiquidGlassShaders.build(hasBlurredContent = true))
}
