// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.liquidglass

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TileMode
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.createBlurRenderEffect
import dev.chrisbanes.haze.createProgressiveBlurRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@InternalHazeApi
internal actual fun createLiquidGlassRenderEffects(
  params: RuntimeShaderLiquidGlassDelegate.RenderParams,
  uniforms: RuntimeShaderUniformProvider.() -> Unit,
): LiquidGlassRenderEffects {
  val blurEffect = params.createBlurRenderEffect()

  val blurredUnderlay = createRuntimeShaderRenderEffect(
    effect = LIQUID_GLASS_OUTPUT_MASK_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(blurEffect),
  ) {
    setMaskUniforms(params)
  }

  val rawOverlay = createRuntimeShaderRenderEffect(
    effect = LIQUID_GLASS_OVERLAY_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
    uniforms = uniforms,
  )

  val maskedOverlay = createRuntimeShaderRenderEffect(
    effect = LIQUID_GLASS_OUTPUT_MASK_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(rawOverlay),
  ) {
    setMaskUniforms(params)
  }

  return LiquidGlassRenderEffects(
    overlay = maskedOverlay,
    underlay = blurredUnderlay,
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

private val LIQUID_GLASS_OVERLAY_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(
    LiquidGlassShaders.build(
      contentMode = LiquidGlassShaders.ContentMode.OverlayWithExternalUnderlay,
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
