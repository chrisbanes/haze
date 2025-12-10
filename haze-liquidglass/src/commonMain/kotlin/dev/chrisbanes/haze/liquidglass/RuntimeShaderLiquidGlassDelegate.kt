// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.asComposeRenderEffect
import dev.chrisbanes.haze.createBlurImageFilter
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class RuntimeShaderLiquidGlassDelegate(
  private val effect: LiquidGlassVisualEffect,
) : LiquidGlassVisualEffect.Delegate {
  private var renderEffect: RenderEffect? = null
  private var lastParams: RenderParams? = null

  override fun DrawScope.draw(context: VisualEffectContext) {
    createAndDrawScaledContentLayer(context) {
      val params = RenderParams(
        layerSize = context.layerSize * effect.calculateInputScaleFactor(context.inputScale),
        refractionStrength = effect.refractionStrength.coerceIn(0f, 1f),
        specularIntensity = effect.specularIntensity.coerceIn(0f, 1f),
        depth = effect.depth.coerceIn(0f, 1f),
        ambientResponse = effect.ambientResponse.coerceIn(0f, 1f),
        tint = effect.tint,
        edgeSoftnessPx = with(context.requireDensity()) { effect.edgeSoftness.toPx() },
        blurRadiusPx = with(context.requireDensity()) { effect.blurRadius.toPx() },
        lightPosition = effect.lightPosition.takeOrElse {
          context.layerSize.center * effect.calculateInputScaleFactor(context.inputScale)
        },
      )

      if (params != lastParams || renderEffect == null) {
        renderEffect = buildRenderEffect(params)
        lastParams = params
      }

      it.renderEffect = renderEffect
      it.alpha = effect.alpha
      drawLayer(it)
    }
  }

  private fun buildRenderEffect(params: RenderParams): RenderEffect {
    // Create blur effect for the blurred content input
    val blurEffect = createBlurImageFilter(
      radiusX = params.blurRadiusPx,
      radiusY = params.blurRadiusPx,
      tileMode = TileMode.Clamp,
    )

    return createRuntimeShaderRenderEffect(
      effect = LIQUID_GLASS_RUNTIME_EFFECT,
      shaderNames = arrayOf("content", "blurredContent"),
      inputs = arrayOf(null, blurEffect),
    ) {
      setFloatUniform("layerSize", params.layerSize.width, params.layerSize.height)
      setFloatUniform("refractionStrength", params.refractionStrength)
      setFloatUniform("specularIntensity", params.specularIntensity)
      setFloatUniform("depth", params.depth)
      setFloatUniform("ambientResponse", params.ambientResponse)
      setFloatUniform("edgeSoftness", params.edgeSoftnessPx)
      setFloatUniform("lightPosition", params.lightPosition.x, params.lightPosition.y)
      setFloatUniform(
        "tintColor",
        params.tint.red,
        params.tint.green,
        params.tint.blue,
        params.tint.alpha,
      )
    }.asComposeRenderEffect()
  }

  private data class RenderParams(
    val layerSize: Size,
    val refractionStrength: Float,
    val specularIntensity: Float,
    val depth: Float,
    val ambientResponse: Float,
    val tint: Color,
    val edgeSoftnessPx: Float,
    val blurRadiusPx: Float,
    val lightPosition: Offset,
  )

  private companion object {
    val LIQUID_GLASS_RUNTIME_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
      createRuntimeEffect(LiquidGlassShaders.LIQUID_GLASS_SKSL)
    }
  }
}
