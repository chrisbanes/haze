// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.MutableRuntimeShaderRenderEffect
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.PlatformRuntimeEffect
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.asComposeRenderEffect
import dev.chrisbanes.haze.createBlendRenderEffect
import dev.chrisbanes.haze.createBlurRenderEffect
import dev.chrisbanes.haze.createMutableRuntimeShaderRenderEffect
import dev.chrisbanes.haze.createOffsetRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.createScaledContentLayer
import dev.chrisbanes.haze.createShaderRenderEffect
import dev.chrisbanes.haze.drawScaledContent
import dev.chrisbanes.haze.trace
import dev.chrisbanes.haze.translate
import dev.chrisbanes.haze.wrapRuntimeShaderConstruction

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class RuntimeShaderGlassDelegate(
  private val effect: GlassRuntimeEffect,
  private val runtimeEffectFactory: GlassRuntimeEffectFactory = PlatformGlassRuntimeEffectFactory,
) : GlassRuntimeEffect.Delegate {
  internal val runtimeEffectFactoryForTest: GlassRuntimeEffectFactory
    get() = runtimeEffectFactory

  /** Reused by every merged-field uniform upload; the shaders copy each array as it is set. */
  private val mergedFieldScratch = MergedFieldUniformScratch()
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
  private var opticalKey: GlassOpticalEffectKey? = null
  internal var opticalShader: MutableRuntimeShaderRenderEffect? = null
    private set
  internal var opticalEffect: PlatformRenderEffect? = null
    private set
  private var refractionDetailKey: GlassRefractionDetailEffectKey? = null
  internal var refractionDetailShader: MutableRuntimeShaderRenderEffect? = null
    private set
  private var refractionDetailEffect: PlatformRenderEffect? = null
  private var refractionDetailCoverageShader: MutableRuntimeShaderRenderEffect? = null
  private var refractionDetailCoverageEffect: PlatformRenderEffect? = null
  private var rimKey: GlassRimEffectKey? = null
  internal var rimShader: MutableRuntimeShaderRenderEffect? = null
    private set
  internal var rimBrushProvider: GlassRimBrushProvider? = null
    private set
  private var rimBrushProviderInitialized = false
  private var rimBrushProviderMerged = false
  private var mergedBackgroundShaderProvider: GlassMergedBackgroundShaderProvider? = null
  private var mergedBackgroundShaderProviderInitialized = false
  private var stageShadersMerged = false
  internal var rimEffect: PlatformRenderEffect? = null
    private set
  private var fusedEffectKey: GlassFusedEffectKey? = null
  internal var fusedShader: MutableRuntimeShaderRenderEffect? = null
    private set
  private var fusedInputKey: GlassDepthInputKey? = null
  private var fusedDepthInputShaders: FusedDepthInputShaders? = null
  private var fusedEffect: PlatformRenderEffect? = null
  internal val interactionOptical = interactionSlot<GlassOpticalEffectKey> {
    GLASS_INTERACTION_OPTICAL_EFFECT
  }
  internal val interactionDetail = interactionSlot<GlassRefractionDetailEffectKey> {
    GLASS_INTERACTION_REFRACTION_DETAIL_EFFECT
  }
  internal val interactionDetailCoverage = interactionSlot<GlassRefractionDetailEffectKey> {
    GLASS_INTERACTION_REFRACTION_DETAIL_COVERAGE_EFFECT
  }
  internal val interactionLighting = interactionSlot<GlassInteractionLightingKey> {
    if (stageShadersMerged) GLASS_MERGED_INTERACTION_LIGHTING_EFFECT else GLASS_INTERACTION_LIGHTING_EFFECT
  }
  internal var interactionOutputEffect: MutableRuntimeShaderRenderEffect? = null
    private set
  private var interactionOutputInput: PlatformRenderEffect? = null
  private var interactionOutputUniforms: GlassInteractionUniforms? = null
  private var interactionOutputFeatherWidth: Float = Float.NaN
  private var interactionOutputComposeEffect: RenderEffect? = null
  internal val recordedInteractionOptical = RecordedInteractionLayer<GlassOpticalEffectKey>()
  internal val recordedInteractionDetail = RecordedInteractionLayer<GlassRefractionDetailEffectKey>()
  internal val recordedInteractionDetailCoverage =
    RecordedInteractionLayer<GlassRefractionDetailEffectKey>()
  private var recordedInteractionCompositeLayer: GraphicsLayer? = null
  private var recordedInteractionCompositeOptical: GraphicsLayer? = null
  private var recordedInteractionCompositeDetail: GraphicsLayer? = null
  private var recordedInteractionCompositeCoverage: GraphicsLayer? = null
  private var recordedInteractionCompositeSize: IntSize? = null
  private var recordedInteractionCompositeOpticalRecordCount: Int = -1
  private var recordedInteractionCompositeDetailRecordCount: Int = -1
  private var recordedInteractionLightingLayer: GraphicsLayer? = null
  private var recordedInteractionLightingSize: IntSize? = null
  private var recordedRimLayer: GraphicsLayer? = null
  private var recordedRimKey: GlassRimEffectKey? = null
  private var recordedRimSupportsDirectDrawing = false
  internal val layers = GlassLayers()
  private var graphicsContext: GraphicsContext? = null
  private var preparedRender: GlassPreparedRender? = null
  private var preparedParams: GlassRenderParams? = null
  private var preparedRenderEffects: GlassRenderEffects? = null
  private var preparedInteractionUniforms: GlassInteractionUniforms? = null
  private var preparedInteractionPatch: GlassInteractionPatch? = null
  private var preparedSourceAvailable: Boolean = false
  private var preparedStageAvailability: GlassStageAvailability? = null
  private var retainedOutputAvailable: Boolean = false
  private var backdropOutputAvailable: Boolean = false
  internal var lastSuccessfulSourceSnapshot: GlassRuntimeSourceSnapshot? = null
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
  internal var interactionLightingRecordCount: Int = 0
    private set
  internal var interactionOpticalRecordCount: Int = 0
    private set
  internal var interactionDetailRecordCount: Int = 0
    private set
  internal var interactionCompositeRecordCount: Int = 0
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

  override fun DrawScope.prepareDraw(context: HazeEffectRuntimeDrawScope) {
    backdropOutputAvailable = false
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
    val currentRenderEffects = trace(GlassTraceSection.PrepareEffects) {
      getRenderEffects(currentPreparedRender)
    }
    val interactionPatch = if (
      !supportsFusedGlassRenderEffect || currentPreparedRender.interactionTopology.hasLighting
    ) {
      resolveGlassInteractionPatch(
        params = params,
        uniforms = interactionUniforms,
        topology = currentPreparedRender.interactionTopology,
      )
    } else {
      null
    }

    trace(GlassTraceSection.PrepareLayers) {
      val scaledSize = params.coordinates.sampleSize.roundToIntSize()
      val currentGraphicsContext = context.requireGraphicsContext()
      graphicsContext = currentGraphicsContext
      if (layers.scaledSize != scaledSize) {
        clearRimLayerMetadata()
        layers.scaledSize = scaledSize
        clearInteractionLayerMetadata()
        clearRetainedMetadata()
      }

      refreshPreparedAvailability(params, currentRenderEffects)

      val blurRequired = !supportsFusedGlassRenderEffect && shouldBlur(params, currentRenderEffects)
      val depthMixRequired =
        !supportsFusedGlassRenderEffect && shouldDepthMix(params, currentRenderEffects)
      val refractionDetailRequired = !supportsFusedGlassRenderEffect &&
        currentRenderEffects.refractionDetail != null
      val rimRequired = currentRenderEffects.rim != null
      val interactionOpticsRequired =
        !supportsFusedGlassRenderEffect && currentPreparedRender.interactionTopology.hasOptics
      val interactionDetailRequired = interactionOpticsRequired && refractionDetailRequired
      val interactionLightingRequired = currentPreparedRender.interactionTopology.hasLighting

      // Drop stages which are no longer part of the graph before allocating their replacements.
      // This keeps topology transitions from temporarily retaining both complete graphs.
      if (blurRequired) {
        val blurEffects = checkNotNull(currentRenderEffects.blur)
        if (blurEffects.native != null) {
          layers.releaseBlurIntermediates(currentGraphicsContext)
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
        clearRimLayerMetadata()
      }
      releaseObsoleteInteractionLayers(
        opticsRequired = interactionOpticsRequired,
        detailRequired = interactionDetailRequired,
        lightingRequired = interactionLightingRequired,
        graphicsContext = currentGraphicsContext,
      )
      layers.groupAlpha.prepare(
        required = currentPreparedRender.groupCompositeSize != null,
        graphicsContext = currentGraphicsContext,
      )

      layers.ensureSource(currentGraphicsContext)
      if (blurRequired) {
        val blurEffects = checkNotNull(currentRenderEffects.blur)
        if (blurEffects.native == null) {
          layers.ensureBlurHorizontal(currentGraphicsContext)
        }
        layers.ensureBlurred(currentGraphicsContext)
      }
      if (depthMixRequired) {
        layers.ensureDepthMixed(currentGraphicsContext)
      }
      layers.ensureOptical(currentGraphicsContext)
      if (refractionDetailRequired) {
        layers.ensureRefractionDetail(currentGraphicsContext)
        layers.ensureRefractionDetailCoverage(currentGraphicsContext)
        layers.ensureRefractionComposite(currentGraphicsContext)
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
    }
    prepareInteractionRenderEffects(
      render = currentPreparedRender,
      effects = currentRenderEffects,
      patch = interactionPatch,
      params = params,
    )
    preparedRender = currentPreparedRender
    preparedParams = params
    preparedRenderEffects = currentRenderEffects
    preparedInteractionUniforms = interactionUniforms
    preparedInteractionPatch = interactionPatch
  }

  override val usesRuntimeShader: Boolean get() = true

  override fun DrawScope.prepareBackdrop(
    context: HazeEffectRuntimeDrawScope,
  ): PlatformRenderEffect? {
    val currentPreparedRender = effect.preparedRender
    if (
      !supportsFusedGlassRenderEffect ||
      effect.preparedRenderBudget !is GlassRenderBudgetDecision.Runtime ||
      currentPreparedRender == null
    ) {
      releaseRetainedResources()
      return null
    }
    val params = currentPreparedRender.params
    val interactionUniforms = currentPreparedRender.interactionUniforms
    val currentRenderEffects = trace(GlassTraceSection.PrepareEffects) {
      getFusedRenderEffects(currentPreparedRender, backdrop = true)
    }
    val interactionPatch = if (currentPreparedRender.interactionTopology.hasLighting) {
      resolveGlassInteractionPatch(
        params = params,
        uniforms = interactionUniforms,
        topology = currentPreparedRender.interactionTopology,
      )
    } else {
      null
    }

    val currentGraphicsContext = context.requireGraphicsContext()
    val scaledSize = params.coordinates.sampleSize.roundToIntSize()
    graphicsContext = currentGraphicsContext
    if (layers.scaledSize != scaledSize) {
      layers.scaledSize = scaledSize
      clearRimLayerMetadata()
      clearInteractionLayerMetadata()
      clearRetainedMetadata()
    }
    layers.prepareBackdrop(
      rim = currentRenderEffects.rim != null,
      interactionLighting = currentPreparedRender.interactionTopology.hasLighting,
      graphicsContext = currentGraphicsContext,
    )
    if (currentRenderEffects.rim == null) {
      clearRimLayerMetadata()
    }
    clearRetainedMetadata()
    prepareInteractionRenderEffects(
      render = currentPreparedRender,
      effects = currentRenderEffects,
      patch = interactionPatch,
      params = params,
    )
    preparedRender = currentPreparedRender
    preparedParams = params
    preparedRenderEffects = currentRenderEffects
    preparedInteractionUniforms = interactionUniforms
    preparedInteractionPatch = interactionPatch

    if (recordRimIfNeeded(currentPreparedRender, currentRenderEffects) == null) return null
    if (currentPreparedRender.interactionTopology.hasLighting) {
      val patch = interactionPatch ?: return null
      recordInteractionLighting(
        key = resolveGlassInteractionLightingKey(
          previous = interactionLighting.key,
          coordinates = patch.coordinates,
          edgeSoftnessPx = params.edgeSoftnessPx,
          cornerRadii = params.cornerRadii,
          mergedField = params.mergedField,
        ),
        patch = patch,
      ) ?: return null
    }
    backdropOutputAvailable = true
    return currentRenderEffects.optical
  }

  private fun prepareInteractionRenderEffects(
    render: GlassPreparedRender,
    effects: GlassRenderEffects,
    patch: GlassInteractionPatch?,
    params: GlassRenderParams,
  ) {
    if (patch == null) return
    if (!supportsFusedGlassRenderEffect && render.interactionTopology.hasOptics) {
      val opticalLayer = checkNotNull(layers.interactionOptical)
      updateInteractionOpticalEffect(
        opticalLayer,
        render.opticalKey.copy(coordinates = patch.coordinates),
        patch.uniforms,
        outputTargetsLayer = effects.refractionDetail == null,
      )
      val detail = effects.refractionDetail
      val outputLayer = if (detail != null) {
        val localDetailKey = detail.key.localTo(patch.coordinates)
        updateInteractionDetailEffect(
          checkNotNull(layers.interactionRefractionDetail),
          localDetailKey,
          patch.uniforms,
        )
        updateInteractionDetailCoverageEffect(
          checkNotNull(layers.interactionRefractionDetailCoverage),
          localDetailKey,
          patch.uniforms,
        )
        checkNotNull(layers.interactionRefractionComposite)
      } else {
        opticalLayer
      }
      updateInteractionOutputEffect(
        layer = outputLayer,
        input = if (detail == null) checkNotNull(interactionOptical.platformEffect) else null,
        patch = patch,
        featherWidth = calculateGlassInteractionOutputFeatherWidth(
          radiusPx = patch.uniforms.radiusPx,
          sampleStepPx = params.sampleStepPx,
        ),
      )
    }
    if (render.interactionTopology.hasLighting) {
      updateInteractionLightingEffect(
        layer = checkNotNull(layers.interactionLighting),
        key = resolveGlassInteractionLightingKey(
          previous = interactionLighting.key,
          coordinates = patch.coordinates,
          edgeSoftnessPx = params.edgeSoftnessPx,
          cornerRadii = params.cornerRadii,
          mergedField = params.mergedField,
        ),
        uniforms = patch.uniforms,
      )
    }
  }

  private fun releaseObsoleteInteractionLayers(
    opticsRequired: Boolean,
    detailRequired: Boolean,
    lightingRequired: Boolean,
    graphicsContext: GraphicsContext,
  ) {
    if (!opticsRequired) {
      layers.releaseInteractionOptical(graphicsContext)
      clearInteractionOpticalLayerMetadata()
    }
    if (!detailRequired) {
      layers.releaseInteractionRefractionDetail(graphicsContext)
      clearInteractionRefractionLayerMetadata()
    }
    if (!lightingRequired) {
      layers.releaseInteractionLighting(graphicsContext)
      clearInteractionLightingLayerMetadata()
    }
  }

  override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope): Unit =
    trace(GlassTraceSection.RuntimeDraw) {
      val render = preparedRender ?: return
      val params = preparedParams ?: return
      val effects = preparedRenderEffects ?: return
      val interactionUniforms = preparedInteractionUniforms ?: return
      val interactionPatch = preparedInteractionPatch
      if (render.alpha <= 0f) {
        retainedOutputAvailable = false
        return
      }
      requireDrawableMaterialSize(params.coordinates.materialSize, ::clearRetainedOutput) ?: return
      var completed = false
      try {
        val previousInputs = lastSuccessfulStageInputs
        val currentInputs = resolveGlassStageInputs(
          previous = previousInputs,
          blur = if (supportsFusedGlassRenderEffect) {
            render.blurKey
          } else {
            render.blurKey?.takeIf { shouldBlur(params, effects) }
          },
          depth = params.depth,
          optical = render.opticalKey,
          opticalInteraction = interactionUniforms.takeIf {
            supportsFusedGlassRenderEffect && render.interactionTopology.hasOptics
          },
          detail = render.refractionDetailKey,
          rim = render.rimKey,
        )
        val sourceState = context.resolveGlassRuntimeSourceState(
          captureScale = params.coordinates.scaleFactor,
          backgroundColor = params.sourceBackgroundColor,
          previousSnapshot = lastSuccessfulSourceSnapshot,
          mergedBackground = params.mergedBackground,
        )
        if (
          !sourceState.hasDrawableSource && (
            lastSuccessfulSourceSnapshot?.backgroundColor != params.sourceBackgroundColor ||
              lastSuccessfulSourceSnapshot?.mergedBackground != params.mergedBackground
            )
        ) {
          clearRetainedOutput()
          return
        }
        val shouldRecordSource = sourceState.hasDrawableSource && (
          sourceState.snapshot == null ||
            sourceState.snapshot != lastSuccessfulSourceSnapshot ||
            !preparedSourceAvailable ||
            !retainedOutputAvailable
          )
        val source = requireRetainedStage(
          if (shouldRecordSource) {
            trace(GlassTraceSection.Source) { recordSource(context, params) }
          } else {
            retainedSource()
          },
          ::clearRetainedOutput,
        ) ?: return
        val invalidation = calculateRequiredStageInvalidation(
          previous = previousInputs,
          current = currentInputs,
          sourceChanged = shouldRecordSource,
          availability = preparedStageAvailability ?: stageAvailability(params, effects),
        )
        if (supportsFusedGlassRenderEffect) {
          val baseInputsChanged = previousInputs == null || !previousInputs.hasSameBaseAs(currentInputs)
          val completedOptical = requireRetainedStage(
            if (
              shouldRecordSource ||
              baseInputsChanged ||
              !retainedOutputAvailable
            ) {
              trace(GlassTraceSection.Optical) {
                recordFusedOutput(
                  source = source,
                  params = params,
                  renderEffect = effects.optical,
                )
              }
            } else {
              layers.optical?.takeUnless { it.isReleased }
            },
            ::clearRetainedOutput,
          ) ?: return
          requireRetainedStage(
            if (invalidation.rim) {
              trace(GlassTraceSection.Rim) { recordRimIfNeeded(render, effects) }
            } else {
              retainedRim(effects)
            },
            ::clearRetainedOutput,
          ) ?: return
          if (interactionUniforms.hasLighting && interactionPatch != null) {
            requireRetainedStage(
              trace(GlassTraceSection.InteractionLighting) {
                recordInteractionLighting(
                  key = resolveGlassInteractionLightingKey(
                    previous = interactionLighting.key,
                    coordinates = interactionPatch.coordinates,
                    edgeSoftnessPx = params.edgeSoftnessPx,
                    cornerRadii = params.cornerRadii,
                    mergedField = params.mergedField,
                  ),
                  patch = interactionPatch,
                )
              },
              ::clearRetainedOutput,
            ) ?: return
          }
          if (render.alpha >= 1f) {
            trace(GlassTraceSection.Compose) {
              drawCompletedLayer(completedOptical, context, params, alpha = 1f)
            }
          } else {
            val groupAlpha = requireRetainedStage(
              layers.groupAlpha.layer?.takeUnless { it.isReleased },
              ::clearRetainedOutput,
            ) ?: return
            val groupCompositeSize = requireRetainedStage(
              render.groupCompositeSize,
              ::clearRetainedOutput,
            ) ?: return
            trace(GlassTraceSection.GroupAlpha) {
              recordAndDrawGlassGroupAlpha(
                layer = groupAlpha,
                alpha = render.alpha,
                size = groupCompositeSize,
              ) {
                trace(GlassTraceSection.Compose) {
                  drawCompletedLayer(completedOptical, context, params, alpha = 1f)
                }
              }
            }
          }
          if (shouldRecordSource) {
            lastSuccessfulSourceSnapshot = sourceState.snapshot
          }
          lastSuccessfulStageInputs = currentInputs
          retainedOutputAvailable = true
          refreshPreparedAvailability(params, effects)
          completed = true
          return
        }
        val blurred = requireRetainedStage(
          if (invalidation.blur) {
            trace(GlassTraceSection.Blur) {
              recordBlurredIfNeeded(source, params, effects)
            }
          } else {
            retainedBlurInput(source, params, effects)
          },
          ::clearRetainedOutput,
        ) ?: return
        val depthInput = requireRetainedStage(
          if (invalidation.depth) {
            trace(GlassTraceSection.Depth) {
              recordDepthInput(source, blurred, params.depth)
            }
          } else {
            retainedDepthInput(source, blurred, params.depth)
          },
          ::clearRetainedOutput,
        ) ?: return
        val optical = requireRetainedStage(
          if (invalidation.optical) {
            trace(GlassTraceSection.Optical) {
              recordOptical(
                input = depthInput,
                params = params,
                effects = effects,
              )
            }
          } else {
            layers.optical
          },
          ::clearRetainedOutput,
        ) ?: return
        val refractionDetail = effects.refractionDetail?.let { detail ->
          requireRetainedStage(
            if (invalidation.detail) {
              trace(GlassTraceSection.Detail) {
                recordRefractionDetail(source, params, detail)
              }
            } else {
              layers.refractionDetail?.takeUnless { layer -> layer.isReleased }
            },
            ::clearRetainedOutput,
          ) ?: return
        }
        val refractionDetailCoverage = effects.refractionDetail?.let {
          requireRetainedStage(
            if (invalidation.detail) {
              trace(GlassTraceSection.Detail) {
                recordRefractionDetailCoverage(source, params, it)
              }
            } else {
              layers.refractionDetailCoverage?.takeUnless { layer -> layer.isReleased }
            },
            ::clearRetainedOutput,
          ) ?: return
        }
        val completedOptical = if (
          refractionDetail != null && refractionDetailCoverage != null
        ) {
          requireRetainedStage(
            if (invalidation.optical || invalidation.detail) {
              trace(GlassTraceSection.Optical) {
                recordRefractionComposite(
                  optical = optical,
                  detail = refractionDetail,
                  coverage = refractionDetailCoverage,
                  params = params,
                )
              }
            } else {
              layers.refractionComposite?.takeUnless { layer -> layer.isReleased }
            },
            ::clearRetainedOutput,
          ) ?: return
        } else {
          optical
        }
        requireRetainedStage(
          if (invalidation.rim) {
            trace(GlassTraceSection.Rim) { recordRimIfNeeded(render, effects) }
          } else {
            retainedRim(effects)
          },
          ::clearRetainedOutput,
        ) ?: return
        val interactionOpticalLayer = if (
          interactionUniforms.hasOptics && interactionPatch != null
        ) {
          requireRetainedStage(
            trace(GlassTraceSection.InteractionOptical) {
              recordInteractionOptical(
                input = depthInput,
                key = render.opticalKey,
                patch = interactionPatch,
                inputContentChanged = invalidation.depth,
                outputTargetsLayer = effects.refractionDetail == null,
              )
            },
            ::clearRetainedOutput,
          ) ?: return
        } else {
          null
        }
        val interactionRefractionDetail = if (
          interactionUniforms.hasOptics &&
          effects.refractionDetail != null &&
          interactionPatch != null
        ) {
          requireRetainedStage(
            trace(GlassTraceSection.InteractionDetail) {
              recordInteractionRefractionDetail(
                input = source,
                key = effects.refractionDetail.key,
                patch = interactionPatch,
                inputContentChanged = shouldRecordSource,
              )
            },
            ::clearRetainedOutput,
          ) ?: return
        } else {
          null
        }
        val interactionRefractionDetailCoverage = if (
          interactionUniforms.hasOptics &&
          effects.refractionDetail != null &&
          interactionPatch != null
        ) {
          requireRetainedStage(
            trace(GlassTraceSection.InteractionDetail) {
              recordInteractionRefractionDetailCoverage(
                input = source,
                key = effects.refractionDetail.key,
                patch = interactionPatch,
                inputContentChanged = shouldRecordSource,
              )
            },
            ::clearRetainedOutput,
          ) ?: return
        } else {
          null
        }
        val completedInteractionOutput = if (
          interactionOpticalLayer != null &&
          interactionRefractionDetail != null &&
          interactionRefractionDetailCoverage != null &&
          interactionPatch != null
        ) {
          requireRetainedStage(
            trace(GlassTraceSection.InteractionDetail) {
              recordInteractionRefractionComposite(
                optical = interactionOpticalLayer,
                detail = interactionRefractionDetail,
                coverage = interactionRefractionDetailCoverage,
                patch = interactionPatch,
              )
            },
            ::clearRetainedOutput,
          ) ?: return
        } else {
          interactionOpticalLayer
        }
        if (completedInteractionOutput != null && interactionPatch != null) {
          updateInteractionOutputEffect(
            layer = completedInteractionOutput,
            input = if (completedInteractionOutput === interactionOpticalLayer) {
              checkNotNull(interactionOptical.platformEffect)
            } else {
              null
            },
            patch = interactionPatch,
            featherWidth = calculateGlassInteractionOutputFeatherWidth(
              radiusPx = interactionPatch.uniforms.radiusPx,
              sampleStepPx = params.sampleStepPx,
            ),
          )
        }
        if (interactionUniforms.hasLighting && interactionPatch != null) {
          requireRetainedStage(
            trace(GlassTraceSection.InteractionLighting) {
              recordInteractionLighting(
                key = resolveGlassInteractionLightingKey(
                  previous = interactionLighting.key,
                  coordinates = interactionPatch.coordinates,
                  edgeSoftnessPx = params.edgeSoftnessPx,
                  cornerRadii = params.cornerRadii,
                  mergedField = params.mergedField,
                ),
                patch = interactionPatch,
              )
            },
            ::clearRetainedOutput,
          ) ?: return
        }
        if (render.alpha >= 1f && completedInteractionOutput == null) {
          trace(GlassTraceSection.Compose) {
            drawCompletedOutput(
              optical = completedOptical,
              interactionOutput = completedInteractionOutput,
              interactionPatch = interactionPatch,
              context = context,
              params = params,
            )
          }
        } else {
          val groupAlpha = requireRetainedStage(
            layers.groupAlpha.layer?.takeUnless { it.isReleased },
            ::clearRetainedOutput,
          ) ?: return
          val groupCompositeSize = requireRetainedStage(
            render.groupCompositeSize,
            ::clearRetainedOutput,
          ) ?: return
          trace(GlassTraceSection.GroupAlpha) {
            recordAndDrawGlassGroupAlpha(
              layer = groupAlpha,
              alpha = render.alpha,
              size = groupCompositeSize,
            ) {
              trace(GlassTraceSection.Compose) {
                drawCompletedOutput(
                  optical = completedOptical,
                  interactionOutput = completedInteractionOutput,
                  interactionPatch = interactionPatch,
                  context = context,
                  params = params,
                )
              }
            }
          }
        }
        if (shouldRecordSource) {
          lastSuccessfulSourceSnapshot = sourceState.snapshot
        }
        lastSuccessfulStageInputs = currentInputs
        retainedOutputAvailable = true
        refreshPreparedAvailability(params, effects)
        completed = true
      } finally {
        if (!completed) {
          clearRetainedOutput()
        }
      }
    }

  override fun DrawScope.drawForeground(context: HazeEffectRuntimeDrawScope): Unit =
    trace(GlassTraceSection.Compose) {
      val render = preparedRender ?: return
      val params = preparedParams ?: return
      requireDrawableMaterialSize(params.coordinates.materialSize, ::clearRetainedOutput) ?: return
      if (!retainedOutputAvailable && !backdropOutputAvailable) return
      // Rim and interaction lighting must draw above this node's content. With group alpha, their
      // independent alpha is an approximation of full-group composition and can slightly
      // double-lighten where they overlap the glass.
      preparedInteractionUniforms?.takeIf { it.hasLighting }?.let {
        val patch = preparedInteractionPatch ?: return@let
        layers.interactionLighting?.takeUnless { layer -> layer.isReleased }?.let { layer ->
          drawCompletedPatch(
            layer = layer,
            patch = patch,
            context = context,
            params = params,
            blendMode = BlendMode.SrcOver,
            alpha = render.alpha,
          )
        }
      }
      layers.rim?.takeUnless { it.isReleased }?.let { rim ->
        if (supportsGlassRimBrush() != recordedRimSupportsDirectDrawing) {
          val effects = preparedRenderEffects ?: return
          recordRimIfNeeded(render, effects) ?: return
        }
        drawCompletedLayer(rim, context, params, alpha = render.alpha)
      }
    }

  override fun canDrawRetainedOutput(): Boolean {
    val fusedOutputAvailable = supportsFusedGlassRenderEffect
    val detailRequired = preparedRenderEffects?.refractionDetail != null ||
      preparedRenderEffects == null && lastSuccessfulStageInputs?.detail != null
    val interactionUniforms = preparedInteractionUniforms
    val interactionPatchAvailable = preparedInteractionPatch != null
    val interactionOpticsRequired = !fusedOutputAvailable &&
      interactionPatchAvailable &&
      interactionUniforms?.hasOptics == true
    val interactionDetailRequired = interactionOpticsRequired && detailRequired
    val interactionLightingRequired = interactionPatchAvailable &&
      interactionUniforms?.hasLighting == true
    return retainedOutputAvailable && layers.hasOptical &&
      (
        !(requiresGlassGroupAlpha(preparedRender?.alpha ?: 1f) || interactionOpticsRequired) ||
          layers.groupAlpha.isAvailable
        ) &&
      (fusedOutputAvailable || !detailRequired || layers.hasRefractionComposite) &&
      (!interactionOpticsRequired || layers.hasInteractionOptical) &&
      (
        !interactionDetailRequired || layers.hasInteractionRefractionDetail &&
          layers.hasInteractionRefractionDetailCoverage &&
          layers.hasInteractionRefractionComposite
        ) &&
      (!interactionLightingRequired || layers.hasInteractionLighting)
  }

  override fun releaseObsoleteInteractionOutput(topology: GlassInteractionTopology) {
    val context = graphicsContext ?: return
    val opticsRequired = !supportsFusedGlassRenderEffect && topology.hasOptics
    val detailRequired = opticsRequired && lastSuccessfulStageInputs?.detail != null
    // A composite recorded with interaction optics still references the outgoing patch.
    if (!opticsRequired && layers.interactionOptical != null) {
      layers.groupAlpha.release(context)
    }
    releaseObsoleteInteractionLayers(
      opticsRequired = opticsRequired,
      detailRequired = detailRequired,
      lightingRequired = topology.hasLighting,
      graphicsContext = context,
    )
    if (!opticsRequired) clearInteractionOpticalEffectCache()
    if (!detailRequired) clearInteractionDetailEffectCache()
    if (!topology.hasLighting) clearInteractionLightingEffectCache()
    clearPreparedRender()
  }

  private fun clearPreparedRender() {
    preparedRender = null
    preparedParams = null
    preparedRenderEffects = null
    preparedInteractionUniforms = null
    preparedInteractionPatch = null
    preparedStageAvailability = null
  }

  override fun invalidateRetainedOutput() {
    clearRetainedMetadata()
    clearRimLayerMetadata()
    clearInteractionLayerMetadata()
    clearPreparedRender()
    preparedSourceAvailable = false
  }

  override fun clearRetainedOutput() {
    releaseRetainedResources(releaseShaderHandles = false)
  }

  private fun clearRetainedMetadata() {
    retainedOutputAvailable = false
    backdropOutputAvailable = false
    lastSuccessfulSourceSnapshot = null
    lastSuccessfulStageInputs = null
  }

  private fun clearInteractionLayerMetadata() {
    clearInteractionOpticalLayerMetadata()
    clearInteractionRefractionLayerMetadata()
    clearInteractionLightingLayerMetadata()
    clearInteractionOpticalEffectCache()
    clearInteractionDetailEffectCache()
    clearInteractionLightingEffectCache()
  }

  private fun clearInteractionOpticalEffectCache() {
    interactionOptical.clear()
    // Its shader still holds the previous optical input, which the detail path never replaces.
    clearInteractionOutputEffectCache()
  }

  private fun clearInteractionOutputEffectCache() {
    interactionOutputEffect = null
    interactionOutputInput = null
    interactionOutputUniforms = null
    interactionOutputFeatherWidth = Float.NaN
    interactionOutputComposeEffect = null
  }

  private fun clearInteractionDetailEffectCache() {
    interactionDetail.clear()
    interactionDetailCoverage.clear()
  }

  private fun clearInteractionLightingEffectCache() {
    interactionLighting.clear()
  }

  private fun clearInteractionOpticalLayerMetadata() {
    recordedInteractionOptical.clear()
    interactionOptical.clearLayer()
  }

  private fun clearInteractionRefractionLayerMetadata() {
    recordedInteractionDetail.clear()
    recordedInteractionDetailCoverage.clear()
    recordedInteractionCompositeLayer = null
    recordedInteractionCompositeOptical = null
    recordedInteractionCompositeDetail = null
    recordedInteractionCompositeCoverage = null
    recordedInteractionCompositeSize = null
    recordedInteractionCompositeOpticalRecordCount = -1
    recordedInteractionCompositeDetailRecordCount = -1
    interactionDetail.clearLayer()
    interactionDetailCoverage.clearLayer()
  }

  private fun clearInteractionLightingLayerMetadata() {
    recordedInteractionLightingLayer = null
    recordedInteractionLightingSize = null
    interactionLighting.clearLayer()
  }

  private fun clearRimLayerMetadata() {
    recordedRimLayer = null
    recordedRimKey = null
    recordedRimSupportsDirectDrawing = false
  }

  override fun detach() {
    releaseRetainedResources(releaseShaderHandles = false)
  }

  override fun release() {
    releaseRetainedResources(releaseShaderHandles = true)
  }

  override fun onTrimMemory(context: HazeEffectLifecycleScope, level: TrimMemoryLevel) {
    if (shouldReleaseRetainedGlass(level)) {
      releaseRetainedResources(graphicsContext ?: context.requireGraphicsContext())
      context.invalidateDraw()
    }
  }

  private fun releaseRetainedResources(
    releaseContext: GraphicsContext? = graphicsContext,
    releaseShaderHandles: Boolean = true,
  ) {
    layers.release(releaseContext)
    graphicsContext = null
    blurKey = null
    blurEffects = null
    opticalKey = null
    opticalEffect = null
    refractionDetailKey = null
    refractionDetailEffect = null
    refractionDetailCoverageEffect = null
    rimKey = null
    rimEffect = null
    fusedEffectKey = null
    fusedEffect = null
    if (releaseShaderHandles) {
      blurHorizontalShader = null
      blurVerticalShader = null
      progressiveBlurHorizontalShader = null
      progressiveBlurVerticalShader = null
      opticalShader = null
      refractionDetailShader = null
      refractionDetailCoverageShader = null
      rimShader = null
      rimBrushProvider = null
      rimBrushProviderInitialized = false
      mergedBackgroundShaderProvider = null
      mergedBackgroundShaderProviderInitialized = false
      fusedShader = null
      fusedInputKey = null
      fusedDepthInputShaders = null
      interactionOptical.releaseShader()
      interactionDetail.releaseShader()
      interactionDetailCoverage.releaseShader()
      interactionLighting.releaseShader()
      interactionOutputEffect = null
    }
    clearInteractionLayerMetadata()
    clearPreparedRender()
    preparedSourceAvailable = false
    clearRimLayerMetadata()
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
  ): GraphicsLayer? = depthPassThrough(source, blurred, depth)
    ?: layers.depthMixed?.takeUnless { it.isReleased }

  private fun retainedRim(effects: GlassRenderEffects): Unit? = when {
    effects.rim == null -> Unit
    layers.hasRim -> Unit
    else -> null
  }

  private fun refreshPreparedAvailability(params: GlassRenderParams, effects: GlassRenderEffects) {
    preparedSourceAvailable = layers.hasSource
    preparedStageAvailability = if (supportsFusedGlassRenderEffect) {
      GlassStageAvailability(
        blur = true,
        depth = true,
        optical = layers.hasOptical,
        detail = true,
        rim = effects.rim == null || layers.hasRim,
      )
    } else {
      stageAvailability(params = params, effects = effects)
    }
  }

  private fun stageAvailability(
    params: GlassRenderParams,
    effects: GlassRenderEffects,
  ): GlassStageAvailability {
    val blur = effects.blur?.takeIf { shouldBlur(params, effects) }
    return GlassStageAvailability(
      blur = blur == null ||
        layers.hasBlurred && (
          blur.native != null ||
            layers.hasBlurHorizontal
          ),
      depth = !shouldDepthMix(params, effects) ||
        layers.hasDepthMixed,
      optical = layers.hasOptical,
      detail = effects.refractionDetail == null ||
        layers.hasRefractionDetail && layers.hasRefractionDetailCoverage &&
        layers.hasRefractionComposite,
      rim = effects.rim == null || layers.hasRim,
    )
  }

  private fun DrawScope.recordSource(
    context: HazeEffectRuntimeDrawScope,
    params: GlassRenderParams,
  ): GraphicsLayer? {
    if (!context.hasDrawableInput) return retainedSource()

    return createScaledContentLayer(
      context = context,
      scaleFactor = params.coordinates.scaleFactor,
      layerSize = context.layerSize,
      existingLayer = layers.source,
      backgroundColor = params.sourceBackgroundColor,
      // Produced inside the recorded block, so a replay sets this delegate's uniforms again.
      backgroundShader = params.mergedBackground?.let { field ->
        val materialOrigin = params.coordinates.materialOrigin
        { mergedBackgroundShader(materialOrigin, field) }
      },
    )?.also {
      layers.source = it
      sourceRecordCount++
    }
  }

  private fun mergedBackgroundShader(materialOrigin: Offset, field: GlassMergedField): Shader? {
    if (!mergedBackgroundShaderProviderInitialized) {
      mergedBackgroundShaderProvider = createGlassMergedBackgroundShaderProvider()
      mergedBackgroundShaderProviderInitialized = true
    }
    return mergedBackgroundShaderProvider?.invoke(materialOrigin, field)
  }

  private fun DrawScope.recordBlurredIfNeeded(
    source: GraphicsLayer,
    params: GlassRenderParams,
    effects: GlassRenderEffects,
  ): GraphicsLayer? {
    val blur = effects.blur
    if (!shouldBlur(params, effects) || blur == null) return source
    val plan = blur.key.plan
    source.alpha = 1f
    source.blendMode = BlendMode.SrcOver
    blur.native?.let { native ->
      return layers.blurred?.takeUnless { it.isReleased }?.also { blurred ->
        blurred.scaleX = 1f
        blurred.scaleY = 1f
        blurred.pivotOffset = Offset.Zero
        blurred.renderEffect = native.asComposeRenderEffect()
        recordGlassLayer(blurred, plan.sampleSize) { drawLayer(source) }
        blurRecordCount++
      }
    }
    val workingSize = plan.sampleSize

    val horizontal = layers.blurHorizontal?.takeUnless { it.isReleased } ?: return null
    horizontal.scaleX = 1f
    horizontal.scaleY = 1f
    horizontal.pivotOffset = Offset.Zero
    horizontal.renderEffect = checkNotNull(blur.horizontal).asComposeRenderEffect()
    recordGlassLayer(horizontal, workingSize) { drawLayer(source) }

    return layers.blurred?.takeUnless { it.isReleased }?.also { blurred ->
      blurred.scaleX = 1f
      blurred.scaleY = 1f
      blurred.pivotOffset = Offset.Zero
      blurred.renderEffect = checkNotNull(blur.vertical).asComposeRenderEffect()
      recordGlassLayer(blurred, workingSize) { drawLayer(horizontal) }
      blurRecordCount++
    }
  }

  private fun DrawScope.recordDepthInput(
    source: GraphicsLayer,
    blurred: GraphicsLayer,
    depth: Float,
  ): GraphicsLayer? {
    depthPassThrough(source, blurred, depth)?.let { return it }
    val layer = layers.depthMixed?.takeUnless { it.isReleased } ?: return null
    val scaledSize = layers.scaledSize ?: return null
    return layer.also {
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

  private fun DrawScope.recordFusedOutput(
    source: GraphicsLayer,
    params: GlassRenderParams,
    renderEffect: PlatformRenderEffect,
  ): GraphicsLayer? = layers.optical?.takeUnless { it.isReleased }?.also { layer ->
    source.alpha = 1f
    source.blendMode = BlendMode.SrcOver
    layer.alpha = 1f
    layer.blendMode = BlendMode.SrcOver
    layer.compositingStrategy = CompositingStrategy.Auto
    layer.renderEffect = renderEffect.asComposeRenderEffect()
    recordGlassLayer(layer, params.coordinates.sampleSize.roundToIntSize()) {
      drawLayer(source)
    }
    opticalRecordCount++
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
    layer.compositingStrategy = CompositingStrategy.Auto
    recordGlassLayer(layer, params.coordinates.sampleSize.roundToIntSize()) {
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
    recordGlassLayer(layer, params.coordinates.sampleSize.roundToIntSize()) {
      drawLayer(source)
    }
    detailRecordCount++
  }

  private fun DrawScope.recordRefractionDetailCoverage(
    source: GraphicsLayer,
    params: GlassRenderParams,
    detail: GlassRefractionDetailRenderEffect,
  ): GraphicsLayer? = layers.refractionDetailCoverage
    ?.takeUnless { it.isReleased }
    ?.also { layer ->
      source.alpha = 1f
      source.blendMode = BlendMode.SrcOver
      layer.alpha = 1f
      layer.blendMode = BlendMode.SrcOver
      layer.renderEffect = detail.coverageEffect.asComposeRenderEffect()
      recordGlassLayer(layer, params.coordinates.sampleSize.roundToIntSize()) {
        drawLayer(source)
      }
      detailRecordCount++
    }

  private fun DrawScope.recordRefractionComposite(
    optical: GraphicsLayer,
    detail: GraphicsLayer,
    coverage: GraphicsLayer,
    params: GlassRenderParams,
  ): GraphicsLayer? = layers.refractionComposite
    ?.takeUnless { it.isReleased }
    ?.also { layer ->
      layer.alpha = 1f
      layer.blendMode = BlendMode.SrcOver
      layer.compositingStrategy = CompositingStrategy.Offscreen
      layer.renderEffect = null
      recordGlassLayer(layer, params.coordinates.sampleSize.roundToIntSize()) {
        optical.blendMode = BlendMode.SrcOver
        drawLayer(optical)
        coverage.blendMode = BlendMode.DstOut
        drawLayer(coverage)
        detail.blendMode = BlendMode.Plus
        drawLayer(detail)
      }
      detailRecordCount++
    }

  private fun DrawScope.recordRimIfNeeded(
    render: GlassPreparedRender,
    effects: GlassRenderEffects,
  ): Unit? {
    val params = render.params
    val rimEffect = effects.rim ?: return Unit
    val layer = layers.rim?.takeUnless { it.isReleased } ?: return null
    // The prepared render already holds this key whenever the rim is visible.
    val key = render.rimKey ?: params.rimEffectKey()
    // Recording canvases may support shaders even when the destination canvas does not.
    val supportsDirectDrawing = supportsGlassRimBrush()
    layer.alpha = 1f
    if (
      layer !== recordedRimLayer ||
      key != recordedRimKey ||
      supportsDirectDrawing != recordedRimSupportsDirectDrawing
    ) {
      val merged = key.mergedField != null
      if (!rimBrushProviderInitialized || rimBrushProviderMerged != merged) {
        rimBrushProvider = createGlassRimBrushProvider(merged)
        rimBrushProviderInitialized = true
        rimBrushProviderMerged = merged
      }
      // Capture the provider: a replay must not read a field that release has since cleared.
      val drawnDirectly = recordGlassRimLayer(
        layer,
        params.coordinates.sampleSize.roundToIntSize(),
        rimBrushProvider,
        key,
        supportsDirectDrawing,
      )
      // The rim is procedural: supported canvases can draw it without an offscreen effect.
      // Keep the original RenderEffect path for canvases that cannot draw a RuntimeShader.
      layer.renderEffect = rimEffect.takeUnless { drawnDirectly }?.asComposeRenderEffect()
      recordedRimLayer = layer
      recordedRimKey = key
      recordedRimSupportsDirectDrawing = supportsDirectDrawing
      rimRecordCount++
    }
    return Unit
  }

  private fun DrawScope.recordInteractionOptical(
    input: GraphicsLayer,
    key: GlassOpticalEffectKey,
    patch: GlassInteractionPatch,
    inputContentChanged: Boolean,
    outputTargetsLayer: Boolean,
  ): GraphicsLayer? = layers.interactionOptical?.takeUnless { it.isReleased }?.also { layer ->
    val localKey = key.copy(coordinates = patch.coordinates)
    input.alpha = 1f
    input.blendMode = BlendMode.SrcOver
    layer.alpha = 1f
    updateInteractionOpticalEffect(layer, localKey, patch.uniforms, outputTargetsLayer)
    if (
      recordInteractionInput(
        layer = layer,
        input = input,
        key = localKey,
        patch = patch,
        recorded = recordedInteractionOptical,
        inputContentChanged = inputContentChanged,
      )
    ) {
      interactionOpticalRecordCount++
    }
  }

  private fun DrawScope.recordInteractionRefractionDetail(
    input: GraphicsLayer,
    key: GlassRefractionDetailEffectKey,
    patch: GlassInteractionPatch,
    inputContentChanged: Boolean,
  ): GraphicsLayer? = layers.interactionRefractionDetail
    ?.takeUnless { it.isReleased }
    ?.also { layer ->
      input.alpha = 1f
      input.blendMode = BlendMode.SrcOver
      layer.alpha = 1f
      layer.blendMode = BlendMode.SrcOver
      val localKey = key.localTo(patch.coordinates)
      updateInteractionDetailEffect(layer, localKey, patch.uniforms)
      if (
        recordInteractionInput(
          layer = layer,
          input = input,
          key = localKey,
          patch = patch,
          recorded = recordedInteractionDetail,
          inputContentChanged = inputContentChanged,
        )
      ) {
        interactionDetailRecordCount++
      }
    }

  private fun DrawScope.recordInteractionRefractionDetailCoverage(
    input: GraphicsLayer,
    key: GlassRefractionDetailEffectKey,
    patch: GlassInteractionPatch,
    inputContentChanged: Boolean,
  ): GraphicsLayer? = layers.interactionRefractionDetailCoverage
    ?.takeUnless { it.isReleased }
    ?.also { layer ->
      input.alpha = 1f
      input.blendMode = BlendMode.SrcOver
      layer.alpha = 1f
      layer.blendMode = BlendMode.SrcOver
      val localKey = key.localTo(patch.coordinates)
      updateInteractionDetailCoverageEffect(layer, localKey, patch.uniforms)
      if (
        recordInteractionInput(
          layer = layer,
          input = input,
          key = localKey,
          patch = patch,
          recorded = recordedInteractionDetailCoverage,
          inputContentChanged = inputContentChanged,
        )
      ) {
        interactionDetailRecordCount++
      }
    }

  /** Records [input] into [layer] when it changed since [recorded], and reports whether it did. */
  private fun <K : Any> DrawScope.recordInteractionInput(
    layer: GraphicsLayer,
    input: GraphicsLayer,
    key: K,
    patch: GlassInteractionPatch,
    recorded: RecordedInteractionLayer<K>,
    inputContentChanged: Boolean,
  ): Boolean {
    if (!recorded.needsRecord(layer, input, key, inputContentChanged)) return false
    val origin = patch.bounds.topLeft
    recordGlassLayer(layer, patch.bounds.size) {
      translate(Offset(-origin.x.toFloat(), -origin.y.toFloat())) { drawLayer(input) }
    }
    recorded.update(layer, input, key)
    return true
  }

  private fun DrawScope.recordInteractionRefractionComposite(
    optical: GraphicsLayer,
    detail: GraphicsLayer,
    coverage: GraphicsLayer,
    patch: GlassInteractionPatch,
  ): GraphicsLayer? = layers.interactionRefractionComposite
    ?.takeUnless { it.isReleased }
    ?.also { layer ->
      layer.alpha = 1f
      layer.blendMode = BlendMode.SrcOver
      layer.compositingStrategy = CompositingStrategy.Offscreen
      layer.renderEffect = null
      optical.blendMode = BlendMode.SrcOver
      coverage.blendMode = BlendMode.DstOut
      detail.blendMode = BlendMode.Plus
      if (
        layer !== recordedInteractionCompositeLayer ||
        optical !== recordedInteractionCompositeOptical ||
        detail !== recordedInteractionCompositeDetail ||
        coverage !== recordedInteractionCompositeCoverage ||
        patch.bounds.size != recordedInteractionCompositeSize ||
        interactionOpticalRecordCount != recordedInteractionCompositeOpticalRecordCount ||
        interactionDetailRecordCount != recordedInteractionCompositeDetailRecordCount
      ) {
        recordGlassLayer(layer, patch.bounds.size) {
          drawLayer(optical)
          drawLayer(coverage)
          drawLayer(detail)
        }
        recordedInteractionCompositeLayer = layer
        recordedInteractionCompositeOptical = optical
        recordedInteractionCompositeDetail = detail
        recordedInteractionCompositeCoverage = coverage
        recordedInteractionCompositeSize = patch.bounds.size
        recordedInteractionCompositeOpticalRecordCount = interactionOpticalRecordCount
        recordedInteractionCompositeDetailRecordCount = interactionDetailRecordCount
        interactionCompositeRecordCount++
      }
    }

  private fun DrawScope.recordInteractionLighting(
    key: GlassInteractionLightingKey,
    patch: GlassInteractionPatch,
  ): GraphicsLayer? = layers.interactionLighting?.takeUnless { it.isReleased }?.also { layer ->
    layer.alpha = 1f
    layer.blendMode = BlendMode.SrcOver
    updateInteractionLightingEffect(layer, key, patch.uniforms)
    if (
      layer !== recordedInteractionLightingLayer ||
      patch.bounds.size != recordedInteractionLightingSize
    ) {
      recordGlassLayer(layer, patch.bounds.size) {
        drawRect(Color.Black)
      }
      interactionLightingRecordCount++
      recordedInteractionLightingLayer = layer
      recordedInteractionLightingSize = patch.bounds.size
    }
  }

  private fun updateInteractionOpticalEffect(
    layer: GraphicsLayer,
    key: GlassOpticalEffectKey,
    uniforms: GlassInteractionUniforms,
    outputTargetsLayer: Boolean,
  ) {
    // The output effect replaces this layer's effect, so the optical effect is not attached.
    interactionOptical.bind(layer, key, uniforms, attach = !outputTargetsLayer) {
      setOpticalUniforms(key, mergedFieldScratch)
      setInteractionOpticalUniforms(uniforms)
    }
  }

  private fun updateInteractionDetailEffect(
    layer: GraphicsLayer,
    key: GlassRefractionDetailEffectKey,
    uniforms: GlassInteractionUniforms,
  ) {
    interactionDetail.bind(layer, key, uniforms) {
      setRefractionDetailUniforms(key, mergedFieldScratch)
      setInteractionDetailUniforms(uniforms)
    }
  }

  private fun updateInteractionDetailCoverageEffect(
    layer: GraphicsLayer,
    key: GlassRefractionDetailEffectKey,
    uniforms: GlassInteractionUniforms,
  ) {
    interactionDetailCoverage.bind(layer, key, uniforms) {
      setRefractionDetailUniforms(key, mergedFieldScratch)
      setInteractionDetailUniforms(uniforms)
    }
  }

  private fun updateInteractionLightingEffect(
    layer: GraphicsLayer,
    key: GlassInteractionLightingKey,
    uniforms: GlassInteractionUniforms,
  ) {
    interactionLighting.bind(layer, key, uniforms) {
      setInteractionLightingUniforms(key, uniforms, mergedFieldScratch)
    }
  }

  private fun updateInteractionOutputEffect(
    layer: GraphicsLayer,
    input: PlatformRenderEffect?,
    patch: GlassInteractionPatch,
    featherWidth: Float,
  ) {
    val outputEffect = interactionOutputEffect ?: traceCreateRenderEffect {
      createMutableRuntimeShaderRenderEffect(
        effect = GLASS_INTERACTION_OUTPUT_EFFECT,
        shaderNames = arrayOf("content"),
        inputs = arrayOf(input),
      )
    }.also {
      interactionOutputEffect = it
      interactionOutputInput = input
      interactionOutputComposeEffect = null
    }
    // The input changes on every frame of a press-drag, so update the existing shader with it.
    val inputChanged = input != interactionOutputInput
    if (
      inputChanged ||
      patch.uniforms != interactionOutputUniforms ||
      featherWidth != interactionOutputFeatherWidth ||
      interactionOutputComposeEffect == null
    ) {
      val updateUniforms: RuntimeShaderUniformProvider.() -> Unit = {
        setInteractionOutputUniforms(patch.uniforms, featherWidth)
      }
      interactionOutputComposeEffect = if (inputChanged) {
        outputEffect.updateInputs(inputs = arrayOf(input), uniforms = updateUniforms)
      } else {
        outputEffect.updateUniforms(updateUniforms)
      }.asComposeRenderEffect()
      interactionOutputInput = input
      interactionOutputUniforms = patch.uniforms
      interactionOutputFeatherWidth = featherWidth
    }
    layer.renderEffect = checkNotNull(interactionOutputComposeEffect)
  }

  private fun DrawScope.drawCompletedLayer(
    layer: GraphicsLayer,
    context: HazeEffectRuntimeDrawScope,
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

  private fun DrawScope.drawCompletedOutput(
    optical: GraphicsLayer,
    interactionOutput: GraphicsLayer?,
    interactionPatch: GlassInteractionPatch?,
    context: HazeEffectRuntimeDrawScope,
    params: GlassRenderParams,
  ) {
    drawCompletedLayer(optical, context, params, alpha = 1f)
    if (interactionOutput != null && interactionPatch != null) {
      drawCompletedPatch(
        layer = interactionOutput,
        patch = interactionPatch,
        context = context,
        params = params,
        blendMode = BlendMode.SrcAtop,
        alpha = 1f,
      )
    }
  }

  private fun DrawScope.drawCompletedPatch(
    layer: GraphicsLayer,
    patch: GlassInteractionPatch,
    context: HazeEffectRuntimeDrawScope,
    params: GlassRenderParams,
    blendMode: BlendMode,
    alpha: Float,
  ) {
    layer.clip = false
    layer.alpha = alpha
    layer.blendMode = blendMode
    drawScaledContent(
      offset = -context.layerOffset,
      scaledSize = params.coordinates.materialSize,
      clip = effect.shouldClipToNodeBounds(),
    ) {
      val compositeBounds = patch.compositeBounds.translate(
        translateX = -patch.bounds.left,
        translateY = -patch.bounds.top,
      )
      translate(Offset(patch.bounds.left.toFloat(), patch.bounds.top.toFloat())) {
        clipRect(
          left = compositeBounds.left.toFloat(),
          top = compositeBounds.top.toFloat(),
          right = compositeBounds.right.toFloat(),
          bottom = compositeBounds.bottom.toFloat(),
        ) {
          drawLayer(layer)
        }
      }
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
    // updateUniforms and updateInputs each return a new effect that snapshots the uniforms and
    // inputs given so far, on Android (copied from the shared compiled shader) as on Skiko (into
    // the ImageFilter). The previous effect is not updated, so every effect mutated here must be
    // reassigned before its retained layer is recorded; never update uniforms on a path that
    // presents retained output as-is.
    if (supportsFusedGlassRenderEffect) {
      return getFusedRenderEffects(render)
    }
    invalidateStageShadersOnMergeChange(render)
    val nextBlurKey = render.blurKey
    if (nextBlurKey != blurKey) {
      blurEffects = nextBlurKey?.let(::updateBlurRenderEffects)
      blurKey = nextBlurKey
    }
    val nextOpticalKey = render.opticalKey
    if (nextOpticalKey != opticalKey || opticalEffect == null) {
      val shader = opticalShader ?: traceCreateRenderEffect {
        createRetainedGlassOpticalRenderEffect(merged = nextOpticalKey.mergedField != null)
      }.also {
        opticalShader = it
      }
      opticalEffect = shader.updateUniforms { setOpticalUniforms(nextOpticalKey, mergedFieldScratch) }
      opticalKey = nextOpticalKey
    }
    val nextRefractionDetailKey = render.refractionDetailKey
    if (
      nextRefractionDetailKey != refractionDetailKey ||
      nextRefractionDetailKey != null &&
      (refractionDetailEffect == null || refractionDetailCoverageEffect == null)
    ) {
      refractionDetailEffect = nextRefractionDetailKey?.let { key ->
        val shader = refractionDetailShader ?: traceCreateRenderEffect {
          createRefractionDetailRenderEffect(
            interactive = false,
            coverageOnly = false,
            merged = key.mergedField != null,
          )
        }.also {
          refractionDetailShader = it
        }
        shader.updateUniforms { setRefractionDetailUniforms(key, mergedFieldScratch) }
      }
      refractionDetailCoverageEffect = nextRefractionDetailKey?.let { key ->
        val shader = refractionDetailCoverageShader
          ?: traceCreateRenderEffect {
            createRefractionDetailRenderEffect(
              interactive = false,
              coverageOnly = true,
              merged = key.mergedField != null,
            )
          }.also {
            refractionDetailCoverageShader = it
          }
        shader.updateUniforms { setRefractionDetailUniforms(key, mergedFieldScratch) }
      }
      refractionDetailKey = nextRefractionDetailKey
    }
    updateRimEffect(render.rimKey)
    val currentOpticalEffect = checkNotNull(opticalEffect)
    val currentRefractionDetail = nextRefractionDetailKey?.let { key ->
      GlassRefractionDetailRenderEffect(
        key = key,
        effect = checkNotNull(refractionDetailEffect),
        coverageEffect = checkNotNull(refractionDetailCoverageEffect),
      )
    }
    return GlassRenderEffects(
      blur = blurEffects,
      optical = currentOpticalEffect,
      refractionDetail = currentRefractionDetail,
      rim = rimEffect,
    )
  }

  /** Merged variants declare extra uniforms, so changing variant recreates the stage shaders. */
  private fun invalidateStageShadersOnMergeChange(render: GlassPreparedRender) {
    val merged = render.params.mergedField != null
    if (merged == stageShadersMerged) return
    opticalShader = null
    refractionDetailShader = null
    refractionDetailCoverageShader = null
    rimShader = null
    interactionLighting.releaseShader()
    clearInteractionLightingEffectCache()
    stageShadersMerged = merged
  }

  private fun getFusedRenderEffects(
    render: GlassPreparedRender,
    backdrop: Boolean = false,
  ): GlassRenderEffects {
    invalidateStageShadersOnMergeChange(render)
    updateRimEffect(render.rimKey)
    val backdropBackground = render.params.sourceBackgroundColor.takeIf {
      backdrop && it.alpha > 0f
    }
    val backdropMergedBackground = render.params.mergedBackground.takeIf { backdrop }
    val interaction = render.interactionUniforms.takeIf { render.interactionTopology.hasOptics }
    val currentFusedKey = fusedEffectKey
    // Compare the components first, so an unchanged frame does not build a key to throw away.
    val fusedKeyChanged = currentFusedKey == null || fusedEffect == null || !currentFusedKey.matches(
      blur = render.blurKey,
      depth = render.params.depth,
      optical = render.opticalKey,
      detail = render.refractionDetailKey,
      interaction = interaction,
      backdropBackground = backdropBackground,
      backdropMergedBackground = backdropMergedBackground,
    )
    if (fusedKeyChanged) {
      val nextFusedEffectKey = GlassFusedEffectKey(
        blur = render.blurKey,
        depth = render.params.depth,
        optical = render.opticalKey,
        detail = render.refractionDetailKey,
        interaction = interaction,
        backdropBackground = backdropBackground,
        backdropMergedBackground = backdropMergedBackground,
      )
      fusedEffect = nextFusedEffectKey.let { key ->
        val nextInputKey = key.depthInputKey(interactionOptics = render.interactionTopology.hasOptics)
        val previousInputKey = fusedInputKey
        val inputChanged = previousInputKey != nextInputKey
        val topologyChanged = previousInputKey == null ||
          previousInputKey.interactionOptics != nextInputKey.interactionOptics ||
          previousInputKey.sharpDetail != nextInputKey.sharpDetail ||
          previousInputKey.merged != nextInputKey.merged
        // The sharp, background-composited window pixels. The optical chain consumes it through the
        // depth input and the detail shader directly, so both see the same pixels.
        val backdropInput = if (inputChanged) {
          val sampleSize = key.optical.coordinates.sampleSize
          val backdropBackgroundShader = key.backdropMergedBackground?.let { field ->
            mergedBackgroundShader(key.optical.coordinates.materialOrigin, field)
          } ?: key.backdropBackground?.let { color ->
            (Brush.linearGradient(listOf(color, color)) as ShaderBrush).createShader(sampleSize)
          }
          backdropBackgroundShader?.let(::createGlassBackdropInputRenderEffect)
        } else {
          null
        }
        val nextInput = if (inputChanged) {
          updateFusedDepthInputRenderEffect(
            blur = key.blur,
            depth = key.depth,
            sampleSize = key.optical.coordinates.sampleSize,
            input = backdropInput,
          )
        } else {
          null
        }
        val recreatedShader = fusedShader == null || topologyChanged
        if (recreatedShader) {
          fusedShader = traceCreateRenderEffect {
            createFusedGlassRenderEffect(
              input = nextInput,
              interactionOptics = nextInputKey.interactionOptics,
              sharpDetail = nextInputKey.sharpDetail,
              merged = nextInputKey.merged,
            )
          }
          refractionDetailShader = null
        }
        val shader = checkNotNull(fusedShader)
        val updateUniforms: RuntimeShaderUniformProvider.() -> Unit = {
          setOpticalUniforms(key.optical, mergedFieldScratch)
          key.detail?.let { setRefractionDetailUniforms(it, mergedFieldScratch) }
          key.interaction?.let { setFusedInteractionOpticalUniforms(it) }
        }
        val optical = if (inputChanged && !recreatedShader) {
          shader.updateInputs(
            inputs = arrayOf(nextInput),
            uniforms = updateUniforms,
          )
        } else {
          shader.updateUniforms(updateUniforms)
        }
        fusedInputKey = nextInputKey
        val detailKey = key.detail ?: return@let optical
        // A new detail shader needs `backdropInput` here: it is only created when the fused shader
        // is recreated, the merge or sharp-detail topology changes, or retained shaders were
        // released, and each of those either clears `fusedInputKey` or changes one of its topology
        // fields, so `inputChanged` is true.
        val existingDetailShader = refractionDetailShader
        val detailShader = existingDetailShader ?: traceCreateRenderEffect {
          createRefractionDetailRenderEffect(
            interactive = nextInputKey.interactionOptics,
            coverageOnly = false,
            merged = nextInputKey.merged,
            input = backdropInput,
          )
        }.also {
          refractionDetailShader = it
        }
        val updateDetailUniforms: RuntimeShaderUniformProvider.() -> Unit = {
          setRefractionDetailUniforms(detailKey, mergedFieldScratch)
          key.interaction?.let(::setInteractionDetailUniforms)
        }
        val detail = if (existingDetailShader != null && inputChanged) {
          detailShader.updateInputs(inputs = arrayOf(backdropInput), uniforms = updateDetailUniforms)
        } else {
          detailShader.updateUniforms(updateDetailUniforms)
        }
        wrapRuntimeShaderConstruction {
          createBlendRenderEffect(
            blendMode = BlendMode.Plus,
            background = optical,
            foreground = detail,
          )
        }
      }
      fusedEffectKey = nextFusedEffectKey
    }
    val currentFusedEffect = checkNotNull(fusedEffect)
    return GlassRenderEffects(
      blur = null,
      optical = currentFusedEffect,
      refractionDetail = null,
      rim = rimEffect,
    )
  }

  private fun updateRimEffect(nextRimKey: GlassRimEffectKey?) {
    if (nextRimKey != rimKey) {
      // buildGlassPreparedRender only creates a rim key while the rim is visible.
      rimEffect = nextRimKey?.let { key ->
        val shader = rimShader ?: traceCreateRenderEffect {
          createRetainedGlassRimRenderEffect(merged = key.mergedField != null)
        }.also { rimShader = it }
        shader.updateUniforms { setRimUniforms(key, mergedFieldScratch) }
      }
      rimKey = nextRimKey
    }
  }

  private fun updateFusedDepthInputRenderEffect(
    blur: GlassBlurEffectKey?,
    depth: Float,
    sampleSize: Size,
    input: PlatformRenderEffect?,
  ): PlatformRenderEffect? {
    if (depth <= 0.0001f) return input
    val plan = blur?.plan?.takeUnless { it.isIdentity } ?: return input
    if (plan.usesNativeWideBlur) {
      return createGlassDepthInputRenderEffect(
        sharp = input,
        blur = wrapRuntimeShaderConstruction {
          createBlurRenderEffect(
            radiusX = plan.effectiveRadiusPx,
            radiusY = plan.effectiveRadiusPx,
            tileMode = androidx.compose.ui.graphics.TileMode.Clamp,
            input = input,
          )
        },
        depth = depth,
      )
    }
    val progressive = blur.progressive != null
    val shaders = fusedDepthInputShaders
      ?.takeIf { it.progressive == progressive }
      ?: FusedDepthInputShaders(
        progressive = progressive,
        horizontal = traceCreateRenderEffect {
          createGlassBlurRenderEffectWithInput(
            horizontal = true,
            progressive = progressive,
            input = null,
          )
        },
        vertical = traceCreateRenderEffect {
          createGlassBlurRenderEffectWithInput(
            horizontal = false,
            progressive = progressive,
            input = null,
          )
        },
      ).also { fusedDepthInputShaders = it }
    val progressiveMask = blur.progressive?.toShader(blur.maskSize)
    val horizontal = shaders.horizontal.updateInputs(inputs = arrayOf(input)) {
      setGlassBlurUniforms(blur, plan.horizontalKernel, sampleSize.width, sampleSize.height)
      progressiveMask?.let { setChildShader("mask", it) }
    }
    val vertical = shaders.vertical.updateInputs(inputs = arrayOf(horizontal)) {
      setGlassBlurUniforms(blur, plan.verticalKernel, sampleSize.width, sampleSize.height)
      progressiveMask?.let { setChildShader("mask", it) }
    }
    return createGlassDepthInputRenderEffect(
      sharp = input,
      blur = vertical,
      depth = depth,
    )
  }

  private fun createGlassBackdropInputRenderEffect(backgroundShader: Shader): PlatformRenderEffect {
    val background = createShaderRenderEffect(
      backgroundShader,
    )
    return createBlendRenderEffect(
      blendMode = BlendMode.SrcOver,
      background = background,
      foreground = createOffsetRenderEffect(0f, 0f),
    )
  }

  private fun updateBlurRenderEffects(key: GlassBlurEffectKey): GlassBlurRenderEffects? {
    if (key.plan.isIdentity) return null
    if (key.plan.usesNativeWideBlur) {
      return GlassBlurRenderEffects(
        key = key,
        native = wrapRuntimeShaderConstruction {
          checkNotNull(
            createBlurRenderEffect(
              radiusX = key.plan.effectiveRadiusPx,
              radiusY = key.plan.effectiveRadiusPx,
              tileMode = androidx.compose.ui.graphics.TileMode.Clamp,
            ),
          )
        },
        horizontal = null,
        vertical = null,
      )
    }
    val progressive = key.progressive != null
    val horizontalShader = if (progressive) {
      progressiveBlurHorizontalShader ?: traceCreateRenderEffect {
        createGlassBlurRenderEffectWithInput(
          horizontal = true,
          progressive = true,
          input = null,
        )
      }.also { progressiveBlurHorizontalShader = it }
    } else {
      blurHorizontalShader ?: traceCreateRenderEffect {
        createGlassBlurRenderEffectWithInput(
          horizontal = true,
          progressive = false,
          input = null,
        )
      }.also { blurHorizontalShader = it }
    }
    val verticalShader = if (progressive) {
      progressiveBlurVerticalShader ?: traceCreateRenderEffect {
        createGlassBlurRenderEffectWithInput(
          horizontal = false,
          progressive = true,
          input = null,
        )
      }.also { progressiveBlurVerticalShader = it }
    } else {
      blurVerticalShader ?: traceCreateRenderEffect {
        createGlassBlurRenderEffectWithInput(
          horizontal = false,
          progressive = false,
          input = null,
        )
      }.also { blurVerticalShader = it }
    }
    val progressiveMask = key.progressive?.toShader(key.maskSize)
    val horizontal = horizontalShader.updateUniforms {
      setGlassBlurUniforms(
        key = key,
        kernel = key.plan.horizontalKernel,
        sampleWidth = key.plan.sampleSize.width.toFloat(),
        sampleHeight = key.plan.sampleSize.height.toFloat(),
      )
      progressiveMask?.let { setChildShader("mask", it) }
    }
    val vertical = verticalShader.updateUniforms {
      setGlassBlurUniforms(
        key = key,
        kernel = key.plan.verticalKernel,
        sampleWidth = key.plan.sampleSize.width.toFloat(),
        sampleHeight = key.plan.sampleSize.height.toFloat(),
      )
      progressiveMask?.let { setChildShader("mask", it) }
    }
    return GlassBlurRenderEffects(
      key = key,
      native = null,
      horizontal = horizontal,
      vertical = vertical,
    )
  }

  private fun <K : Any> interactionSlot(effect: () -> PlatformRuntimeEffect) =
    InteractionEffectSlot<K> {
      traceCreateRenderEffect {
        createMutableRuntimeShaderRenderEffect(
          effect = effect(),
          shaderNames = arrayOf("content"),
          inputs = arrayOf(null),
        )
      }
    }

  private fun traceCreateRenderEffect(
    block: () -> MutableRuntimeShaderRenderEffect,
  ): MutableRuntimeShaderRenderEffect =
    trace(GlassTraceSection.CreateRenderEffect) {
      runtimeEffectFactory.create(block)
    }
}

internal fun interface GlassRuntimeEffectFactory {
  fun create(
    block: () -> MutableRuntimeShaderRenderEffect,
  ): MutableRuntimeShaderRenderEffect
}

internal object PlatformGlassRuntimeEffectFactory : GlassRuntimeEffectFactory {
  override fun create(
    block: () -> MutableRuntimeShaderRenderEffect,
  ): MutableRuntimeShaderRenderEffect = block()
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

@Poko
internal class GlassFusedEffectKey(
  val blur: GlassBlurEffectKey?,
  val depth: Float,
  val optical: GlassOpticalEffectKey,
  val detail: GlassRefractionDetailEffectKey?,
  val interaction: GlassInteractionUniforms?,
  val backdropBackground: Color?,
  val backdropMergedBackground: GlassMergedField?,
)

/**
 * Whether a [GlassFusedEffectKey] built from these components would equal this one, without
 * building it. Every component of the key is compared, and floats as the generated `equals` does,
 * so `NaN` equals itself and `-0f` differs from `0f`.
 */
internal fun GlassFusedEffectKey.matches(
  blur: GlassBlurEffectKey?,
  depth: Float,
  optical: GlassOpticalEffectKey,
  detail: GlassRefractionDetailEffectKey?,
  interaction: GlassInteractionUniforms?,
  backdropBackground: Color?,
  backdropMergedBackground: GlassMergedField?,
): Boolean = this.blur == blur &&
  this.depth.compareTo(depth) == 0 &&
  this.optical == optical &&
  this.detail == detail &&
  this.interaction == interaction &&
  this.backdropBackground == backdropBackground &&
  this.backdropMergedBackground == backdropMergedBackground

@Poko
internal class GlassDepthInputKey(
  val blur: GlassBlurEffectKey?,
  val depth: Float,
  val interactionOptics: Boolean = false,
  val sharpDetail: Boolean = false,
  val backdropBackground: Color? = null,
  val backdropMergedBackground: GlassMergedField? = null,
  /** The merged backdrop background samples the field relative to this origin. */
  val backdropMergedOrigin: Offset? = null,
  val merged: Boolean = false,
)

internal fun GlassFusedEffectKey.depthInputKey(interactionOptics: Boolean) = GlassDepthInputKey(
  blur = blur,
  depth = depth,
  interactionOptics = interactionOptics,
  sharpDetail = detail != null,
  backdropBackground = backdropBackground,
  backdropMergedBackground = backdropMergedBackground,
  backdropMergedOrigin = backdropMergedBackground?.let { optical.coordinates.materialOrigin },
  merged = optical.mergedField != null,
)

private class FusedDepthInputShaders(
  val progressive: Boolean,
  val horizontal: MutableRuntimeShaderRenderEffect,
  val vertical: MutableRuntimeShaderRenderEffect,
)

@OptIn(InternalHazeApi::class)
internal data class GlassRefractionDetailRenderEffect(
  val key: GlassRefractionDetailEffectKey,
  val effect: PlatformRenderEffect,
  val coverageEffect: PlatformRenderEffect,
)

@OptIn(InternalHazeApi::class)
internal data class GlassBlurRenderEffects(
  val key: GlassBlurEffectKey,
  val native: PlatformRenderEffect?,
  val horizontal: PlatformRenderEffect?,
  val vertical: PlatformRenderEffect?,
)

internal expect fun createGlassDepthInputRenderEffect(
  sharp: PlatformRenderEffect?,
  blur: PlatformRenderEffect?,
  depth: Float,
): PlatformRenderEffect?

internal fun createFusedGlassRenderEffect(
  input: PlatformRenderEffect? = null,
  interactionOptics: Boolean = false,
  sharpDetail: Boolean = true,
  merged: Boolean = false,
): MutableRuntimeShaderRenderEffect = createMutableRuntimeShaderRenderEffect(
  effect = fusedGlassRuntimeEffect(interactionOptics, sharpDetail, merged),
  shaderNames = arrayOf("content"),
  inputs = arrayOf(input),
)

private fun fusedGlassRuntimeEffect(
  interactionOptics: Boolean,
  sharpDetail: Boolean,
  merged: Boolean,
) = when {
  // Interaction optics are not merged, so a merged field always uses the non-interactive variants.
  merged && sharpDetail -> GLASS_MERGED_FUSED_EFFECT
  merged -> GLASS_MERGED_NO_DETAIL_FUSED_EFFECT
  interactionOptics && sharpDetail -> GLASS_INTERACTION_OPTICS_FUSED_EFFECT
  interactionOptics -> GLASS_INTERACTION_OPTICS_NO_DETAIL_FUSED_EFFECT
  sharpDetail -> GLASS_FUSED_EFFECT
  else -> GLASS_NO_DETAIL_FUSED_EFFECT
}

internal fun createGlassBlurRenderEffectWithInput(
  horizontal: Boolean,
  progressive: Boolean,
  input: PlatformRenderEffect?,
): MutableRuntimeShaderRenderEffect = createMutableRuntimeShaderRenderEffect(
  effect = if (progressive) {
    if (horizontal) {
      GLASS_PROGRESSIVE_HORIZONTAL_BLUR_EFFECT
    } else {
      GLASS_PROGRESSIVE_VERTICAL_BLUR_EFFECT
    }
  } else {
    if (horizontal) GLASS_HORIZONTAL_BLUR_EFFECT else GLASS_VERTICAL_BLUR_EFFECT
  },
  shaderNames = arrayOf("content"),
  inputs = arrayOf(input),
)

internal expect val supportsFusedGlassRenderEffect: Boolean

internal fun createRetainedGlassOpticalRenderEffect(
  merged: Boolean = false,
): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = if (merged) GLASS_MERGED_OPTICAL_EFFECT else GLASS_OPTICAL_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

private fun createRefractionDetailRenderEffect(
  interactive: Boolean,
  coverageOnly: Boolean,
  merged: Boolean = false,
  input: PlatformRenderEffect? = null,
): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = when {
      // Interaction stages are not merged, so a merged field uses the non-interactive variants.
      merged && coverageOnly -> GLASS_MERGED_REFRACTION_DETAIL_COVERAGE_EFFECT
      merged -> GLASS_MERGED_REFRACTION_DETAIL_EFFECT
      interactive && coverageOnly -> GLASS_INTERACTION_REFRACTION_DETAIL_COVERAGE_EFFECT
      interactive -> GLASS_INTERACTION_REFRACTION_DETAIL_EFFECT
      coverageOnly -> GLASS_REFRACTION_DETAIL_COVERAGE_EFFECT
      else -> GLASS_REFRACTION_DETAIL_EFFECT
    },
    shaderNames = arrayOf("content"),
    inputs = arrayOf(input),
  )

internal fun createRetainedGlassRimRenderEffect(
  merged: Boolean = false,
): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = if (merged) GLASS_MERGED_RIM_EFFECT else GLASS_RIM_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

internal fun RuntimeShaderUniformProvider.setGlassBlurUniforms(
  key: GlassBlurEffectKey,
  kernel: SemanticBlurKernel,
  sampleWidth: Float,
  sampleHeight: Float,
) {
  setFloatUniform("sampleSize", sampleWidth, sampleHeight)
  setFloatUniform("sourceIsOpaque", if (key.sourceIsOpaque) 1f else 0f)
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
private val GLASS_FUSED_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildFused())
}
private val GLASS_INTERACTION_OPTICS_FUSED_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildFused(interactionOptics = true))
}
private val GLASS_NO_DETAIL_FUSED_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildFused(sharpDetail = false))
}
private val GLASS_INTERACTION_OPTICS_NO_DETAIL_FUSED_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildFused(interactionOptics = true, sharpDetail = false))
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
private val GLASS_REFRACTION_DETAIL_COVERAGE_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildRefractionDetail(coverageOnly = true))
}
private val GLASS_INTERACTION_REFRACTION_DETAIL_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildRefractionDetail(interactive = true))
}
private val GLASS_INTERACTION_REFRACTION_DETAIL_COVERAGE_EFFECT by lazy(
  LazyThreadSafetyMode.NONE,
) {
  createRuntimeEffect(GlassShaders.buildRefractionDetail(interactive = true, coverageOnly = true))
}
private val GLASS_INTERACTION_LIGHTING_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildInteractionLighting())
}
private val GLASS_INTERACTION_OUTPUT_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildInteractionOutputComposite())
}
private val GLASS_RIM_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildRim())
}
private val GLASS_MERGED_FUSED_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildFused(merged = true))
}
private val GLASS_MERGED_NO_DETAIL_FUSED_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildFused(sharpDetail = false, merged = true))
}
private val GLASS_MERGED_OPTICAL_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildOptical(merged = true))
}
private val GLASS_MERGED_REFRACTION_DETAIL_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildRefractionDetail(merged = true))
}
private val GLASS_MERGED_REFRACTION_DETAIL_COVERAGE_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildRefractionDetail(coverageOnly = true, merged = true))
}
private val GLASS_MERGED_RIM_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildRim(merged = true))
}
private val GLASS_MERGED_INTERACTION_LIGHTING_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildInteractionLighting(merged = true))
}

