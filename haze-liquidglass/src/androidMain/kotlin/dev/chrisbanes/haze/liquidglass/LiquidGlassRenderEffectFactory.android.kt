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
  val blurEffect = createBlurRenderEffect(
    radiusX = params.blurRadiusPx,
    radiusY = params.blurRadiusPx,
    tileMode = TileMode.Clamp,
  )

  val glass = createRuntimeShaderRenderEffect(
    effect = LIQUID_GLASS_RUNTIME_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(blurEffect),
    uniforms = uniforms,
  )

  return createRuntimeShaderRenderEffect(
    effect = LIQUID_GLASS_OUTPUT_MASK_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(glass),
  ) {
    setMaskUniforms(params)
  }
}

private val LIQUID_GLASS_RUNTIME_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(
    LiquidGlassShaders.build(
      contentMode = LiquidGlassShaders.ContentMode.SingleBlurredInput,
    ),
  )
}

private val LIQUID_GLASS_OUTPUT_MASK_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(LiquidGlassShaders.buildOutputMask())
}

private fun RuntimeShaderUniformProvider.setMaskUniforms(
  params: RuntimeShaderLiquidGlassDelegate.RenderParams,
) {
  setFloatUniform("layerSize", params.layerSize.width, params.layerSize.height)
  setFloatUniform("edgeSoftness", params.edgeSoftnessPx)
  setFloatUniform(
    "cornerRadii",
    params.cornerRadii.topLeft,
    params.cornerRadii.topRight,
    params.cornerRadii.bottomRight,
    params.cornerRadii.bottomLeft,
  )
}
