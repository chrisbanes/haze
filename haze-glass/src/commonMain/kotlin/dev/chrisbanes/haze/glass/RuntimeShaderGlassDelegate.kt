// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.MutableRuntimeShaderRenderEffect
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.asComposeRenderEffect
import dev.chrisbanes.haze.createMutableRuntimeShaderRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class RuntimeShaderGlassDelegate(
  private val effect: GlassVisualEffect,
) : GlassVisualEffect.Delegate, RetainedOutputDelegate {
  private var blurKey: GlassBlurEffectKey? = null
  private var blurEffects: GlassBlurRenderEffects? = null
  private var opticalKey: GlassOpticalEffectKey? = null
  private var opticalEffect: PlatformRenderEffect? = null
  private var refractionDetailKey: GlassRefractionDetailEffectKey? = null
  private var refractionDetailEffect: PlatformRenderEffect? = null
  private var rimKey: GlassRimEffectKey? = null
  private var rimEffect: PlatformRenderEffect? = null
  private var interactionOpticalKey: GlassInteractionOpticalKey? = null
  private var interactionOpticalEffect: MutableRuntimeShaderRenderEffect? = null
  private var interactionDetailKey: GlassInteractionDetailKey? = null
  private var interactionDetailEffect: MutableRuntimeShaderRenderEffect? = null
  private var interactionLightingKey: GlassInteractionLightingKey? = null
  private var interactionLightingEffect: MutableRuntimeShaderRenderEffect? = null
  private var recordedInteractionOpticalLayer: GraphicsLayer? = null
  private var recordedInteractionOpticalInput: GraphicsLayer? = null
  private var recordedInteractionOpticalKey: GlassInteractionOpticalKey? = null
  private var recordedInteractionDetailLayer: GraphicsLayer? = null
  private var recordedInteractionDetailInput: GraphicsLayer? = null
  private var recordedInteractionDetailKey: GlassInteractionDetailKey? = null
  private var recordedInteractionLightingLayer: GraphicsLayer? = null
  private var recordedInteractionLightingKey: GlassInteractionLightingKey? = null
  internal val layers = GlassLayers()
  private var graphicsContext: GraphicsContext? = null
  private var preparedParams: GlassRenderParams? = null
  private var preparedRenderEffects: GlassRenderEffects? = null
  private var preparedInteractionUniforms: GlassInteractionUniforms? = null
  private var preparedInteractionSignals: GlassInteractionSignals? = null
  private var preparedSourceAvailable: Boolean = false
  private var preparedStageAvailability: GlassStageAvailability? = null
  private var retainedOutputAvailable: Boolean = false
  internal var lastSuccessfulSourceSnapshot: GlassSourceSnapshot? = null
    private set
  internal var lastSuccessfulStageInputs: GlassStageInputs? = null
    private set
  private var lastSuccessfulInteractionUniforms: GlassInteractionUniforms? = null
  private var lastSuccessfulInteractionSignals: GlassInteractionSignals? = null
  internal var baseOpticalEffectCreationCount: Int = 0
    private set
  internal var sourceRecordCount: Int = 0
    private set
  internal var interactionFrameCount: Int = 0
    private set

  override fun DrawScope.prepareDraw(context: VisualEffectContext) {
    val scaleFactor = effect.resolveInputScaleFactor(context.inputScale)
    val rawCoordinates = resolveGlassCoordinates(
      layerSize = context.layerSize,
      layerOffset = context.layerOffset,
      materialSize = context.size,
      scaleFactor = scaleFactor,
    )
    if (
      requireDrawableMaterialSize(rawCoordinates.materialSize, ::releaseRetainedResources) == null ||
      requireDrawableMaterialSize(rawCoordinates.sampleSize, ::releaseRetainedResources) == null
    ) {
      return
    }
    val coordinates = rawCoordinates.withRoundedSampleSize()
    if (requireDrawableMaterialSize(coordinates.sampleSize, ::releaseRetainedResources) == null) {
      return
    }
    val params = buildRenderParams(context, coordinates)
    val interactionUniforms = params.interactionUniforms(
      state = effect.interactionRenderState(context),
      radiusFraction = effect.interactionLightRadiusFraction,
    )
    val currentRenderEffects = getRenderEffects(params)
    val scaledSize = coordinates.sampleSize.roundToIntSize()

    val currentGraphicsContext = context.requireGraphicsContext()
    graphicsContext = currentGraphicsContext
    if (layers.scaledSize != scaledSize) {
      layers.release(currentGraphicsContext)
      layers.scaledSize = scaledSize
      clearInteractionLayerMetadata()
      clearRetainedMetadata()
    }

    preparedSourceAvailable = layers.hasSource
    preparedStageAvailability = stageAvailability(params, currentRenderEffects)

    layers.ensureSource(currentGraphicsContext)
    if (shouldBlur(params, currentRenderEffects)) {
      val blurEffects = checkNotNull(currentRenderEffects.blur)
      val blurWorkingSize = blurEffects.key.plan.workingSize
      if (layers.updateBlurWorkingSize(blurWorkingSize, currentGraphicsContext)) {
        context.invalidateDraw()
      }
      if (blurEffects.key.plan.requiresPrefilter) {
        layers.ensureBlurPrefiltered(currentGraphicsContext)
      } else {
        layers.releaseBlurPrefiltered(currentGraphicsContext)
      }
      layers.ensureBlurHorizontal(currentGraphicsContext)
      layers.ensureBlurred(currentGraphicsContext)
    } else {
      layers.releaseBlurred(currentGraphicsContext)
    }
    if (shouldDepthMix(params, currentRenderEffects)) {
      layers.ensureDepthMixed(currentGraphicsContext)
    } else {
      layers.releaseDepthMixed(currentGraphicsContext)
    }
    layers.ensureOptical(currentGraphicsContext)
    layers.prepareRefractionDetail(
      required = currentRenderEffects.refractionDetail != null,
      graphicsContext = currentGraphicsContext,
    )
    if (currentRenderEffects.rim != null) {
      layers.ensureRim(currentGraphicsContext)
    } else {
      layers.releaseRim(currentGraphicsContext)
    }
    layers.prepareInteraction(
      optics = interactionUniforms.hasOptics,
      detail = interactionUniforms.hasOptics && currentRenderEffects.refractionDetail != null,
      lighting = interactionUniforms.hasLighting,
      graphicsContext = currentGraphicsContext,
    )

    preparedParams = params
    preparedRenderEffects = currentRenderEffects
    preparedInteractionUniforms = interactionUniforms
    preparedInteractionSignals = effect.currentInteractionSignals
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    val params = preparedParams ?: return
    val effects = preparedRenderEffects ?: return
    val interactionUniforms = preparedInteractionUniforms ?: return
    val interactionSignals = preparedInteractionSignals ?: return
    requireDrawableMaterialSize(params.coordinates.materialSize, ::clearRetainedOutput) ?: return
    var completed = false
    try {
      val currentInputs = GlassStageInputs(
        blur = effects.blur?.key?.takeIf { shouldBlur(params, effects) },
        depth = params.depth,
        optical = params.opticalEffectKey(),
        detail = effects.refractionDetail?.key,
        rim = params.rimEffectKey().takeIf { effects.rim != null },
      )
      val sourceState = context.resolveGlassSourceState(params.coordinates.scaleFactor)
      val interactionSignalActive = interactionSignals.hovered ||
        interactionSignals.focused || interactionSignals.pressed
      val dynamicInteractionOnly = retainedOutputAvailable &&
        (
          interactionSignalActive ||
            interactionUniforms != lastSuccessfulInteractionUniforms ||
            interactionSignals != lastSuccessfulInteractionSignals
          ) &&
        currentInputs == lastSuccessfulStageInputs
      val shouldRecordSource = sourceState.hasDrawableSource && (
        sourceState.snapshot == null ||
          sourceState.snapshot != lastSuccessfulSourceSnapshot ||
          !preparedSourceAvailable ||
          !retainedOutputAvailable
        ) && !dynamicInteractionOnly
      val source = requireRetainedStage(
        if (shouldRecordSource) recordSource(context, params) else retainedSource(),
        ::clearRetainedOutput,
      )
        ?: return
      val invalidation = calculateRequiredStageInvalidation(
        previous = lastSuccessfulStageInputs,
        current = currentInputs,
        sourceChanged = shouldRecordSource,
        availability = preparedStageAvailability ?: stageAvailability(params, effects),
      )
      val blurred = requireRetainedStage(
        if (invalidation.blur) {
          recordBlurredIfNeeded(source, params, effects)
        } else {
          retainedBlurInput(source, params, effects)
        },
        ::clearRetainedOutput,
      ) ?: return
      val depthInput = requireRetainedStage(
        if (invalidation.depth) {
          recordDepthInput(source, blurred, params.depth)
        } else {
          retainedDepthInput(source, blurred, params.depth)
        },
        ::clearRetainedOutput,
      ) ?: return
      val optical = requireRetainedStage(
        if (invalidation.optical) recordOptical(depthInput, params, effects) else layers.optical,
        ::clearRetainedOutput,
      ) ?: return
      val refractionDetail = effects.refractionDetail?.let {
        requireRetainedStage(
          if (invalidation.detail) {
            recordRefractionDetail(source, params, it)
          } else {
            layers.refractionDetail?.takeUnless { layer -> layer.isReleased }
          },
          ::clearRetainedOutput,
        ) ?: return
      }
      requireRetainedStage(
        if (invalidation.rim) recordRimIfNeeded(params, effects) else retainedRim(effects),
        ::clearRetainedOutput,
      ) ?: return
      val completedOptical = if (interactionUniforms.hasOptics) {
        requireRetainedStage(
          recordInteractionOptical(
            input = depthInput,
            key = GlassInteractionOpticalKey(params.opticalEffectKey()),
            uniforms = interactionUniforms,
          ),
          ::clearRetainedOutput,
        ) ?: return
      } else {
        optical
      }
      val completedRefractionDetail = if (
        interactionUniforms.hasOptics && effects.refractionDetail != null
      ) {
        requireRetainedStage(
          recordInteractionRefractionDetail(
            input = source,
            key = GlassInteractionDetailKey(effects.refractionDetail.key),
            uniforms = interactionUniforms,
          ),
          ::clearRetainedOutput,
        ) ?: return
      } else {
        refractionDetail
      }
      if (interactionUniforms.hasLighting) {
        requireRetainedStage(
          recordInteractionLighting(
            key = GlassInteractionLightingKey(
              coordinates = params.coordinates,
              edgeSoftnessPx = params.edgeSoftnessPx,
              cornerRadii = params.cornerRadii,
            ),
            uniforms = interactionUniforms,
          ),
          ::clearRetainedOutput,
        ) ?: return
      }
      withAlpha(alpha = effect.alpha, context = context) {
        drawCompletedLayer(completedOptical, context, params, alpha = 1f)
        if (completedRefractionDetail != null) {
          drawCompletedLayer(completedRefractionDetail, context, params, alpha = 1f)
        }
      }
      lastSuccessfulSourceSnapshot = if (sourceState.hasDrawableSource) {
        sourceState.snapshot
      } else {
        lastSuccessfulSourceSnapshot
      }
      lastSuccessfulStageInputs = currentInputs
      lastSuccessfulInteractionUniforms = interactionUniforms
      lastSuccessfulInteractionSignals = interactionSignals
      retainedOutputAvailable = true
      if (interactionUniforms.hasOptics || interactionUniforms.hasLighting) {
        interactionFrameCount++
      }
      completed = true
    } finally {
      if (!completed) {
        clearRetainedOutput()
      }
    }
  }

  override fun DrawScope.drawForeground(context: VisualEffectContext) {
    val params = preparedParams ?: return
    requireDrawableMaterialSize(params.coordinates.materialSize, ::clearRetainedOutput) ?: return
    if (!retainedOutputAvailable) return
    preparedInteractionUniforms?.takeIf { it.hasLighting }?.let {
      layers.interactionLighting?.takeUnless { layer -> layer.isReleased }?.let { layer ->
        drawCompletedLayer(layer, context, params, alpha = effect.alpha)
      }
    }
    layers.rim?.takeUnless { it.isReleased }?.let { rim ->
      drawCompletedLayer(rim, context, params, alpha = effect.alpha)
    }
  }

  override fun canDrawRetainedOutput(): Boolean {
    val detailRequired = preparedRenderEffects?.refractionDetail != null ||
      preparedRenderEffects == null && lastSuccessfulStageInputs?.detail != null
    val interactionUniforms = preparedInteractionUniforms
    val interactionOpticsRequired = interactionUniforms?.hasOptics == true
    val interactionDetailRequired = interactionOpticsRequired && detailRequired
    val interactionLightingRequired = interactionUniforms?.hasLighting == true
    return retainedOutputAvailable && layers.hasOptical &&
      (!detailRequired || layers.hasRefractionDetail) &&
      (!interactionOpticsRequired || layers.hasInteractionOptical) &&
      (!interactionDetailRequired || layers.hasInteractionRefractionDetail) &&
      (!interactionLightingRequired || layers.hasInteractionLighting)
  }

  override fun clearRetainedOutput() {
    releaseRetainedResources()
  }

  private fun clearRetainedMetadata() {
    retainedOutputAvailable = false
    lastSuccessfulSourceSnapshot = null
    lastSuccessfulStageInputs = null
    lastSuccessfulInteractionUniforms = null
    lastSuccessfulInteractionSignals = null
  }

  private fun clearInteractionLayerMetadata() {
    recordedInteractionOpticalLayer = null
    recordedInteractionOpticalInput = null
    recordedInteractionOpticalKey = null
    recordedInteractionDetailLayer = null
    recordedInteractionDetailInput = null
    recordedInteractionDetailKey = null
    recordedInteractionLightingLayer = null
    recordedInteractionLightingKey = null
  }

  override fun detach() {
    releaseRetainedResources()
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    if (level.severity >= TrimMemoryLevel.MODERATE.severity) {
      releaseRetainedResources(graphicsContext ?: context.requireGraphicsContext())
      context.invalidateDraw()
    }
  }

  private fun releaseRetainedResources(
    releaseContext: GraphicsContext? = graphicsContext,
  ) {
    layers.release(releaseContext)
    graphicsContext = null
    blurKey = null
    blurEffects = null
    opticalKey = null
    opticalEffect = null
    refractionDetailKey = null
    refractionDetailEffect = null
    rimKey = null
    rimEffect = null
    interactionOpticalKey = null
    interactionOpticalEffect = null
    interactionDetailKey = null
    interactionDetailEffect = null
    interactionLightingKey = null
    interactionLightingEffect = null
    clearInteractionLayerMetadata()
    preparedParams = null
    preparedRenderEffects = null
    preparedInteractionUniforms = null
    preparedInteractionSignals = null
    preparedSourceAvailable = false
    preparedStageAvailability = null
    clearRetainedMetadata()
  }

  private fun retainedSource(): GraphicsLayer? = layers.source
    ?.takeUnless { it.isReleased }
    ?.takeIf { retainedOutputAvailable }

  private fun retainedBlurInput(
    source: GraphicsLayer,
    params: GlassRenderParams,
    effects: GlassRenderEffects,
  ): GraphicsLayer? = if (shouldBlur(params, effects)) {
    layers.blurred?.takeUnless { it.isReleased }
  } else {
    source
  }

  private fun retainedDepthInput(
    source: GraphicsLayer,
    blurred: GraphicsLayer,
    depth: Float,
  ): GraphicsLayer? = when {
    depth <= 0f || blurred === source -> source
    depth >= 1f -> blurred
    else -> layers.depthMixed?.takeUnless { it.isReleased }
  }

  private fun retainedRim(effects: GlassRenderEffects): Unit? = when {
    effects.rim == null -> Unit
    layers.hasRim -> Unit
    else -> null
  }

  private fun stageAvailability(
    params: GlassRenderParams,
    effects: GlassRenderEffects,
  ): GlassStageAvailability {
    val blur = effects.blur?.takeIf { shouldBlur(params, effects) }
    return GlassStageAvailability(
      blur = blur == null || layers.hasBlurred && layers.hasBlurHorizontal &&
        (!blur.key.plan.requiresPrefilter || layers.hasBlurPrefiltered),
      depth = !shouldDepthMix(params, effects) || layers.hasDepthMixed,
      optical = layers.hasOptical,
      detail = effects.refractionDetail == null || layers.hasRefractionDetail,
      rim = effects.rim == null || layers.hasRim,
    )
  }

  private fun DrawScope.recordSource(
    context: VisualEffectContext,
    params: GlassRenderParams,
  ): GraphicsLayer? {
    val hasDrawableSource = context.areas.any { area ->
      area.contentLayer
        ?.takeUnless { it.isReleased }
        ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 } != null
    }
    if (!hasDrawableSource) {
      return layers.source
        ?.takeUnless { it.isReleased }
        ?.takeIf { retainedOutputAvailable }
    }

    return createScaledContentLayer(
      context = context,
      scaleFactor = params.coordinates.scaleFactor,
      layerSize = context.layerSize,
      layerOffset = context.layerOffset,
      existingLayer = layers.source,
      backgroundColor = Color.Transparent,
    )?.also {
      layers.source = it
      sourceRecordCount++
    }
  }

  private fun DrawScope.recordBlurredIfNeeded(
    source: GraphicsLayer,
    params: GlassRenderParams,
    effects: GlassRenderEffects,
  ): GraphicsLayer? {
    val blur = effects.blur
    if (!shouldBlur(params, effects) || blur == null) return source
    val plan = blur.key.plan
    val workingSize = plan.workingSize
    source.alpha = 1f
    source.blendMode = BlendMode.SrcOver
    val horizontalInput = if (plan.requiresPrefilter) {
      val prefiltered = layers.blurPrefiltered?.takeUnless { it.isReleased } ?: return null
      val prefilterEffect = blur.prefilter ?: return null
      prefiltered.scaleX = 1f
      prefiltered.scaleY = 1f
      prefiltered.pivotOffset = Offset.Zero
      prefiltered.renderEffect = prefilterEffect.asComposeRenderEffect()
      prefiltered.record(plan.sampleSize) { drawLayer(source) }
      prefiltered
    } else {
      source
    }

    val horizontal = layers.blurHorizontal?.takeUnless { it.isReleased } ?: return null
    horizontal.scaleX = 1f
    horizontal.scaleY = 1f
    horizontal.pivotOffset = Offset.Zero
    horizontal.renderEffect = blur.horizontal.asComposeRenderEffect()
    horizontal.record(workingSize) {
      scale(plan.scaleFactor, pivot = Offset.Zero) { drawLayer(horizontalInput) }
    }

    return layers.blurred?.takeUnless { it.isReleased }?.also { blurred ->
      blurred.scaleX = 1f / plan.scaleFactor
      blurred.scaleY = 1f / plan.scaleFactor
      blurred.pivotOffset = Offset.Zero
      blurred.renderEffect = blur.vertical.asComposeRenderEffect()
      blurred.record(workingSize) { drawLayer(horizontal) }
    }
  }

  private fun DrawScope.recordDepthInput(
    source: GraphicsLayer,
    blurred: GraphicsLayer,
    depth: Float,
  ): GraphicsLayer? {
    return when {
      depth <= 0f || blurred === source -> source
      depth >= 1f -> blurred
      else -> {
        val layer = layers.depthMixed?.takeUnless { it.isReleased } ?: return null
        val scaledSize = layers.scaledSize ?: return null
        layer.also {
          it.alpha = 1f
          it.renderEffect = null
          recordDepthMix(
            layer = it,
            size = scaledSize,
            source = source,
            blurred = blurred,
            depth = depth,
          )
        }
      }
    }
  }

  private fun DrawScope.recordOptical(
    input: GraphicsLayer,
    params: GlassRenderParams,
    effects: GlassRenderEffects,
  ): GraphicsLayer? = layers.optical?.takeUnless { it.isReleased }?.also { layer ->
    input.alpha = 1f
    input.blendMode = BlendMode.SrcOver
    layer.alpha = 1f
    layer.renderEffect = effects.optical.asComposeRenderEffect()
    layer.record(params.coordinates.sampleSize.roundToIntSize()) {
      drawLayer(input)
    }
  }

  private fun DrawScope.recordRefractionDetail(
    source: GraphicsLayer,
    params: GlassRenderParams,
    detail: GlassRefractionDetailRenderEffect,
  ): GraphicsLayer? = layers.refractionDetail?.takeUnless { it.isReleased }?.also { layer ->
    source.alpha = 1f
    source.blendMode = BlendMode.SrcOver
    layer.alpha = 1f
    layer.blendMode = BlendMode.SrcOver
    layer.renderEffect = detail.effect.asComposeRenderEffect()
    layer.record(params.coordinates.sampleSize.roundToIntSize()) {
      drawLayer(source)
    }
  }

  private fun DrawScope.recordRimIfNeeded(
    params: GlassRenderParams,
    effects: GlassRenderEffects,
  ): Unit? {
    val rimEffect = effects.rim ?: return Unit
    val layer = layers.rim?.takeUnless { it.isReleased } ?: return null
    layer.alpha = 1f
    layer.renderEffect = rimEffect.asComposeRenderEffect()
    layer.record(params.coordinates.sampleSize.roundToIntSize()) {
      drawRect(Color.Black)
    }
    return Unit
  }

  private fun DrawScope.recordInteractionOptical(
    input: GraphicsLayer,
    key: GlassInteractionOpticalKey,
    uniforms: GlassInteractionUniforms,
  ): GraphicsLayer? = layers.interactionOptical?.takeUnless { it.isReleased }?.also { layer ->
    input.alpha = 1f
    input.blendMode = BlendMode.SrcOver
    layer.alpha = 1f
    layer.renderEffect = updateInteractionOpticalEffect(key, uniforms).asComposeRenderEffect()
    if (
      layer !== recordedInteractionOpticalLayer ||
      input !== recordedInteractionOpticalInput ||
      key != recordedInteractionOpticalKey
    ) {
      layer.record(key.base.coordinates.sampleSize.roundToIntSize()) {
        drawLayer(input)
      }
      recordedInteractionOpticalLayer = layer
      recordedInteractionOpticalInput = input
      recordedInteractionOpticalKey = key
    }
  }

  private fun DrawScope.recordInteractionRefractionDetail(
    input: GraphicsLayer,
    key: GlassInteractionDetailKey,
    uniforms: GlassInteractionUniforms,
  ): GraphicsLayer? = layers.interactionRefractionDetail
    ?.takeUnless { it.isReleased }
    ?.also { layer ->
      input.alpha = 1f
      input.blendMode = BlendMode.SrcOver
      layer.alpha = 1f
      layer.blendMode = BlendMode.SrcOver
      layer.renderEffect = updateInteractionDetailEffect(key, uniforms).asComposeRenderEffect()
      if (
        layer !== recordedInteractionDetailLayer ||
        input !== recordedInteractionDetailInput ||
        key != recordedInteractionDetailKey
      ) {
        layer.record(key.base.sampleSize.roundToIntSize()) {
          drawLayer(input)
        }
        recordedInteractionDetailLayer = layer
        recordedInteractionDetailInput = input
        recordedInteractionDetailKey = key
      }
    }

  private fun DrawScope.recordInteractionLighting(
    key: GlassInteractionLightingKey,
    uniforms: GlassInteractionUniforms,
  ): GraphicsLayer? = layers.interactionLighting?.takeUnless { it.isReleased }?.also { layer ->
    layer.alpha = 1f
    layer.blendMode = BlendMode.SrcOver
    layer.renderEffect = updateInteractionLightingEffect(key, uniforms).asComposeRenderEffect()
    if (layer !== recordedInteractionLightingLayer || key != recordedInteractionLightingKey) {
      layer.record(key.coordinates.sampleSize.roundToIntSize()) {
        drawRect(Color.Black)
      }
      recordedInteractionLightingLayer = layer
      recordedInteractionLightingKey = key
    }
  }

  private fun updateInteractionOpticalEffect(
    key: GlassInteractionOpticalKey,
    uniforms: GlassInteractionUniforms,
  ): PlatformRenderEffect {
    if (key != interactionOpticalKey || interactionOpticalEffect == null) {
      interactionOpticalKey = key
      interactionOpticalEffect = createMutableRuntimeShaderRenderEffect(
        effect = GLASS_INTERACTION_OPTICAL_EFFECT,
        shaderNames = arrayOf("content"),
        inputs = arrayOf(null),
      )
    }
    return checkNotNull(interactionOpticalEffect).updateUniforms {
      setOpticalUniforms(key.base)
      setInteractionOpticalUniforms(uniforms)
    }
  }

  private fun updateInteractionDetailEffect(
    key: GlassInteractionDetailKey,
    uniforms: GlassInteractionUniforms,
  ): PlatformRenderEffect {
    if (key != interactionDetailKey || interactionDetailEffect == null) {
      interactionDetailKey = key
      interactionDetailEffect = createMutableRuntimeShaderRenderEffect(
        effect = GLASS_INTERACTION_REFRACTION_DETAIL_EFFECT,
        shaderNames = arrayOf("content"),
        inputs = arrayOf(null),
      )
    }
    return checkNotNull(interactionDetailEffect).updateUniforms {
      setRefractionDetailUniforms(key.base)
      setInteractionDetailUniforms(uniforms)
    }
  }

  private fun updateInteractionLightingEffect(
    key: GlassInteractionLightingKey,
    uniforms: GlassInteractionUniforms,
  ): PlatformRenderEffect {
    if (key != interactionLightingKey || interactionLightingEffect == null) {
      interactionLightingKey = key
      interactionLightingEffect = createMutableRuntimeShaderRenderEffect(
        effect = GLASS_INTERACTION_LIGHTING_EFFECT,
        shaderNames = arrayOf("content"),
        inputs = arrayOf(null),
      )
    }
    return checkNotNull(interactionLightingEffect).updateUniforms {
      setInteractionLightingUniforms(key, uniforms)
    }
  }

  private fun DrawScope.drawCompletedLayer(
    layer: GraphicsLayer,
    context: VisualEffectContext,
    params: GlassRenderParams,
    alpha: Float,
  ) {
    layer.clip = effect.shouldClipToNodeBounds()
    layer.alpha = alpha
    drawScaledContent(
      offset = -context.layerOffset,
      scaledSize = params.coordinates.materialSize,
      clip = effect.shouldClipToNodeBounds(),
    ) {
      drawLayer(layer)
    }
  }

  private fun shouldBlur(
    params: GlassRenderParams,
    effects: GlassRenderEffects,
  ): Boolean = params.depth > 0f && params.blurRadiusPx > 0f && effects.blur != null

  private fun shouldDepthMix(
    params: GlassRenderParams,
    effects: GlassRenderEffects,
  ): Boolean = params.depth > 0f && params.depth < 1f && shouldBlur(params, effects)

  private fun DrawScope.buildRenderParams(
    context: VisualEffectContext,
    coordinates: GlassCoordinates,
  ): GlassRenderParams {
    val scaleFactor = coordinates.scaleFactor
    val density = context.requireDensity()
    val layoutDirection = context.currentValueOf(LocalLayoutDirection)
    val unscaledRadii = effect.shape.toCornerRadiiPx(
      layerSize = context.size,
      density = density,
      layoutDirection = layoutDirection,
    )
    val scaledRadii = unscaledRadii * scaleFactor
    val resolvedOptics = resolveGlassOptics(
      optics = effect.optics,
      materialSizePx = context.size,
      density = density,
      cornerRadiiPx = unscaledRadii,
    )
    val lightPosition = effect.lightPosition
      .takeOrElse { context.size.center } * scaleFactor
    return GlassRenderParams(
      coordinates = coordinates,
      refractionStrength = resolvedOptics.refractionStrength,
      specularIntensity = effect.specularIntensity.coerceIn(0f, 1f),
      depth = resolvedOptics.depth,
      ambientResponse = effect.ambientResponse.coerceIn(0f, 1f),
      tint = effect.tint,
      edgeSoftnessPx = with(density) { effect.edgeSoftness.toPx() } * scaleFactor,
      blurRadiusPx = resolvedOptics.blurRadiusPx * scaleFactor,
      blurSigmaPx = resolvedOptics.blurSigmaPx * scaleFactor,
      progressive = resolvedOptics.progressive,
      refractionHeightPx = resolvedOptics.refractionHeightPx * scaleFactor,
      chromaticAberrationStrength = effect.chromaticAberrationStrength.coerceIn(0f, 1f),
      surfaceProfile = effect.surfaceProfile.ordinal.toFloat(),
      chromaticAberrationMode = effect.chromaticAberrationMode.ordinal.toFloat(),
      contrast = effect.contrast.coerceIn(-1f, 1f),
      whitePoint = effect.whitePoint.coerceIn(-1f, 1f),
      chromaMultiplier = effect.chromaMultiplier.coerceIn(0f, 2f),
      refractionScalePx = resolvedOptics.refractionScalePx * scaleFactor,
      contentNormalBlend = effect.contentNormalBlend.coerceIn(0f, 1f),
      specularExponent = effect.specularExponent.coerceAtLeast(0f),
      fresnelExponent = effect.fresnelExponent.coerceAtLeast(0f),
      geometryToneGain = resolvedOptics.toneGain,
      geometryNeutralLift = resolvedOptics.neutralLiftWeight,
      cornerRadii = scaledRadii,
      lightPosition = lightPosition,
      sampleStepPx = 2f * scaleFactor,
    )
  }

  private fun getRenderEffects(params: GlassRenderParams): GlassRenderEffects {
    val nextBlurKey = params.blurEffectKey()
    if (nextBlurKey != blurKey) {
      blurEffects = createGlassBlurRenderEffects(nextBlurKey)
      blurKey = nextBlurKey
    }
    val nextOpticalKey = params.opticalEffectKey()
    if (nextOpticalKey != opticalKey || opticalEffect == null) {
      opticalEffect = createGlassOpticalRenderEffect(nextOpticalKey)
      baseOpticalEffectCreationCount++
      opticalKey = nextOpticalKey
    }
    val nextRefractionDetailKey = params.activeRefractionDetailEffectKey()
    if (
      nextRefractionDetailKey != refractionDetailKey ||
      nextRefractionDetailKey != null && refractionDetailEffect == null
    ) {
      refractionDetailEffect = nextRefractionDetailKey?.let(::createRefractionDetailRenderEffect)
      refractionDetailKey = nextRefractionDetailKey
    }
    val nextRimKey = params.rimEffectKey()
    if (nextRimKey != rimKey) {
      rimEffect = createGlassRimRenderEffect(nextRimKey)
      rimKey = nextRimKey
    }
    return GlassRenderEffects(
      blur = blurEffects,
      optical = checkNotNull(opticalEffect),
      refractionDetail = nextRefractionDetailKey?.let { key ->
        GlassRefractionDetailRenderEffect(key, checkNotNull(refractionDetailEffect))
      },
      rim = rimEffect,
    )
  }
}