internal fun RuntimeShaderUniformProvider.setOpticalUniforms(
  key: GlassOpticalEffectKey,
  scratch: MergedFieldUniformScratch = MergedFieldUniformScratch(),
) {
  setCommonUniforms(key.coordinates, key.sampleStepPx, key.edgeSoftnessPx, key.cornerRadii)
  setFloatUniform("refractionStrength", key.refractionStrength)
  setFloatUniform("refractionFoldStrength", key.refractionFoldStrength)
  setFloatUniform("ambientResponse", key.ambientResponse)
  setFloatUniform("refractionHeight", key.refractionHeightPx)
  setFloatUniform("edgeRefractionWidth", key.edgeRefractionWidthPx)
  setFloatUniform("chromaticAberrationStrength", key.chromaticAberrationStrength)
  setFloatUniform("surfaceProfile", key.surfaceProfile)
  setFloatUniform("chromaticAberrationMode", key.chromaticAberrationMode)
  setFloatUniform("contrast", key.contrast)
  setFloatUniform("whitePoint", key.whitePoint)
  setFloatUniform("chromaMultiplier", key.chromaMultiplier)
  setFloatUniform("refractionScale", key.refractionScalePx)
  setFloatUniform("contentNormalBlend", key.contentNormalBlend)
  setFloatUniform("fresnelExponent", key.fresnelExponent)
  setColorUniform("tintColor", key.tint)
  key.mergedField?.let { setMergedFieldUniforms(it, includeTint = true, includeOptics = true, scratch = scratch) }
}

