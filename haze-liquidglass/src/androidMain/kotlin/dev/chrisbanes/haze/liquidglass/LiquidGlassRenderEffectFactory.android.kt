// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.liquidglass

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.TileMode
import dev.chrisbanes.haze.HazeBlendMode
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.createBlendRenderEffect
import dev.chrisbanes.haze.createBlurRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import dev.chrisbanes.haze.then

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

  val underlayBase = createRuntimeShaderRenderEffect(
    effect = LIQUID_GLASS_BLUR_UNDERLAY_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  ) {
    setUnderlayUniforms(params)
  }
  val underlay = blurEffect?.let(underlayBase::then) ?: underlayBase

  val overlay = createRuntimeShaderRenderEffect(
    effect = LIQUID_GLASS_RUNTIME_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
    uniforms = uniforms,
  )

  val blended = createBlendRenderEffect(
    blendMode = HazeBlendMode.SrcOver,
    background = underlay,
    foreground = overlay,
  )

  return createRuntimeShaderRenderEffect(
    effect = LIQUID_GLASS_OUTPUT_MASK_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(blended),
  ) {
    setMaskUniforms(params)
  }
}

private val LIQUID_GLASS_RUNTIME_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(LiquidGlassShaders.build())
}

private val LIQUID_GLASS_BLUR_UNDERLAY_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(LiquidGlassShaders.buildBlurUnderlay())
}

private val LIQUID_GLASS_OUTPUT_MASK_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(LiquidGlassShaders.buildOutputMask())
}

private fun RuntimeShaderUniformProvider.setUnderlayUniforms(
  params: RuntimeShaderLiquidGlassDelegate.RenderParams,
) {
  setFloatUniform("layerSize", params.layerSize.width, params.layerSize.height)
  setFloatUniform("contrast", params.contrast)
  setFloatUniform("whitePoint", params.whitePoint)
  setFloatUniform("chromaMultiplier", params.chromaMultiplier)
  setFloatUniform(
    "tintColor",
    params.tint.red,
    params.tint.green,
    params.tint.blue,
    params.tint.alpha,
  )
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