@OptIn(InternalHazeApi::class)
internal data class GlassRenderEffects(
  val blur: GlassBlurRenderEffects?,
  val optical: PlatformRenderEffect,
  val refractionDetail: GlassRefractionDetailRenderEffect?,
  val rim: PlatformRenderEffect?,
)

@OptIn(InternalHazeApi::class)
internal data class GlassRefractionDetailRenderEffect(
  val key: GlassRefractionDetailEffectKey,
  val effect: PlatformRenderEffect,
)

@OptIn(InternalHazeApi::class)
internal data class GlassBlurRenderEffects(
  val key: GlassBlurEffectKey,
  val prefilter: PlatformRenderEffect?,
  val horizontal: PlatformRenderEffect,
  val vertical: PlatformRenderEffect,
)

internal expect fun createGlassBlurRenderEffects(
  key: GlassBlurEffectKey,
): GlassBlurRenderEffects?

internal expect fun createGlassOpticalRenderEffect(
  key: GlassOpticalEffectKey,
): PlatformRenderEffect

internal expect fun createRefractionDetailRenderEffect(
  key: GlassRefractionDetailEffectKey,
): PlatformRenderEffect

internal expect fun createGlassRimRenderEffect(
  key: GlassRimEffectKey,
): PlatformRenderEffect?

