// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.liquidglass

import androidx.compose.ui.graphics.TileMode
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.createBlurRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect

@InternalHazeApi
internal actual fun createLiquidGlassRenderEffect(
  params: RuntimeShaderLiquidGlassDelegate.RenderParams,
  uniforms: RuntimeShaderUniformProvider.() -> Unit,
): PlatformRenderEffect {
  val blurEffect = createBlurRenderEffect(
    radiusX = params.blurRadiusPx,
    radiusY = params.blurRadiusPx,
    tileMode = TileMode.Clamp,
  )

  return createRuntimeShaderRenderEffect(
    effect = LIQUID_GLASS_RUNTIME_EFFECT,
    shaderNames = arrayOf("content", "blurredContent"),
    inputs = arrayOf(null, blurEffect),
    uniforms = uniforms,
  )
}

private val LIQUID_GLASS_RUNTIME_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(LiquidGlassShaders.build(hasBlurredContent = true))
}