internal fun RuntimeShaderUniformProvider.setRefractionDetailUniforms(
  key: GlassRefractionDetailEffectKey,
  scratch: MergedFieldUniformScratch = MergedFieldUniformScratch(),
) {
  setCommonUniforms(
    key.sampleSize,
    key.materialOrigin,
    key.materialSize,
    key.sampleStepPx,
    key.edgeSoftnessPx,
    key.cornerRadii,
  )
  setFloatUniform("refractionStrength", key.refractionStrength)
  setFloatUniform("refractionFoldStrength", key.refractionFoldStrength)
  setFloatUniform("refractionHeight", key.refractionHeightPx)
  setFloatUniform("edgeRefractionWidth", key.edgeRefractionWidthPx)
  setFloatUniform("refractionScale", key.refractionScalePx)
  setFloatUniform("surfaceProfile", key.surfaceProfile)
  setFloatUniform("detailWidth", key.detailWidthPx)
  setFloatUniform("detailIntensity", key.detailIntensity)
  setFloatUniform("detailVisibility", key.detailVisibility)
  key.mergedField?.let { setMergedFieldUniforms(it, includeOptics = true, scratch = scratch) }
}

internal fun RuntimeShaderUniformProvider.setRimUniforms(
  key: GlassRimEffectKey,
  scratch: MergedFieldUniformScratch = MergedFieldUniformScratch(),
) {
  setCommonUniforms(key.coordinates, key.sampleStepPx, key.edgeSoftnessPx, key.cornerRadii)
  setFloatUniform("specularIntensity", key.specularIntensity)
  setColorUniform("edgeShadow", key.edgeShadow)
  setFloatUniform("specularExponent", key.specularExponent)
  setFloatUniform(
    "lightPosition",
    key.lightPosition.x,
    key.lightPosition.y,
  )
  key.mergedField?.let { setMergedFieldUniforms(it, scratch = scratch) }
}