internal fun createSharedGlassBlurRenderEffects(
  key: GlassBlurEffectKey,
): GlassBlurRenderEffects? {
  if (key.plan.isIdentity) return null
  val progressiveMask = key.progressive?.toShader(
    GlassCoordinates(
      sampleSize = Size(key.plan.workingSize.width.toFloat(), key.plan.workingSize.height.toFloat()),
      materialOrigin = key.materialOrigin,
      materialSize = key.materialSize,
      scaleFactor = 1f,
    ),
  )
  val horizontalBlur = createBlurRenderEffect(
    key = key,
    kernel = key.plan.horizontalKernel,
    horizontal = true,
    progressiveMask = progressiveMask,
  )
  val verticalBlur = createBlurRenderEffect(
    key = key,
    kernel = key.plan.verticalKernel,
    horizontal = false,
    progressiveMask = progressiveMask,
  )
  val prefilter = key.plan.takeIf { it.requiresPrefilter }?.let { plan ->
    createRuntimeShaderRenderEffect(
      effect = GLASS_DOWNSAMPLE_PREFILTER_EFFECT,
      shaderNames = arrayOf("content"),
      inputs = arrayOf(null),
    ) {
      setFloatUniform(
        "sampleSize",
        plan.sampleSize.width.toFloat(),
        plan.sampleSize.height.toFloat(),
      )
    }
  }
  return GlassBlurRenderEffects(
    key = key,
    prefilter = prefilter,
    horizontal = horizontalBlur,
    vertical = verticalBlur,
  )
}

