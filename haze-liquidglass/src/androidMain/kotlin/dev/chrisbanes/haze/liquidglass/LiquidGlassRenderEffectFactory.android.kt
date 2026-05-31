// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.liquidglass

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.TileMode
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.createBlurRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@InternalHazeApi
internal actual fun createLiquidGlassRenderEffect(
  params: RuntimeShaderLiquidGlassDelegate.RenderParams,
  uniforms: RuntimeShaderUniformProvider.() -> Unit,
): PlatformRenderEffect {
  // Android's RenderEffect.createRuntimeShaderEffect only supports a single content input,
  // so we chain a blur effect with the runtime shader to provide blurred content.
  val blurEffect = createBlurRenderEffect(
    radiusX = params.blurRadiusPx,
    radiusY = params.blurRadiusPx,
    tileMode = TileMode.Clamp,
  )

  return createRuntimeShaderRenderEffect(
    effect = LIQUID_GLASS_RUNTIME_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(blurEffect),
    uniforms = uniforms,
  )
}

private val LIQUID_GLASS_RUNTIME_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(LiquidGlassShaders.build())
}