internal fun RuntimeShaderUniformProvider.setInteractionOpticalUniforms(
  uniforms: GlassInteractionUniforms,
) {
  setInteractionPositionUniforms(uniforms)
  setFloatUniform("interactionRefractionMultiplier", uniforms.refractionMultiplier)
  setFloatUniform("interactionWhitePointDelta", uniforms.whitePointDelta)
}

private fun RuntimeShaderUniformProvider.setFusedInteractionOpticalUniforms(
  uniforms: GlassInteractionUniforms,
) {
  setInteractionOpticalUniforms(uniforms)
  setFloatUniform("interactionOpticalActive", if (uniforms.hasOptics) 1f else 0f)
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
  scratch: MergedFieldUniformScratch = MergedFieldUniformScratch(),
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
  val field = key.mergedField
  if (field != null) {
    setMergedFieldUniforms(field, includeLighting = true, scratch = scratch)
  } else {
    setInteractionPositionUniforms(uniforms)
    setFloatUniform("interactionLightingIntensity", uniforms.lightingIntensity)
  }
}

private fun RuntimeShaderUniformProvider.setInteractionOutputUniforms(
  uniforms: GlassInteractionUniforms,
  featherWidth: Float,
) {
  setInteractionPositionUniforms(uniforms)
  setFloatUniform("featherWidth", featherWidth)
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
  setCommonUniforms(
    coordinates.sampleSize,
    coordinates.materialOrigin,
    coordinates.materialSize,
    sampleStepPx,
    edgeSoftnessPx,
    cornerRadii,
  )
}

private fun RuntimeShaderUniformProvider.setCommonUniforms(
  sampleSize: Size,
  materialOrigin: Offset,
  materialSize: Size,
  sampleStepPx: Float,
  edgeSoftnessPx: Float,
  cornerRadii: CornerRadii,
) {
  setFloatUniform("sampleSize", sampleSize.width, sampleSize.height)
  setFloatUniform("materialOrigin", materialOrigin.x, materialOrigin.y)
  setFloatUniform("materialSize", materialSize.width, materialSize.height)
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

private fun GlassRefractionDetailEffectKey.localTo(
  coordinates: GlassCoordinates,
): GlassRefractionDetailEffectKey = copy(
  sampleSize = coordinates.sampleSize,
  materialOrigin = coordinates.materialOrigin,
  materialSize = coordinates.materialSize,
)

private fun depthPassThrough(
  source: GraphicsLayer,
  blurred: GraphicsLayer,
  depth: Float,
): GraphicsLayer? = when {
  depth <= 0f || blurred === source -> source
  depth >= 1f -> blurred
  else -> null
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