private fun createBlurRenderEffect(
  key: GlassBlurEffectKey,
  kernel: SemanticBlurKernel,
  horizontal: Boolean,
  progressiveMask: androidx.compose.ui.graphics.Shader?,
  sampleWidth: Int = key.plan.workingSize.width,
  sampleHeight: Int = key.plan.workingSize.height,
): PlatformRenderEffect = createRuntimeShaderRenderEffect(
  effect = if (progressiveMask == null) {
    if (horizontal) GLASS_HORIZONTAL_BLUR_EFFECT else GLASS_VERTICAL_BLUR_EFFECT
  } else {
    if (horizontal) {
      GLASS_PROGRESSIVE_HORIZONTAL_BLUR_EFFECT
    } else {
      GLASS_PROGRESSIVE_VERTICAL_BLUR_EFFECT
    }
  },
  shaderNames = arrayOf("content"),
  inputs = arrayOf(null),
) {
  setBlurUniforms(key, kernel, sampleWidth, sampleHeight)
  progressiveMask?.let { setChildShader("mask", it) }
}

internal fun createSharedGlassOpticalRenderEffect(
  key: GlassOpticalEffectKey,
): PlatformRenderEffect = createRuntimeShaderRenderEffect(
  effect = GLASS_OPTICAL_EFFECT,
  shaderNames = arrayOf("content"),
  inputs = arrayOf(null),
) {
  setOpticalUniforms(key)
}

