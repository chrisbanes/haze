// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.asComposeRenderEffect

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class RuntimeShaderLiquidGlassDelegate(
  private val effect: LiquidGlassVisualEffect,
) : LiquidGlassVisualEffect.Delegate, RetainedOutputDelegate {
  private var renderEffect: RenderEffect? = null
  private var lastParams: RenderParams? = null
  private var contentLayer: GraphicsLayer? = null
  private var lastScaledLayerSize: Size? = null
  private var graphicsContext: GraphicsContext? = null
  private var retainedOutputAvailable: Boolean = false

  override fun DrawScope.draw(context: VisualEffectContext) {
    val scaleFactor = effect.resolveInputScaleFactor(context.inputScale)
    val layerSize = context.layerSize * scaleFactor
    val clipToNodeBounds = effect.shouldClipToNodeBounds()
    val currentScaledSize = layerSize.roundToIntSize().let {
      Size(it.width.toFloat(), it.height.toFloat())
    }
    val hasDrawableSourceLayers = context.hasDrawableSourceLayers()

    if (!hasDrawableSourceLayers) {
      val retainedLayer = contentLayer
        ?.takeUnless { it.isReleased }
        ?.takeIf { retainedOutputAvailable }
        ?: return

      if (lastScaledLayerSize != currentScaledSize) {
        retainedOutputAvailable = false
        return
      }

      drawRetainedLayer(
        layer = retainedLayer,
        context = context,
        scaleFactor = scaleFactor,
        layerSize = layerSize,
        clipToNodeBounds = clipToNodeBounds,
      )
      return
    }

    if (contentLayer == null || contentLayer!!.isReleased || lastScaledLayerSize != currentScaledSize) {
      graphicsContext = context.requireGraphicsContext()
      contentLayer?.let { graphicsContext!!.releaseGraphicsLayer(it) }
      contentLayer = graphicsContext!!.createGraphicsLayer()
      lastScaledLayerSize = currentScaledSize
      retainedOutputAvailable = false
    }

    val layer = createScaledContentLayer(
      context = context,
      scaleFactor = scaleFactor,
      layerSize = context.layerSize,
      layerOffset = context.layerOffset,
      existingLayer = contentLayer,
      backgroundColor = Color.Transparent,
    ) ?: return
    retainedOutputAvailable = true

    drawRetainedLayer(
      layer = layer,
      context = context,
      scaleFactor = scaleFactor,
      layerSize = layerSize,
      clipToNodeBounds = clipToNodeBounds,
    )
  }

  private fun DrawScope.drawRetainedLayer(
    layer: GraphicsLayer,
    context: VisualEffectContext,
    scaleFactor: Float,
    layerSize: Size,
    clipToNodeBounds: Boolean,
  ) {
    layer.clip = clipToNodeBounds
    drawScaledContent(
      offset = -context.layerOffset,
      scaledSize = size * scaleFactor,
      clip = clipToNodeBounds,
    ) {
      val density = context.requireDensity()
      val layoutDirection = context.currentValueOf(LocalLayoutDirection)
      val layerRadii = effect.shape.toCornerRadiiPx(
        layerSize = layerSize,
        density = density,
        layoutDirection = layoutDirection,
      )
      val params = RenderParams(
        layerSize = layerSize,
        refractionStrength = effect.refractionStrength.coerceIn(0f, 1f),
        specularIntensity = effect.specularIntensity.coerceIn(0f, 1f),
        depth = effect.depth.coerceIn(0f, 1f),
        ambientResponse = effect.ambientResponse.coerceIn(0f, 1f),
        tint = effect.tint,
        edgeSoftnessPx = with(density) { effect.edgeSoftness.toPx() },
        blurRadiusPx = with(density) { effect.blurRadius.toPx() },
        refractionHeightPx = effect.refractionHeight.coerceIn(0f, 1f) * layerSize.minDimension,
        chromaticAberrationStrength = effect.chromaticAberrationStrength.coerceIn(0f, 1f),
        surfaceProfile = effect.surfaceProfile.ordinal.toFloat(),
        chromaticAberrationMode = effect.chromaticAberrationMode.ordinal.toFloat(),
        contrast = effect.contrast.coerceIn(-1f, 1f),
        whitePoint = effect.whitePoint.coerceIn(-1f, 1f),
        chromaMultiplier = effect.chromaMultiplier.coerceIn(0f, 2f),
        refractionScale = effect.refractionScale.coerceAtLeast(0f),
        contentNormalBlend = effect.contentNormalBlend.coerceIn(0f, 1f),
        specularExponent = effect.specularExponent.coerceAtLeast(0f),
        fresnelExponent = effect.fresnelExponent.coerceAtLeast(0f),
        cornerRadii = layerRadii,
        lightPosition = effect.lightPosition.takeOrElse {
          context.layerSize.center * scaleFactor
        },
      )

      if (params != lastParams || renderEffect == null) {
        renderEffect = buildRenderEffect(params)
        lastParams = params
      }

      layer.renderEffect = renderEffect
      layer.alpha = effect.alpha
      drawLayer(layer)
    }
  }

  override fun canDrawRetainedOutput(): Boolean {
    return retainedOutputAvailable && contentLayer?.isReleased == false
  }

  override fun clearRetainedOutput() {
    retainedOutputAvailable = false
  }

  override fun detach() {
    contentLayer?.let { layer ->
      graphicsContext?.releaseGraphicsLayer(layer)
    }
    contentLayer = null
    lastScaledLayerSize = null
    graphicsContext = null
    retainedOutputAvailable = false
  }

  private fun buildRenderEffect(params: RenderParams): RenderEffect {
    return createLiquidGlassRenderEffect(params) {
      setFloatUniform("layerSize", params.layerSize.width, params.layerSize.height)
      setFloatUniform("refractionStrength", params.refractionStrength)
      setFloatUniform("specularIntensity", params.specularIntensity)
      setFloatUniform("depth", params.depth)
      setFloatUniform("ambientResponse", params.ambientResponse)
      setFloatUniform("edgeSoftness", params.edgeSoftnessPx)
      setFloatUniform("refractionHeight", params.refractionHeightPx)
      setFloatUniform("chromaticAberrationStrength", params.chromaticAberrationStrength)
      setFloatUniform("surfaceProfile", params.surfaceProfile)
      setFloatUniform("chromaticAberrationMode", params.chromaticAberrationMode)
      setFloatUniform("contrast", params.contrast)
      setFloatUniform("whitePoint", params.whitePoint)
      setFloatUniform("chromaMultiplier", params.chromaMultiplier)
      setFloatUniform("refractionScale", params.refractionScale)
      setFloatUniform("contentNormalBlend", params.contentNormalBlend)
      setFloatUniform("specularExponent", params.specularExponent)
      setFloatUniform("fresnelExponent", params.fresnelExponent)
      setFloatUniform(
        "cornerRadii",
        params.cornerRadii.topLeft,
        params.cornerRadii.topRight,
        params.cornerRadii.bottomRight,
        params.cornerRadii.bottomLeft,
      )
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

  internal data class RenderParams(
    val layerSize: Size,
    val refractionStrength: Float,
    val specularIntensity: Float,
    val depth: Float,
    val ambientResponse: Float,
    val tint: Color,
    val edgeSoftnessPx: Float,
    val blurRadiusPx: Float,
    val refractionHeightPx: Float,
    val chromaticAberrationStrength: Float,
    val surfaceProfile: Float,
    val chromaticAberrationMode: Float,
    val contrast: Float,
    val whitePoint: Float,
    val chromaMultiplier: Float,
    val refractionScale: Float,
    val contentNormalBlend: Float,
    val specularExponent: Float,
    val fresnelExponent: Float,
    val cornerRadii: CornerRadii,
    val lightPosition: Offset,
  )
}

@OptIn(InternalHazeApi::class)
private fun VisualEffectContext.hasDrawableSourceLayers(): Boolean {
  return areas.any { area ->
    area.contentLayer
      ?.takeUnless { it.isReleased }
      ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 } != null
  }
}

@OptIn(InternalHazeApi::class)
internal expect fun createLiquidGlassRenderEffect(
  params: RuntimeShaderLiquidGlassDelegate.RenderParams,
  uniforms: RuntimeShaderUniformProvider.() -> Unit,
): PlatformRenderEffect
