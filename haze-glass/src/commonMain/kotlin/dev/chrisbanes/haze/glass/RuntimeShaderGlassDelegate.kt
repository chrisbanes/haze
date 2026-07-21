// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
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

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class RuntimeShaderGlassDelegate(
  private val effect: GlassVisualEffect,
) : GlassVisualEffect.Delegate, RetainedOutputDelegate {
  private var blurKey: GlassBlurEffectKey? = null
  private var blurEffects: GlassBlurRenderEffects? = null
  internal var blurHorizontalShader: MutableRuntimeShaderRenderEffect? = null
    private set
  internal var blurVerticalShader: MutableRuntimeShaderRenderEffect? = null
    private set
  internal var progressiveBlurHorizontalShader: MutableRuntimeShaderRenderEffect? = null
    private set
  internal var progressiveBlurVerticalShader: MutableRuntimeShaderRenderEffect? = null
    private set
  internal var blurPrefilterShader: MutableRuntimeShaderRenderEffect? = null
    private set
  private var opticalKey: GlassOpticalEffectKey? = null
  internal var opticalShader: MutableRuntimeShaderRenderEffect? = null
    private set
  internal var opticalEffect: PlatformRenderEffect? = null
    private set
  private var refractionDetailKey: GlassRefractionDetailEffectKey? = null
  internal var refractionDetailShader: MutableRuntimeShaderRenderEffect? = null
    private set
  private var refractionDetailEffect: PlatformRenderEffect? = null
  private var rimKey: GlassRimEffectKey? = null
  internal var rimShader: MutableRuntimeShaderRenderEffect? = null
    private set
  internal var rimEffect: PlatformRenderEffect? = null
    private set
  private var interactionOpticalEffect: MutableRuntimeShaderRenderEffect? = null
  private var interactionDetailEffect: MutableRuntimeShaderRenderEffect? = null
  private var interactionLightingEffect: MutableRuntimeShaderRenderEffect? = null
  private var recordedInteractionOpticalLayer: GraphicsLayer? = null
  private var recordedInteractionOpticalInput: GraphicsLayer? = null
  private var recordedInteractionOpticalKey: GlassOpticalEffectKey? = null
  private var recordedInteractionDetailLayer: GraphicsLayer? = null
  private var recordedInteractionDetailInput: GraphicsLayer? = null
  private var recordedInteractionDetailKey: GlassRefractionDetailEffectKey? = null
  private var recordedInteractionLightingLayer: GraphicsLayer? = null
  private var recordedInteractionLightingKey: GlassInteractionLightingKey? = null
  internal val layers = GlassLayers()
  private var graphicsContext: GraphicsContext? = null
  private var preparedRender: GlassPreparedRender? = null
  private var preparedParams: GlassRenderParams? = null
  private var preparedRenderEffects: GlassRenderEffects? = null
  private var preparedInteractionUniforms: GlassInteractionUniforms? = null
  private var preparedSourceAvailable: Boolean = false
  private var preparedStageAvailability: GlassStageAvailability? = null
  private var retainedOutputAvailable: Boolean = false
  internal var lastSuccessfulSourceSnapshot: GlassSourceSnapshot? = null
    private set
  internal var lastSuccessfulStageInputs: GlassStageInputs? = null
    private set
  internal var sourceRecordCount: Int = 0
    private set
  internal var blurRecordCount: Int = 0
    private set
  internal var depthRecordCount: Int = 0
    private set
  internal var opticalRecordCount: Int = 0
    private set
  internal var detailRecordCount: Int = 0
    private set
  internal var rimRecordCount: Int = 0
    private set
  internal val stageRecordCounts: GlassStageRecordCounts
    get() = GlassStageRecordCounts(
      source = sourceRecordCount,
      blur = blurRecordCount,
      depth = depthRecordCount,
      optical = opticalRecordCount,
      detail = detailRecordCount,
      rim = rimRecordCount,
    )

  override fun DrawScope.prepareDraw(context: VisualEffectContext) {
    val currentPreparedRender = effect.preparedRender
    if (
      effect.preparedRenderBudget !is GlassRenderBudgetDecision.Runtime ||
      currentPreparedRender == null
    ) {
      releaseRetainedResources()
      return
    }
    val params = currentPreparedRender.params
    val interactionUniforms = currentPreparedRender.interactionUniforms
    val currentRenderEffects = getRenderEffects(currentPreparedRender)
    val scaledSize = params.coordinates.sampleSize.roundToIntSize()

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

    val blurRequired = shouldBlur(params, currentRenderEffects)
    val depthMixRequired = shouldDepthMix(params, currentRenderEffects)
    val refractionDetailRequired = currentRenderEffects.refractionDetail != null
    val rimRequired = currentRenderEffects.rim != null
    val interactionOpticsRequired = interactionUniforms.hasOptics
    val interactionDetailRequired = interactionOpticsRequired && refractionDetailRequired
    val interactionLightingRequired = interactionUniforms.hasLighting

    // Drop stages which are no longer part of the graph before allocating their replacements.
    // This keeps topology transitions from temporarily retaining both complete graphs.
    if (blurRequired) {
      val blurEffects = checkNotNull(currentRenderEffects.blur)
      val blurWorkingSize = blurEffects.key.plan.workingSize
      layers.updateBlurWorkingSize(blurWorkingSize, currentGraphicsContext)
      if (!blurEffects.key.plan.requiresPrefilter) {
        layers.releaseBlurPrefiltered(currentGraphicsContext)
      }
    } else {
      layers.releaseBlurred(currentGraphicsContext)
    }
    if (!depthMixRequired) {
      layers.releaseDepthMixed(currentGraphicsContext)
    }
    if (!refractionDetailRequired) {
      layers.releaseRefractionDetail(currentGraphicsContext)
    }
    if (!rimRequired) {
      layers.releaseRim(currentGraphicsContext)
    }
    releaseObsoleteInteractionLayers(
      opticsRequired = interactionOpticsRequired,
      detailRequired = interactionDetailRequired,
      lightingRequired = interactionLightingRequired,
      graphicsContext = currentGraphicsContext,
    )

    layers.ensureSource(currentGraphicsContext)
    if (blurRequired) {
      val blurPlan = checkNotNull(currentRenderEffects.blur).key.plan
      if (blurPlan.requiresPrefilter) {
        layers.ensureBlurPrefiltered(currentGraphicsContext)
      }
      layers.ensureBlurHorizontal(currentGraphicsContext)
      layers.ensureBlurred(currentGraphicsContext)
    }
    if (depthMixRequired) {
      layers.ensureDepthMixed(currentGraphicsContext)
    }
    layers.ensureOptical(currentGraphicsContext)
    if (refractionDetailRequired) {
      layers.ensureRefractionDetail(currentGraphicsContext)
    }
    if (rimRequired) {
      layers.ensureRim(currentGraphicsContext)
    }
    layers.prepareInteraction(
      optics = interactionOpticsRequired,
      detail = interactionDetailRequired,
      lighting = interactionLightingRequired,
      graphicsContext = currentGraphicsContext,
    )

    preparedRender = currentPreparedRender
    preparedParams = params
    preparedRenderEffects = currentRenderEffects
    preparedInteractionUniforms = interactionUniforms
  }

  private fun releaseObsoleteInteractionLayers(
    opticsRequired: Boolean,
    detailRequired: Boolean,
    lightingRequired: Boolean,
    graphicsContext: GraphicsContext,
  ) {
    if (!opticsRequired) {
      releaseLayer(layers.interactionOptical, graphicsContext)
      layers.interactionOptical = null
    }
    if (!detailRequired) {
      releaseLayer(layers.interactionRefractionDetail, graphicsContext)
      layers.interactionRefractionDetail = null
    }
    if (!lightingRequired) {
      releaseLayer(layers.interactionLighting, graphicsContext)
      layers.interactionLighting = null
    }
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    val render = preparedRender ?: return
    val params = preparedParams ?: return
    val effects = preparedRenderEffects ?: return
    val interactionUniforms = preparedInteractionUniforms ?: return
    requireDrawableMaterialSize(params.coordinates.materialSize, ::clearRetainedOutput) ?: return
    var completed = false
    try {
      val currentInputs = GlassStageInputs(
        blur = render.blurKey?.takeIf { shouldBlur(params, effects) },
        depth = params.depth,
        optical = render.opticalKey,
        detail = render.refractionDetailKey,
        rim = render.rimKey,
      )
      val sourceState = context.resolveGlassSourceState(
        captureScale = params.coordinates.scaleFactor,
        previousSnapshot = lastSuccessfulSourceSnapshot,
      )
      val shouldRecordSource = sourceState.hasDrawableSource && (
        sourceState.snapshot == null ||
          sourceState.snapshot != lastSuccessfulSourceSnapshot ||
          !preparedSourceAvailable ||
          !retainedOutputAvailable
        )
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
            key = render.opticalKey,
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
            key = effects.refractionDetail.key,
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
      withAlpha(alpha = render.alpha, context = context) {
        drawCompletedLayer(completedOptical, context, params, alpha = 1f)
        if (completedRefractionDetail != null) {
          drawCompletedLayer(completedRefractionDetail, context, params, alpha = 1f)
        }
      }
      if (shouldRecordSource) {
        lastSuccessfulSourceSnapshot = sourceState.snapshot
      }
      lastSuccessfulStageInputs = currentInputs
      retainedOutputAvailable = true
      completed = true
    } finally {
      if (!completed) {
        clearRetainedOutput()
      }
    }
  }

  override fun DrawScope.drawForeground(context: VisualEffectContext) {
    val render = preparedRender ?: return
    val params = preparedParams ?: return
    requireDrawableMaterialSize(params.coordinates.materialSize, ::clearRetainedOutput) ?: return
    if (!retainedOutputAvailable) return
    preparedInteractionUniforms?.takeIf { it.hasLighting }?.let {
      layers.interactionLighting?.takeUnless { layer -> layer.isReleased }?.let { layer ->
        drawCompletedLayer(layer, context, params, alpha = render.alpha)
      }
    }
    layers.rim?.takeUnless { it.isReleased }?.let { rim ->
      drawCompletedLayer(rim, context, params, alpha = render.alpha)
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
    if (shouldReleaseRetainedGlass(level)) {
      releaseRetainedResources(graphicsContext ?: context.requireGraphicsContext())
      context.invalidateDraw()
    }
  }

  private fun shouldReleaseRetainedGlass(level: TrimMemoryLevel): Boolean =
    level == TrimMemoryLevel.UI_HIDDEN ||
      level.severity >= TrimMemoryLevel.MODERATE.severity

  private fun releaseRetainedResources(
    releaseContext: GraphicsContext? = graphicsContext,
  ) {
    layers.release(releaseContext)
    graphicsContext = null
    blurKey = null
    blurEffects = null
    blurHorizontalShader = null
    blurVerticalShader = null
    progressiveBlurHorizontalShader = null
    progressiveBlurVerticalShader = null
    blurPrefilterShader = null
    opticalKey = null
    opticalShader = null
    opticalEffect = null
    refractionDetailKey = null
    refractionDetailShader = null
    refractionDetailEffect = null
    rimKey = null
    rimShader = null
    rimEffect = null
    interactionOpticalEffect = null
    interactionDetailEffect = null
    interactionLightingEffect = null
    clearInteractionLayerMetadata()
    preparedParams = null
    preparedRender = null
    preparedRenderEffects = null
    preparedInteractionUniforms = null
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
      blurRecordCount++
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
          depthRecordCount++
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
    opticalRecordCount++
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
    detailRecordCount++
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
    rimRecordCount++
    return Unit
  }

  private fun DrawScope.recordInteractionOptical(
    input: GraphicsLayer,
    key: GlassOpticalEffectKey,
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
      layer.record(key.coordinates.sampleSize.roundToIntSize()) {
        drawLayer(input)
      }
      recordedInteractionOpticalLayer = layer
      recordedInteractionOpticalInput = input
      recordedInteractionOpticalKey = key
    }
  }

  private fun DrawScope.recordInteractionRefractionDetail(
    input: GraphicsLayer,
    key: GlassRefractionDetailEffectKey,
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
        layer.record(key.sampleSize.roundToIntSize()) {
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
    key: GlassOpticalEffectKey,
    uniforms: GlassInteractionUniforms,
  ): PlatformRenderEffect {
    if (interactionOpticalEffect == null) {
      interactionOpticalEffect = createMutableRuntimeShaderRenderEffect(
        effect = GLASS_INTERACTION_OPTICAL_EFFECT,
        shaderNames = arrayOf("content"),
        inputs = arrayOf(null),
      )
    }
    return checkNotNull(interactionOpticalEffect).updateUniforms {
      setOpticalUniforms(key)
      setInteractionOpticalUniforms(uniforms)
    }
  }

  private fun updateInteractionDetailEffect(
    key: GlassRefractionDetailEffectKey,
    uniforms: GlassInteractionUniforms,
  ): PlatformRenderEffect {
    if (interactionDetailEffect == null) {
      interactionDetailEffect = createMutableRuntimeShaderRenderEffect(
        effect = GLASS_INTERACTION_REFRACTION_DETAIL_EFFECT,
        shaderNames = arrayOf("content"),
        inputs = arrayOf(null),
      )
    }
    return checkNotNull(interactionDetailEffect).updateUniforms {
      setRefractionDetailUniforms(key)
      setInteractionDetailUniforms(uniforms)
    }
  }

  private fun updateInteractionLightingEffect(
    key: GlassInteractionLightingKey,
    uniforms: GlassInteractionUniforms,
  ): PlatformRenderEffect {
    if (interactionLightingEffect == null) {
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

  private fun getRenderEffects(render: GlassPreparedRender): GlassRenderEffects {
    val nextBlurKey = render.blurKey
    if (nextBlurKey != blurKey) {
      blurEffects = nextBlurKey?.let(::updateBlurRenderEffects)
      blurKey = nextBlurKey
    }
    val nextOpticalKey = render.opticalKey
    if (nextOpticalKey != opticalKey || opticalEffect == null) {
      val shader = opticalShader ?: createGlassOpticalRenderEffect().also {
        opticalShader = it
      }
      opticalEffect = shader.updateUniforms { setOpticalUniforms(nextOpticalKey) }
      opticalKey = nextOpticalKey
    }
    val nextRefractionDetailKey = render.refractionDetailKey
    if (
      nextRefractionDetailKey != refractionDetailKey ||
      nextRefractionDetailKey != null && refractionDetailEffect == null
    ) {
      refractionDetailEffect = nextRefractionDetailKey?.let { key ->
        val shader = refractionDetailShader ?: createRefractionDetailRenderEffect().also {
          refractionDetailShader = it
        }
        shader.updateUniforms { setRefractionDetailUniforms(key) }
      }
      refractionDetailKey = nextRefractionDetailKey
    }
    val nextRimKey = render.rimKey
    if (nextRimKey != rimKey) {
      rimEffect = nextRimKey?.takeIf { it.specularIntensity > 0f }?.let { key ->
        val shader = rimShader ?: createGlassRimRenderEffect().also { rimShader = it }
        shader.updateUniforms { setRimUniforms(key) }
      }
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

  private fun updateBlurRenderEffects(key: GlassBlurEffectKey): GlassBlurRenderEffects? {
    if (key.plan.isIdentity) return null
    val progressive = key.progressive != null
    val horizontalShader = if (progressive) {
      progressiveBlurHorizontalShader ?: createGlassBlurRenderEffect(
        horizontal = true,
        progressive = true,
      ).also { progressiveBlurHorizontalShader = it }
    } else {
      blurHorizontalShader ?: createGlassBlurRenderEffect(
        horizontal = true,
        progressive = false,
      ).also { blurHorizontalShader = it }
    }
    val verticalShader = if (progressive) {
      progressiveBlurVerticalShader ?: createGlassBlurRenderEffect(
        horizontal = false,
        progressive = true,
      ).also { progressiveBlurVerticalShader = it }
    } else {
      blurVerticalShader ?: createGlassBlurRenderEffect(
        horizontal = false,
        progressive = false,
      ).also { blurVerticalShader = it }
    }
    val progressiveMask = key.progressive?.toShader(key.maskSize)
    val horizontal = horizontalShader.updateUniforms {
      setBlurUniforms(
        key = key,
        kernel = key.plan.horizontalKernel,
        sampleWidth = key.plan.workingSize.width,
        sampleHeight = key.plan.workingSize.height,
      )
      progressiveMask?.let { setChildShader("mask", it) }
    }
    val vertical = verticalShader.updateUniforms {
      setBlurUniforms(
        key = key,
        kernel = key.plan.verticalKernel,
        sampleWidth = key.plan.workingSize.width,
        sampleHeight = key.plan.workingSize.height,
      )
      progressiveMask?.let { setChildShader("mask", it) }
    }
    val prefilter = key.plan.takeIf { it.requiresPrefilter }?.let { plan ->
      val shader = blurPrefilterShader ?: createGlassBlurPrefilterRenderEffect().also {
        blurPrefilterShader = it
      }
      shader.updateUniforms {
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
      horizontal = horizontal,
      vertical = vertical,
    )
  }
}

internal data class GlassStageRecordCounts(
  val source: Int,
  val blur: Int,
  val depth: Int,
  val optical: Int,
  val detail: Int,
  val rim: Int,
)

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

internal expect fun createGlassBlurRenderEffect(
  horizontal: Boolean,
  progressive: Boolean,
): MutableRuntimeShaderRenderEffect

internal expect fun createGlassBlurPrefilterRenderEffect(): MutableRuntimeShaderRenderEffect

internal expect fun createGlassOpticalRenderEffect(): MutableRuntimeShaderRenderEffect

internal expect fun createRefractionDetailRenderEffect(): MutableRuntimeShaderRenderEffect

internal expect fun createGlassRimRenderEffect(): MutableRuntimeShaderRenderEffect

internal fun createSharedGlassBlurRenderEffect(
  horizontal: Boolean,
  progressive: Boolean,
): MutableRuntimeShaderRenderEffect = createMutableRuntimeShaderRenderEffect(
  effect = if (!progressive) {
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
)

internal fun createSharedGlassBlurPrefilterRenderEffect(): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_DOWNSAMPLE_PREFILTER_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

internal fun createSharedGlassOpticalRenderEffect(): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_OPTICAL_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

internal fun createSharedRefractionDetailRenderEffect(): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_REFRACTION_DETAIL_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

internal fun createSharedGlassRimRenderEffect(): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_RIM_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

private fun RuntimeShaderUniformProvider.setBlurUniforms(
  key: GlassBlurEffectKey,
  kernel: SemanticBlurKernel,
  sampleWidth: Int,
  sampleHeight: Int,
) {
  setFloatUniform("sampleSize", sampleWidth.toFloat(), sampleHeight.toFloat())
  setFloatUniform(
    "materialOrigin",
    key.maskOrigin.x,
    key.maskOrigin.y,
  )
  if (key.progressive != null) {
    setFloatUniform("maskCoordinateScale", key.maskCoordinateScale)
  }
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
  if (!size.isDrawable()) {
    onUnavailable()
    return null
  }
  return size
}