internal fun createSharedRefractionDetailRenderEffect(
  key: GlassRefractionDetailEffectKey,
): PlatformRenderEffect = createRuntimeShaderRenderEffect(
  effect = GLASS_REFRACTION_DETAIL_EFFECT,
  shaderNames = arrayOf("content"),
  inputs = arrayOf(null),
) {
  setRefractionDetailUniforms(key)
}

internal fun createSharedGlassRimRenderEffect(
  key: GlassRimEffectKey,
): PlatformRenderEffect? = key.takeIf { it.specularIntensity > 0f }?.let {
  createRuntimeShaderRenderEffect(
    effect = GLASS_RIM_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  ) {
    setRimUniforms(key)
  }
}

private fun RuntimeShaderUniformProvider.setBlurUniforms(
  key: GlassBlurEffectKey,
  kernel: SemanticBlurKernel,
  sampleWidth: Int,
  sampleHeight: Int,
) {
  setFloatUniform("sampleSize", sampleWidth.toFloat(), sampleHeight.toFloat())
  setFloatUniform(
    "materialOrigin",
    key.materialOrigin.x,
    key.materialOrigin.y,
  )
  setFloatUniform("centerWeight", kernel.centerWeight)
  repeat(SemanticBlurKernel.MAX_TAP_PAIRS) { index ->
    val tap = kernel.taps.getOrNull(index)
    setFloatUniform("offset$index", tap?.offsetPx ?: 0f)
    setFloatUniform("weight$index", tap?.weight ?: 0f)
  }
}

