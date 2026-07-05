// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.asComposeRenderEffect

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class RuntimeShaderLiquidGlassDelegate(
  private val effect: LiquidGlassVisualEffect,
) : LiquidGlassVisualEffect.Delegate, RetainedOutputDelegate {
  private var renderEffects: LiquidGlassRenderEffects? = null
  private var lastParams: RenderParams? = null
  private var contentLayer: GraphicsLayer? = null
  private var underlayContentLayer: GraphicsLayer? = null
  private var compositedContentLayer: GraphicsLayer? = null
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

      val params = buildRenderParams(
        context = context,
        scaleFactor = scaleFactor,
        layerSize = layerSize,
      )
      val currentRenderEffects = getRenderEffects(params)
      val needsUnderlayLayer = shouldDrawUnderlay(params, currentRenderEffects)
      val needsCompositedLayer = shouldDrawCompositedLayer(params, currentRenderEffects)
      val retainedUnderlayLayer = underlayContentLayer
        ?.takeUnless { it.isReleased }
        ?.takeIf { retainedOutputAvailable }
        ?.takeIf { needsUnderlayLayer }
      val retainedCompositedLayer = compositedContentLayer
        ?.takeUnless { it.isReleased }
        ?.takeIf { retainedOutputAvailable }
        ?.takeIf { needsCompositedLayer }

      if (needsUnderlayLayer && retainedUnderlayLayer == null) {
        retainedOutputAvailable = false
        return
      }
      if (needsCompositedLayer && retainedCompositedLayer == null) {
        retainedOutputAvailable = false
        return
      }

      drawRetainedLayer(
        layer = retainedLayer,
        underlayLayer = retainedUnderlayLayer,
        compositedLayer = retainedCompositedLayer,
        context = context,
        scaleFactor = scaleFactor,
        params = params,
        renderEffects = currentRenderEffects,
        clipToNodeBounds = clipToNodeBounds,
      )
      return
    }

    val params = buildRenderParams(
      context = context,
      scaleFactor = scaleFactor,
      layerSize = layerSize,
    )
    val currentRenderEffects = getRenderEffects(params)
    val needsUnderlayLayer = shouldDrawUnderlay(params, currentRenderEffects)
    val needsCompositedLayer = shouldDrawCompositedLayer(params, currentRenderEffects)

    if (contentLayer == null || contentLayer!!.isReleased || lastScaledLayerSize != currentScaledSize) {
      graphicsContext = context.requireGraphicsContext()
      contentLayer?.let { graphicsContext!!.releaseGraphicsLayer(it) }
      underlayContentLayer?.let { graphicsContext!!.releaseGraphicsLayer(it) }
      compositedContentLayer?.let { graphicsContext!!.releaseGraphicsLayer(it) }
      contentLayer = graphicsContext!!.createGraphicsLayer()
      underlayContentLayer = if (needsUnderlayLayer) {
        graphicsContext!!.createGraphicsLayer()
      } else {
        null
      }
      compositedContentLayer = if (needsCompositedLayer) {
        graphicsContext!!.createGraphicsLayer()
      } else {
        null
      }
      lastScaledLayerSize = currentScaledSize
      retainedOutputAvailable = false
    } else if (needsUnderlayLayer && underlayContentLayer?.isReleased != false) {
      graphicsContext = graphicsContext ?: context.requireGraphicsContext()
      underlayContentLayer = graphicsContext!!.createGraphicsLayer()
    } else if (!needsUnderlayLayer && underlayContentLayer != null) {
      underlayContentLayer?.let { graphicsContext?.releaseGraphicsLayer(it) }
      underlayContentLayer = null
    }
    if (needsCompositedLayer && compositedContentLayer?.isReleased != false) {
      graphicsContext = graphicsContext ?: context.requireGraphicsContext()
      compositedContentLayer = graphicsContext!!.createGraphicsLayer()
    } else if (!needsCompositedLayer && compositedContentLayer != null) {
      compositedContentLayer?.let { graphicsContext?.releaseGraphicsLayer(it) }
      compositedContentLayer = null
    }

    val layer = createScaledContentLayer(
      context = context,
      scaleFactor = scaleFactor,
      layerSize = context.layerSize,
      layerOffset = context.layerOffset,
      existingLayer = contentLayer,
      backgroundColor = Color.Transparent,
    ) ?: return

    val underlayLayer = if (needsUnderlayLayer) {
      createScaledContentLayer(
        context = context,
        scaleFactor = scaleFactor,
        layerSize = context.layerSize,
        layerOffset = context.layerOffset,
        existingLayer = underlayContentLayer,
        backgroundColor = Color.Transparent,
      ) ?: return
    } else {
      null
    }
    retainedOutputAvailable = true

    drawRetainedLayer(
      layer = layer,
      underlayLayer = underlayLayer,
      compositedLayer = compositedContentLayer,
      context = context,
      scaleFactor = scaleFactor,
      params = params,
      renderEffects = currentRenderEffects,
      clipToNodeBounds = clipToNodeBounds,
    )
  }

  private fun DrawScope.drawRetainedLayer(
    layer: GraphicsLayer,
    underlayLayer: GraphicsLayer?,
    compositedLayer: GraphicsLayer?,
    context: VisualEffectContext,
    scaleFactor: Float,
    params: RenderParams,
    renderEffects: LiquidGlassRenderEffects,
    clipToNodeBounds: Boolean,
  ) {
    layer.clip = clipToNodeBounds
    underlayLayer?.clip = clipToNodeBounds
    compositedLayer?.clip = clipToNodeBounds
    drawScaledContent(
      offset = -context.layerOffset,
      scaledSize = size * scaleFactor,
      clip = clipToNodeBounds,
    ) {
      if (shouldDrawCompositedLayer(params, renderEffects)) {
        val currentCompositedLayer = compositedLayer
        val outputMask = renderEffects.outputMask
        if (currentCompositedLayer != null && outputMask != null) {
          currentCompositedLayer.renderEffect = outputMask.asComposeRenderEffect()
          currentCompositedLayer.alpha = effect.alpha
          currentCompositedLayer.record(size = params.layerSize.roundToIntSize()) {
            if (shouldDrawUnderlay(params, renderEffects)) {
              val currentUnderlayLayer = underlayLayer
              val underlay = renderEffects.underlay
              if (currentUnderlayLayer != null && underlay != null) {
                currentUnderlayLayer.renderEffect = underlay.asComposeRenderEffect()
                currentUnderlayLayer.alpha = 1f
                drawLayer(currentUnderlayLayer)
              }
            }

            layer.renderEffect = renderEffects.overlay.asComposeRenderEffect()
            layer.alpha = 1f
            drawLayer(layer)
          }
          drawLayer(currentCompositedLayer)
        }
      } else {
        if (shouldDrawUnderlay(params, renderEffects)) {
          val currentUnderlayLayer = underlayLayer
          val underlay = renderEffects.underlay
          if (currentUnderlayLayer != null && underlay != null) {
            currentUnderlayLayer.renderEffect = underlay.asComposeRenderEffect()
            currentUnderlayLayer.alpha = effect.alpha
            drawLayer(currentUnderlayLayer)
          }
        }

        layer.renderEffect = renderEffects.overlay.asComposeRenderEffect()
        layer.alpha = effect.alpha
        drawLayer(layer)
      }
    }
  }

  override fun canDrawRetainedOutput(): Boolean {
    return retainedOutputAvailable && contentLayer?.isReleased == false
  }

  override fun clearRetainedOutput() {
    retainedOutputAvailable = false
  }

  override fun detach() {
    releaseRetainedResources()
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    if (level.severity >= TrimMemoryLevel.MODERATE.severity) {
      releaseRetainedResources()
      context.invalidateDraw()
    }
  }

  private fun releaseRetainedResources() {
    contentLayer?.let { layer ->
      graphicsContext?.releaseGraphicsLayer(layer)
    }
    underlayContentLayer?.let { layer ->
      graphicsContext?.releaseGraphicsLayer(layer)
    }
    compositedContentLayer?.let { layer ->
      graphicsContext?.releaseGraphicsLayer(layer)
    }
    contentLayer = null
    underlayContentLayer = null
    compositedContentLayer = null
    lastScaledLayerSize = null
    graphicsContext = null
    renderEffects = null
    lastParams = null
    retainedOutputAvailable = false
  }

  private fun DrawScope.buildRenderParams(
    context: VisualEffectContext,
    scaleFactor: Float,
    layerSize: Size,
  ): RenderParams {
    val density = context.requireDensity()
    val layoutDirection = context.currentValueOf(LocalLayoutDirection)
    val layerRadii = effect.shape.toCornerRadiiPx(
      layerSize = layerSize,
      density = density,
      layoutDirection = layoutDirection,
    )
    return RenderParams(
      layerSize = layerSize,
      refractionStrength = effect.refractionStrength.coerceIn(0f, 1f),
      specularIntensity = effect.specularIntensity.coerceIn(0f, 1f),
      depth = effect.depth.coerceIn(0f, 1f),
      ambientResponse = effect.ambientResponse.coerceIn(0f, 1f),
      tint = effect.tint,
      edgeSoftnessPx = with(density) { effect.edgeSoftness.toPx() },
      blurRadiusPx = with(density) { effect.blurRadius.toPx() },
      progressive = effect.progressive,
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
  }

  private fun getRenderEffects(params: RenderParams): LiquidGlassRenderEffects {
    if (params != lastParams || renderEffects == null) {
      renderEffects = buildRenderEffects(params)
      lastParams = params
    }
    return checkNotNull(renderEffects)
  }

  private fun shouldDrawUnderlay(
    params: RenderParams,
    renderEffects: LiquidGlassRenderEffects,
  ): Boolean {
    return params.depth > 0f && renderEffects.underlay != null
  }

  private fun shouldDrawCompositedLayer(
    params: RenderParams,
    renderEffects: LiquidGlassRenderEffects,
  ): Boolean {
    return renderEffects.outputMask != null &&
      (params.edgeSoftnessPx > 0f || !params.cornerRadii.isZero())
  }

  private fun buildRenderEffects(params: RenderParams): LiquidGlassRenderEffects {
    return createLiquidGlassRenderEffects(params) {
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
    }
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
    val progressive: HazeProgressive?,
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
internal data class LiquidGlassRenderEffects(
  val overlay: PlatformRenderEffect,
  val underlay: PlatformRenderEffect? = null,
  val outputMask: PlatformRenderEffect? = null,
)

@OptIn(InternalHazeApi::class)
internal expect fun createLiquidGlassRenderEffects(
  params: RuntimeShaderLiquidGlassDelegate.RenderParams,
  uniforms: RuntimeShaderUniformProvider.() -> Unit,
): LiquidGlassRenderEffects