private val GLASS_HORIZONTAL_BLUR_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildBlur(horizontal = true))
}
private val GLASS_DOWNSAMPLE_PREFILTER_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildDownsamplePrefilter())
}
private val GLASS_VERTICAL_BLUR_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildBlur(horizontal = false))
}
private val GLASS_PROGRESSIVE_HORIZONTAL_BLUR_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildBlur(horizontal = true, progressive = true))
}
private val GLASS_PROGRESSIVE_VERTICAL_BLUR_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildBlur(horizontal = false, progressive = true))
}
private val GLASS_OPTICAL_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildOptical())
}
private val GLASS_INTERACTION_OPTICAL_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildOptical(interactive = true))
}
private val GLASS_REFRACTION_DETAIL_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildRefractionDetail())
}
private val GLASS_INTERACTION_REFRACTION_DETAIL_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildRefractionDetail(interactive = true))
}
private val GLASS_INTERACTION_LIGHTING_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildInteractionLighting())
}
private val GLASS_RIM_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildRim())
}

internal fun RuntimeShaderUniformProvider.setOpticalUniforms(
  key: GlassOpticalEffectKey,
) {
  setCommonUniforms(key.coordinates, key.sampleStepPx, key.edgeSoftnessPx, key.cornerRadii)
  setFloatUniform("refractionStrength", key.refractionStrength)
  setFloatUniform("ambientResponse", key.ambientResponse)
  setFloatUniform("refractionHeight", key.refractionHeightPx)
  setFloatUniform("chromaticAberrationStrength", key.chromaticAberrationStrength)
  setFloatUniform("surfaceProfile", key.surfaceProfile)
  setFloatUniform("chromaticAberrationMode", key.chromaticAberrationMode)
  setFloatUniform("contrast", key.contrast)
  setFloatUniform("whitePoint", key.whitePoint)
  setFloatUniform("chromaMultiplier", key.chromaMultiplier)
  setFloatUniform("refractionScale", key.refractionScalePx)
  setFloatUniform("contentNormalBlend", key.contentNormalBlend)
  setFloatUniform("fresnelExponent", key.fresnelExponent)
  setFloatUniform("geometryToneGain", key.geometryToneGain)
  setFloatUniform("geometryNeutralLift", key.geometryNeutralLift)
  setFloatUniform(
    "tintColor",
    key.tint.red,
    key.tint.green,
    key.tint.blue,
    key.tint.alpha,
  )
}

internal fun RuntimeShaderUniformProvider.setRefractionDetailUniforms(
  key: GlassRefractionDetailEffectKey,
) {
  setFloatUniform("sampleSize", key.sampleSize.width, key.sampleSize.height)
  setFloatUniform("materialOrigin", key.materialOrigin.x, key.materialOrigin.y)
  setFloatUniform("materialSize", key.materialSize.width, key.materialSize.height)
  setFloatUniform("edgeSoftness", key.edgeSoftnessPx)
  setFloatUniform(
    "cornerRadii",
    key.cornerRadii.topLeft,
    key.cornerRadii.topRight,
    key.cornerRadii.bottomRight,
    key.cornerRadii.bottomLeft,
  )
  setFloatUniform("refractionStrength", key.refractionStrength)
  setFloatUniform("refractionHeight", key.refractionHeightPx)
  setFloatUniform("refractionScale", key.refractionScalePx)
  setFloatUniform("surfaceProfile", key.surfaceProfile)
  setFloatUniform("detailWidth", key.detailWidthPx)
  setFloatUniform("detailIntensity", key.detailIntensity)
  setFloatUniform("detailVisibility", key.detailVisibility)
}

internal fun RuntimeShaderUniformProvider.setRimUniforms(
  key: GlassRimEffectKey,
) {
  setCommonUniforms(key.coordinates, key.sampleStepPx, key.edgeSoftnessPx, key.cornerRadii)
  setFloatUniform("specularIntensity", key.specularIntensity)
  setFloatUniform("specularExponent", key.specularExponent)
  setFloatUniform(
    "lightPosition",
    key.lightPosition.x,
    key.lightPosition.y,
  )
}

internal fun RuntimeShaderUniformProvider.setInteractionOpticalUniforms(
  uniforms: GlassInteractionUniforms,
) {
  setInteractionPositionUniforms(uniforms)
  setFloatUniform("interactionRefractionMultiplier", uniforms.refractionMultiplier)
  setFloatUniform("interactionWhitePointDelta", uniforms.whitePointDelta)
}

internal fun RuntimeShaderUniformProvider.setInteractionDetailUniforms(
  uniforms: GlassInteractionUniforms,
) {
  setInteractionPositionUniforms(uniforms)
  setFloatUniform("interactionRefractionMultiplier", uniforms.refractionMultiplier)
}

internal fun RuntimeShaderUniformProvider.setInteractionLightingUniforms(
  key: GlassInteractionLightingKey,
  uniforms: GlassInteractionUniforms,
) {
  setFloatUniform("materialOrigin", key.coordinates.materialOrigin.x, key.coordinates.materialOrigin.y)
  setFloatUniform("materialSize", key.coordinates.materialSize.width, key.coordinates.materialSize.height)
  setFloatUniform("edgeSoftness", key.edgeSoftnessPx)
  setFloatUniform(
    "cornerRadii",
    key.cornerRadii.topLeft,
    key.cornerRadii.topRight,
    key.cornerRadii.bottomRight,
    key.cornerRadii.bottomLeft,
  )
  setInteractionPositionUniforms(uniforms)
  setFloatUniform("interactionLightingIntensity", uniforms.lightingIntensity)
}

private fun RuntimeShaderUniformProvider.setInteractionPositionUniforms(
  uniforms: GlassInteractionUniforms,
) {
  setFloatUniform("interactionPosition", uniforms.position.x, uniforms.position.y)
  setFloatUniform("interactionRadius", uniforms.radiusPx)
}

private fun RuntimeShaderUniformProvider.setCommonUniforms(
  coordinates: GlassCoordinates,
  sampleStepPx: Float,
  edgeSoftnessPx: Float,
  cornerRadii: CornerRadii,
) {
  setFloatUniform(
    "sampleSize",
    coordinates.sampleSize.width,
    coordinates.sampleSize.height,
  )
  setFloatUniform(
    "materialOrigin",
    coordinates.materialOrigin.x,
    coordinates.materialOrigin.y,
  )
  setFloatUniform(
    "materialSize",
    coordinates.materialSize.width,
    coordinates.materialSize.height,
  )
  setFloatUniform("sampleStep", sampleStepPx)
  setFloatUniform("edgeSoftness", edgeSoftnessPx)
  setFloatUniform(
    "cornerRadii",
    cornerRadii.topLeft,
    cornerRadii.topRight,
    cornerRadii.bottomRight,
    cornerRadii.bottomLeft,
  )
}

internal inline fun <T : Any> requireRetainedStage(
  value: T?,
  onUnavailable: () -> Unit,
): T? {
  if (value == null) {
    onUnavailable()
  }
  return value
}

internal inline fun requireDrawableMaterialSize(
  size: Size,
  onUnavailable: () -> Unit,
): Size? {
  if (!size.width.isFinite() || !size.height.isFinite() || size.width <= 0f || size.height <= 0f) {
    onUnavailable()
    return null
  }
  return size
}
