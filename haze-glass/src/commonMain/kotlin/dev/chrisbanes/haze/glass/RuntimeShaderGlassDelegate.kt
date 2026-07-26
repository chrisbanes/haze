// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize
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
import dev.chrisbanes.haze.trace

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
  private var refractionDetailCoverageShader: MutableRuntimeShaderRenderEffect? = null
  private var refractionDetailCoverageEffect: PlatformRenderEffect? = null
  private var rimKey: GlassRimEffectKey? = null
  internal var rimShader: MutableRuntimeShaderRenderEffect? = null
    private set
  internal var rimEffect: PlatformRenderEffect? = null
    private set
  private var fusedEffectKey: GlassFusedEffectKey? = null
  internal var fusedShader: MutableRuntimeShaderRenderEffect? = null
    private set
  private var fusedInputKey: GlassDepthInputKey? = null
  private var fusedEffect: PlatformRenderEffect? = null
  private var interactionOpticalEffect: MutableRuntimeShaderRenderEffect? = null
  private var interactionDetailEffect: MutableRuntimeShaderRenderEffect? = null
  private var interactionDetailCoverageEffect: MutableRuntimeShaderRenderEffect? = null
  private var interactionLightingEffect: MutableRuntimeShaderRenderEffect? = null
  private var interactionOpticalEffectKey: GlassOpticalEffectKey? = null
  private var interactionOpticalEffectUniforms: GlassInteractionUniforms? = null
  private var interactionOpticalPlatformEffect: PlatformRenderEffect? = null
  private var interactionOpticalComposeEffect: RenderEffect? = null
  private var interactionOutputEffect: MutableRuntimeShaderRenderEffect? = null
  private var interactionOutputInput: PlatformRenderEffect? = null
  private var interactionOutputUniforms: GlassInteractionUniforms? = null
  private var interactionOutputFeatherWidth: Float = Float.NaN
  private var interactionOutputComposeEffect: RenderEffect? = null
  private var interactionDetailEffectKey: GlassRefractionDetailEffectKey? = null
  private var interactionDetailEffectUniforms: GlassInteractionUniforms? = null
  private var interactionDetailComposeEffect: RenderEffect? = null
  private var interactionDetailEffectLayer: GraphicsLayer? = null
  private var interactionDetailCoverageEffectKey: GlassRefractionDetailEffectKey? = null
  private var interactionDetailCoverageEffectUniforms: GlassInteractionUniforms? = null
  private var interactionDetailCoverageComposeEffect: RenderEffect? = null
  private var interactionDetailCoverageEffectLayer: GraphicsLayer? = null
  private var interactionLightingEffectKey: GlassInteractionLightingKey? = null
  private var interactionLightingEffectUniforms: GlassInteractionUniforms? = null
  private var interactionLightingComposeEffect: RenderEffect? = null
  private var interactionLightingEffectLayer: GraphicsLayer? = null
  private var recordedInteractionOpticalLayer: GraphicsLayer? = null
  private var recordedInteractionOpticalInput: GraphicsLayer? = null
  private var recordedInteractionOpticalKey: GlassOpticalEffectKey? = null
  private var recordedInteractionDetailLayer: GraphicsLayer? = null
  private var recordedInteractionDetailInput: GraphicsLayer? = null
  private var recordedInteractionDetailKey: GlassRefractionDetailEffectKey? = null
  private var recordedInteractionDetailCoverageLayer: GraphicsLayer? = null
  private var recordedInteractionDetailCoverageInput: GraphicsLayer? = null
  private var recordedInteractionDetailCoverageKey: GlassRefractionDetailEffectKey? = null
  private var recordedInteractionCompositeLayer: GraphicsLayer? = null
  private var recordedInteractionCompositeOptical: GraphicsLayer? = null
  private var recordedInteractionCompositeDetail: GraphicsLayer? = null
  private var recordedInteractionCompositeCoverage: GraphicsLayer? = null
  private var recordedInteractionCompositeSize: IntSize? = null
  private var recordedInteractionLightingLayer: GraphicsLayer? = null
  private var recordedInteractionLightingSize: IntSize? = null
  internal val layers = GlassLayers()
  private var graphicsContext: GraphicsContext? = null
  private var preparedRender: GlassPreparedRender? = null
  private var preparedParams: GlassRenderParams? = null
  private var preparedRenderEffects: GlassRenderEffects? = null
  private var preparedInteractionUniforms: GlassInteractionUniforms? = null
  private var preparedInteractionPatch: GlassInteractionPatch? = null
  private var preparedSourceAvailable: Boolean = false
  private var preparedStageAvailability: GlassStageAvailability? = null
  private var preparedSharedBlurGroup: SharedGlassBlurGroup? = null
  private var preparedFusedEffect: PlatformRenderEffect? = null
  internal val usesSharedBlurForTest: Boolean get() = preparedSharedBlurGroup != null
  internal val usesFusedEffectForTest: Boolean get() = preparedFusedEffect != null
  internal val sharedBlurredLayerForTest: GraphicsLayer?
    get() = preparedSharedBlurGroup?.blurred
  internal val sharedOpticalLayerForTest: GraphicsLayer?
    get() = preparedSharedBlurGroup?.opticalAtlas
  internal val sharedRefractionDetailLayerForTest: GraphicsLayer?
    get() = preparedSharedBlurGroup?.refractionDetailAtlas
  private var retainedOutputAvailable: Boolean = false
  private var recordedSharedBlurLayer: GraphicsLayer? = null
  private var recordedSharedBlurTargetLayer: GraphicsLayer? = null
  private var recordedSharedBlurBounds: androidx.compose.ui.geometry.Rect? = null
  private var recordedSharedBlurCaptureScale: Float = Float.NaN
  private var recordedSharedBlurLocalTopLeft: Offset? = null
  private var recordedSharedBlurSampleSize: androidx.compose.ui.unit.IntSize? = null
  private var recordedSharedOpticalSlice: SharedGlassAtlasSlice? = null
  private var recordedSharedRefractionDetailSlice: SharedGlassAtlasSlice? = null
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
  internal var interactionLightingRecordCount: Int = 0
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
      SharedGlassBlurRegistry.remove(this@RuntimeShaderGlassDelegate)
      releaseRetainedResources()
      return
    }
    val params = currentPreparedRender.params
    val interactionUniforms = currentPreparedRender.interactionUniforms
    val currentRenderEffects = trace(GlassTraceSection.PrepareEffects) {
      getRenderEffects(currentPreparedRender)
    }
    val interactionPatch = if (currentRenderEffects.fused == null) {
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
      val currentFusedEffect = currentRenderEffects.fused
      val sharedBlurGroup = if (currentFusedEffect != null) {
        SharedGlassBlurRegistry.remove(this@RuntimeShaderGlassDelegate)
        null
      } else {
        SharedGlassBlurRegistry.update(
          owner = this@RuntimeShaderGlassDelegate,
          context = context,
          params = params,
          opticalKey = currentPreparedRender.opticalKey
            .takeIf { currentRenderEffects.mergeDepthIntoOptical },
          detailKey = currentPreparedRender.refractionDetailKey,
          graphicsContext = currentGraphicsContext,
        )
      }
      preparedSharedBlurGroup = sharedBlurGroup
      preparedFusedEffect = currentFusedEffect
      if (layers.scaledSize != scaledSize) {
        layers.release(currentGraphicsContext)
        layers.scaledSize = scaledSize
        clearInteractionLayerMetadata()
        clearRetainedMetadata()
      }

      preparedSourceAvailable = layers.hasSource
      preparedStageAvailability = if (currentFusedEffect != null) {
        GlassStageAvailability(
          blur = true,
          depth = true,
          optical = layers.hasOptical,
          detail = true,
          rim = currentRenderEffects.rim == null || layers.hasRim,
        )
      } else {
        stageAvailability(
          params = params,
          effects = currentRenderEffects,
          sharedBlur = sharedBlurGroup != null,
        )
      }

      val blurRequired = currentFusedEffect == null && shouldBlur(params, currentRenderEffects)
      val depthMixRequired = currentFusedEffect == null &&
        shouldDepthMix(params, currentRenderEffects) &&
        !currentRenderEffects.mergeDepthIntoOptical
      val refractionDetailRequired = currentFusedEffect == null &&
        currentRenderEffects.refractionDetail != null
      val rimRequired = currentRenderEffects.rim != null
      val interactionOpticsRequired =
        currentFusedEffect == null && currentPreparedRender.interactionTopology.hasOptics
      val interactionDetailRequired = interactionOpticsRequired && refractionDetailRequired
      val interactionLightingRequired = currentPreparedRender.interactionTopology.hasLighting &&
        currentFusedEffect == null

      // Drop stages which are no longer part of the graph before allocating their replacements.
      // This keeps topology transitions from temporarily retaining both complete graphs.
      if (blurRequired) {
        val blurEffects = checkNotNull(currentRenderEffects.blur)
        val blurWorkingSize = if (sharedBlurGroup != null) {
          blurEffects.key.plan.sampleSize
        } else {
          blurEffects.key.plan.workingSize
        }
        layers.updateBlurWorkingSize(blurWorkingSize, currentGraphicsContext)
        if (sharedBlurGroup != null) {
          layers.releaseBlurIntermediates(currentGraphicsContext)
        } else if (!blurEffects.key.plan.requiresPrefilter) {
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
      layers.groupAlpha.prepare(
        required = currentPreparedRender.groupCompositeSize != null,
        graphicsContext = currentGraphicsContext,
      )

      layers.ensureSource(currentGraphicsContext)
      if (blurRequired) {
        val blurPlan = checkNotNull(currentRenderEffects.blur).key.plan
        if (sharedBlurGroup == null) {
          if (blurPlan.requiresPrefilter) {
            layers.ensureBlurPrefiltered(currentGraphicsContext)
          }
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
    preparedRender = currentPreparedRender
    preparedParams = params
    preparedRenderEffects = currentRenderEffects
    preparedInteractionUniforms = interactionUniforms
    preparedInteractionPatch = interactionPatch
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
      clearInteractionOpticalLayerMetadata()
    }
    if (!detailRequired) {
      layers.releaseInteractionRefractionDetail(graphicsContext)
      clearInteractionRefractionLayerMetadata()
    }
    if (!lightingRequired) {
      releaseLayer(layers.interactionLighting, graphicsContext)
      layers.interactionLighting = null
      clearInteractionLightingLayerMetadata()
    }
  }

  override fun DrawScope.draw(context: VisualEffectContext): Unit =
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
        val currentFusedEffect = preparedFusedEffect
        val currentInputs = GlassStageInputs(
          blur = if (currentFusedEffect != null) {
            render.blurKey
          } else {
            render.blurKey?.takeIf { shouldBlur(params, effects) }
          },
          depth = params.depth,
          optical = if (currentFusedEffect != null && render.interactionTopology.hasAnyLayer) {
            render.opticalKey to interactionUniforms
          } else {
            render.opticalKey
          },
          detail = render.refractionDetailKey,
          rim = render.rimKey,
          mergeDepthIntoOptical = effects.mergeDepthIntoOptical,
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
          if (shouldRecordSource) {
            trace(GlassTraceSection.Source) { recordSource(context, params) }
          } else {
            retainedSource()
          },
          ::clearRetainedOutput,
        ) ?: return
        val invalidation = calculateRequiredStageInvalidation(
          previous = lastSuccessfulStageInputs,
          current = currentInputs,
          sourceChanged = shouldRecordSource,
          availability = preparedStageAvailability ?: stageAvailability(params, effects),
        )
        if (currentFusedEffect != null) {
          val previousBaseInputs = lastSuccessfulStageInputs?.copy(rim = currentInputs.rim)
          val baseInputsChanged = previousBaseInputs != currentInputs
          val completedOptical = requireRetainedStage(
            if (
              shouldRecordSource ||
              baseInputsChanged ||
              !layers.hasOptical
            ) {
              trace(GlassTraceSection.Optical) {
                recordFusedOutput(
                  source = source,
                  params = params,
                  renderEffect = currentFusedEffect,
                )
              }
            } else {
              layers.optical?.takeUnless { it.isReleased }
            },
            ::clearRetainedOutput,
          ) ?: return
          requireRetainedStage(
            if (invalidation.rim) {
              trace(GlassTraceSection.Rim) { recordRimIfNeeded(params, effects) }
            } else {
              retainedRim(effects)
            },
            ::clearRetainedOutput,
          ) ?: return
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
          completed = true
          return
        }
        val blurred = requireRetainedStage(
          if (preparedSharedBlurGroup != null) {
            trace(GlassTraceSection.Blur) {
              recordSharedBlurred(
                group = checkNotNull(preparedSharedBlurGroup),
                context = context,
                params = params,
                source = source,
                effects = effects,
              )
            }
          } else if (invalidation.blur) {
            trace(GlassTraceSection.Blur) {
              recordBlurredIfNeeded(source, params, effects)
            }
          } else {
            retainedBlurInput(source, params, effects)
          },
          ::clearRetainedOutput,
        ) ?: return
        val depthInput = if (effects.mergeDepthIntoOptical) {
          source
        } else {
          requireRetainedStage(
            if (invalidation.depth) {
              trace(GlassTraceSection.Depth) {
                recordDepthInput(source, blurred, params.depth)
              }
            } else {
              retainedDepthInput(source, blurred, params.depth)
            },
            ::clearRetainedOutput,
          ) ?: return
        }
        val optical = requireRetainedStage(
          if (preparedSharedBlurGroup != null && effects.mergeDepthIntoOptical) {
            trace(GlassTraceSection.Optical) {
              recordSharedOptical(
                group = checkNotNull(preparedSharedBlurGroup),
                source = source,
                blurred = blurred,
                params = params,
                opticalKey = render.opticalKey,
                effects = effects,
                invalidated = invalidation.optical,
              )
            }
          } else if (invalidation.optical) {
            trace(GlassTraceSection.Optical) {
              clearSharedOpticalSliceMetadata()
              recordOptical(
                input = depthInput,
                source = source,
                blurred = blurred,
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
            if (preparedSharedBlurGroup != null) {
              trace(GlassTraceSection.Detail) {
                recordSharedRefractionDetail(
                  group = checkNotNull(preparedSharedBlurGroup),
                  source = source,
                  params = params,
                  detail = detail,
                  invalidated = invalidation.detail,
                )
              }
            } else if (invalidation.detail) {
              trace(GlassTraceSection.Detail) {
                clearSharedRefractionDetailSliceMetadata()
                recordRefractionDetail(source, params, detail)
              }
            } else {
              layers.refractionDetail?.takeUnless { layer -> layer.isReleased }
            },
            ::clearRetainedOutput,
          ) ?: return
        }
        val sharedDetailCompositionInvalidated = preparedSharedBlurGroup != null &&
          lastSuccessfulStageInputs?.detail != currentInputs.detail
        val detailCoverageInvalidated = if (preparedSharedBlurGroup != null) {
          sharedDetailCompositionInvalidated || !layers.hasRefractionDetailCoverage
        } else {
          invalidation.detail
        }
        val refractionDetailCoverage = effects.refractionDetail?.let {
          requireRetainedStage(
            if (detailCoverageInvalidated) {
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
          val detailCompositeInvalidated = if (preparedSharedBlurGroup != null) {
            sharedDetailCompositionInvalidated || !layers.hasRefractionComposite
          } else {
            invalidation.optical || invalidation.detail
          }
          requireRetainedStage(
            if (detailCompositeInvalidated) {
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
            trace(GlassTraceSection.Rim) { recordRimIfNeeded(params, effects) }
          } else {
            retainedRim(effects)
          },
          ::clearRetainedOutput,
        ) ?: return
        val interactionOptical = if (
          interactionUniforms.hasOptics && interactionPatch != null
        ) {
          requireRetainedStage(
            trace(GlassTraceSection.InteractionOptical) {
              recordInteractionOptical(
                input = depthInput,
                key = render.opticalKey,
                patch = interactionPatch,
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
              )
            },
            ::clearRetainedOutput,
          ) ?: return
        } else {
          null
        }
        val completedInteractionOutput = if (
          interactionOptical != null &&
          interactionRefractionDetail != null &&
          interactionRefractionDetailCoverage != null &&
          interactionPatch != null
        ) {
          requireRetainedStage(
            trace(GlassTraceSection.InteractionDetail) {
              recordInteractionRefractionComposite(
                optical = interactionOptical,
                detail = interactionRefractionDetail,
                coverage = interactionRefractionDetailCoverage,
                patch = interactionPatch,
              )
            },
            ::clearRetainedOutput,
          ) ?: return
        } else {
          interactionOptical
        }
        if (completedInteractionOutput != null && interactionPatch != null) {
          updateInteractionOutputEffect(
            layer = completedInteractionOutput,
            input = if (completedInteractionOutput === interactionOptical) {
              checkNotNull(interactionOpticalPlatformEffect)
            } else {
              null
            },
            patch = interactionPatch,
            featherWidth = maxOf(params.sampleStepPx, 1f),
          )
        }
        if (interactionUniforms.hasLighting && interactionPatch != null) {
          requireRetainedStage(
            trace(GlassTraceSection.InteractionLighting) {
              recordInteractionLighting(
                key = GlassInteractionLightingKey(
                  coordinates = interactionPatch.coordinates,
                  edgeSoftnessPx = params.edgeSoftnessPx,
                  cornerRadii = params.cornerRadii,
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
        completed = true
      } finally {
        if (!completed) {
          clearRetainedOutput()
        }
      }
    }

  override fun DrawScope.drawForeground(context: VisualEffectContext): Unit =
    trace(GlassTraceSection.Compose) {
      val render = preparedRender ?: return
      val params = preparedParams ?: return
      requireDrawableMaterialSize(params.coordinates.materialSize, ::clearRetainedOutput) ?: return
      if (!retainedOutputAvailable) return
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
        drawCompletedLayer(rim, context, params, alpha = render.alpha)
      }
    }

  override fun canDrawRetainedOutput(): Boolean {
    val fusedOutputAvailable = preparedFusedEffect != null
    val detailRequired = preparedRenderEffects?.refractionDetail != null ||
      preparedRenderEffects == null && lastSuccessfulStageInputs?.detail != null
    val interactionUniforms = preparedInteractionUniforms
    val interactionPatchAvailable = preparedInteractionPatch != null
    val interactionOpticsRequired = !fusedOutputAvailable &&
      interactionPatchAvailable &&
      interactionUniforms?.hasOptics == true
    val interactionDetailRequired = interactionOpticsRequired && detailRequired
    val interactionLightingRequired = !fusedOutputAvailable &&
      interactionPatchAvailable &&
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

  override fun clearRetainedOutput() {
    releaseRetainedResources(releaseShaderHandles = false)
  }

  private fun clearRetainedMetadata() {
    retainedOutputAvailable = false
    lastSuccessfulSourceSnapshot = null
    lastSuccessfulStageInputs = null
    clearSharedBlurSliceMetadata()
    clearSharedOpticalSliceMetadata()
    clearSharedRefractionDetailSliceMetadata()
  }

  private fun clearInteractionLayerMetadata() {
    clearInteractionOpticalLayerMetadata()
    clearInteractionRefractionLayerMetadata()
    clearInteractionLightingLayerMetadata()
    interactionOpticalEffectKey = null
    interactionOpticalEffectUniforms = null
    interactionOpticalPlatformEffect = null
    interactionOpticalComposeEffect = null
    interactionOutputEffect = null
    interactionOutputInput = null
    interactionOutputUniforms = null
    interactionOutputFeatherWidth = Float.NaN
    interactionOutputComposeEffect = null
    interactionDetailEffectKey = null
    interactionDetailEffectUniforms = null
    interactionDetailComposeEffect = null
    interactionDetailCoverageEffectKey = null
    interactionDetailCoverageEffectUniforms = null
    interactionDetailCoverageComposeEffect = null
    interactionLightingEffectKey = null
    interactionLightingEffectUniforms = null
    interactionLightingComposeEffect = null
  }

  private fun clearInteractionOpticalLayerMetadata() {
    recordedInteractionOpticalLayer = null
    recordedInteractionOpticalInput = null
    recordedInteractionOpticalKey = null
  }

  private fun clearInteractionRefractionLayerMetadata() {
    recordedInteractionDetailLayer = null
    recordedInteractionDetailInput = null
    recordedInteractionDetailKey = null
    recordedInteractionDetailCoverageLayer = null
    recordedInteractionDetailCoverageInput = null
    recordedInteractionDetailCoverageKey = null
    recordedInteractionCompositeLayer = null
    recordedInteractionCompositeOptical = null
    recordedInteractionCompositeDetail = null
    recordedInteractionCompositeCoverage = null
    recordedInteractionCompositeSize = null
    interactionDetailEffectLayer = null
    interactionDetailCoverageEffectLayer = null
  }

  private fun clearInteractionLightingLayerMetadata() {
    recordedInteractionLightingLayer = null
    recordedInteractionLightingSize = null
    interactionLightingEffectLayer = null
  }

  private fun clearSharedBlurSliceMetadata() {
    recordedSharedBlurLayer = null
    recordedSharedBlurTargetLayer = null
    recordedSharedBlurBounds = null
    recordedSharedBlurCaptureScale = Float.NaN
    recordedSharedBlurLocalTopLeft = null
    recordedSharedBlurSampleSize = null
  }

  private fun clearSharedOpticalSliceMetadata() {
    recordedSharedOpticalSlice = null
  }

  private fun clearSharedRefractionDetailSliceMetadata() {
    recordedSharedRefractionDetailSlice = null
  }

  override fun detach() {
    SharedGlassBlurRegistry.remove(this)
    releaseRetainedResources(releaseShaderHandles = false)
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    if (shouldReleaseRetainedGlass(level)) {
      SharedGlassBlurRegistry.releaseFor(this)
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
      blurPrefilterShader = null
      opticalShader = null
      refractionDetailShader = null
      refractionDetailCoverageShader = null
      rimShader = null
      fusedShader = null
      fusedInputKey = null
      interactionOpticalEffect = null
      interactionDetailEffect = null
      interactionDetailCoverageEffect = null
      interactionLightingEffect = null
      interactionOutputEffect = null
    }
    clearInteractionLayerMetadata()
    preparedParams = null
    preparedRender = null
    preparedRenderEffects = null
    preparedInteractionUniforms = null
    preparedInteractionPatch = null
    preparedSourceAvailable = false
    preparedStageAvailability = null
    preparedSharedBlurGroup = null
    preparedFusedEffect = null
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
    sharedBlur: Boolean = false,
  ): GlassStageAvailability {
    val blur = effects.blur?.takeIf { shouldBlur(params, effects) }
    return GlassStageAvailability(
      blur = blur == null || if (sharedBlur) {
        layers.hasBlurred
      } else {
        layers.hasBlurred && layers.hasBlurHorizontal &&
          (!blur.key.plan.requiresPrefilter || layers.hasBlurPrefiltered)
      },
      depth = effects.mergeDepthIntoOptical ||
        !shouldDepthMix(params, effects) ||
        layers.hasDepthMixed,
      optical = layers.hasOptical,
      detail = effects.refractionDetail == null ||
        layers.hasRefractionDetail && layers.hasRefractionDetailCoverage &&
        layers.hasRefractionComposite,
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
    source.alpha = 1f
    source.blendMode = BlendMode.SrcOver
    val workingSize = plan.workingSize
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

  private fun DrawScope.recordSharedBlurred(
    group: SharedGlassBlurGroup,
    context: VisualEffectContext,
    params: GlassRenderParams,
    source: GraphicsLayer,
    effects: GlassRenderEffects,
  ): GraphicsLayer? {
    val shared = with(group) { obtain(context) }
    if (shared == null) {
      val graphicsContext = context.requireGraphicsContext()
      val plan = effects.blur?.key?.plan ?: return source
      layers.updateBlurWorkingSize(plan.workingSize, graphicsContext)
      if (plan.requiresPrefilter) layers.ensureBlurPrefiltered(graphicsContext)
      layers.ensureBlurHorizontal(graphicsContext)
      layers.ensureBlurred(graphicsContext)
      clearSharedBlurSliceMetadata()
      return recordBlurredIfNeeded(source, params, effects)
    }

    val blurred = layers.blurred?.takeUnless { it.isReleased } ?: return null
    val localTopLeft = context.position - context.layerOffset
    val sampleSize = params.coordinates.sampleSize.roundToIntSize()
    if (
      recordedSharedBlurLayer !== shared.layer ||
      recordedSharedBlurTargetLayer !== blurred ||
      recordedSharedBlurBounds != shared.bounds ||
      recordedSharedBlurCaptureScale != shared.captureScale ||
      recordedSharedBlurLocalTopLeft != localTopLeft ||
      recordedSharedBlurSampleSize != sampleSize
    ) {
      val sampleOffset = (localTopLeft - shared.bounds.topLeft) * shared.captureScale
      blurred.alpha = 1f
      blurred.blendMode = BlendMode.SrcOver
      blurred.scaleX = 1f
      blurred.scaleY = 1f
      blurred.pivotOffset = Offset.Zero
      blurred.renderEffect = null
      blurred.record(sampleSize) {
        translate(-sampleOffset) {
          drawLayer(shared.layer)
        }
      }
      recordedSharedBlurLayer = shared.layer
      recordedSharedBlurTargetLayer = blurred
      recordedSharedBlurBounds = shared.bounds
      recordedSharedBlurCaptureScale = shared.captureScale
      recordedSharedBlurLocalTopLeft = localTopLeft
      recordedSharedBlurSampleSize = sampleSize
      blurRecordCount++
    }
    return blurred
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

  private fun DrawScope.recordFusedOutput(
    source: GraphicsLayer,
    params: GlassRenderParams,
    renderEffect: PlatformRenderEffect,
  ): GraphicsLayer? = layers.optical?.takeUnless { it.isReleased }?.also { layer ->
    clearSharedOpticalSliceMetadata()
    clearSharedRefractionDetailSliceMetadata()
    source.alpha = 1f
    source.blendMode = BlendMode.SrcOver
    layer.alpha = 1f
    layer.blendMode = BlendMode.SrcOver
    layer.compositingStrategy = CompositingStrategy.Auto
    layer.renderEffect = renderEffect.asComposeRenderEffect()
    layer.record(params.coordinates.sampleSize.roundToIntSize()) {
      drawLayer(source)
    }
    opticalRecordCount++
  }

  private fun DrawScope.recordOptical(
    input: GraphicsLayer,
    source: GraphicsLayer,
    blurred: GraphicsLayer,
    params: GlassRenderParams,
    effects: GlassRenderEffects,
  ): GraphicsLayer? = layers.optical?.takeUnless { it.isReleased }?.also { layer ->
    input.alpha = 1f
    input.blendMode = BlendMode.SrcOver
    layer.alpha = 1f
    layer.renderEffect = effects.optical.asComposeRenderEffect()
    val size = params.coordinates.sampleSize.roundToIntSize()
    if (effects.mergeDepthIntoOptical) {
      trace(GlassTraceSection.Depth) {
        recordDepthMix(
          layer = layer,
          size = size,
          source = source,
          blurred = blurred,
          depth = params.depth,
        )
        depthRecordCount++
      }
    } else {
      layer.compositingStrategy = CompositingStrategy.Auto
      layer.record(size) {
        drawLayer(input)
      }
    }
    opticalRecordCount++
  }

  private fun DrawScope.recordSharedOptical(
    group: SharedGlassBlurGroup,
    source: GraphicsLayer,
    blurred: GraphicsLayer,
    params: GlassRenderParams,
    opticalKey: GlassOpticalEffectKey,
    effects: GlassRenderEffects,
    invalidated: Boolean,
  ): GraphicsLayer? {
    val wasShared = recordedSharedOpticalSlice != null
    val shared = with(group) {
      obtainOptical(
        owner = this@RuntimeShaderGlassDelegate,
        opticalKey = opticalKey,
        depth = params.depth,
      )
    }
    val target = layers.optical?.takeUnless { it.isReleased } ?: return null
    val expectedSize = params.coordinates.sampleSize.roundToIntSize()
    if (shared == null || shared.tileSize != expectedSize) {
      clearSharedOpticalSliceMetadata()
      return if (invalidated || wasShared) {
        recordOptical(
          input = source,
          source = source,
          blurred = blurred,
          params = params,
          effects = effects,
        )
      } else {
        target
      }
    }

    val previousSlice = recordedSharedOpticalSlice
    recordedSharedOpticalSlice = recordSharedAtlasSlice(
      previous = previousSlice,
      source = shared.layer,
      target = target,
      tileOrigin = shared.tileOrigin,
      tileSize = shared.tileSize,
      resetCompositingStrategy = true,
    )
    if (recordedSharedOpticalSlice !== previousSlice) {
      opticalRecordCount++
    }
    return target
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
      layer.record(params.coordinates.sampleSize.roundToIntSize()) {
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
      layer.record(params.coordinates.sampleSize.roundToIntSize()) {
        optical.blendMode = BlendMode.SrcOver
        drawLayer(optical)
        coverage.blendMode = BlendMode.DstOut
        drawLayer(coverage)
        detail.blendMode = BlendMode.Plus
        drawLayer(detail)
      }
      detailRecordCount++
    }

  private fun DrawScope.recordSharedRefractionDetail(
    group: SharedGlassBlurGroup,
    source: GraphicsLayer,
    params: GlassRenderParams,
    detail: GlassRefractionDetailRenderEffect,
    invalidated: Boolean,
  ): GraphicsLayer? {
    val wasShared = recordedSharedRefractionDetailSlice != null
    val shared = with(group) {
      obtainRefractionDetail(
        owner = this@RuntimeShaderGlassDelegate,
        detailKey = detail.key,
      )
    }
    val target = layers.refractionDetail?.takeUnless { it.isReleased } ?: return null
    val expectedSize = params.coordinates.sampleSize.roundToIntSize()
    if (shared == null || shared.tileSize != expectedSize) {
      clearSharedRefractionDetailSliceMetadata()
      return if (invalidated || wasShared) {
        recordRefractionDetail(source, params, detail)
      } else {
        target
      }
    }

    val previousSlice = recordedSharedRefractionDetailSlice
    recordedSharedRefractionDetailSlice = recordSharedAtlasSlice(
      previous = previousSlice,
      source = shared.layer,
      target = target,
      tileOrigin = shared.tileOrigin,
      tileSize = shared.tileSize,
      resetCompositingStrategy = false,
    )
    if (recordedSharedRefractionDetailSlice !== previousSlice) {
      detailRecordCount++
    }
    return target
  }

  private fun DrawScope.recordSharedAtlasSlice(
    previous: SharedGlassAtlasSlice?,
    source: GraphicsLayer,
    target: GraphicsLayer,
    tileOrigin: Offset,
    tileSize: androidx.compose.ui.unit.IntSize,
    resetCompositingStrategy: Boolean,
  ): SharedGlassAtlasSlice {
    if (previous?.matches(source, target, tileOrigin, tileSize) == true) return previous

    target.alpha = 1f
    target.blendMode = BlendMode.SrcOver
    target.scaleX = 1f
    target.scaleY = 1f
    target.pivotOffset = Offset.Zero
    if (resetCompositingStrategy) {
      target.compositingStrategy = CompositingStrategy.Auto
    }
    target.renderEffect = null
    target.record(tileSize) {
      translate(-tileOrigin) {
        drawLayer(source)
      }
    }
    return SharedGlassAtlasSlice(
      source = source,
      target = target,
      tileOrigin = tileOrigin,
      tileSize = tileSize,
    )
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
    patch: GlassInteractionPatch,
  ): GraphicsLayer? = layers.interactionOptical?.takeUnless { it.isReleased }?.also { layer ->
    val localKey = key.copy(coordinates = patch.coordinates)
    input.alpha = 1f
    input.blendMode = BlendMode.SrcOver
    layer.alpha = 1f
    updateInteractionOpticalEffect(layer, localKey, patch.uniforms)
    if (
      layer !== recordedInteractionOpticalLayer ||
      input !== recordedInteractionOpticalInput ||
      localKey != recordedInteractionOpticalKey
    ) {
      val origin = patch.bounds.topLeft
      layer.record(patch.bounds.size) {
        translate(Offset(-origin.x.toFloat(), -origin.y.toFloat())) { drawLayer(input) }
      }
      recordedInteractionOpticalLayer = layer
      recordedInteractionOpticalInput = input
      recordedInteractionOpticalKey = localKey
    }
  }

  private fun DrawScope.recordInteractionRefractionDetail(
    input: GraphicsLayer,
    key: GlassRefractionDetailEffectKey,
    patch: GlassInteractionPatch,
  ): GraphicsLayer? = layers.interactionRefractionDetail
    ?.takeUnless { it.isReleased }
    ?.also { layer ->
      input.alpha = 1f
      input.blendMode = BlendMode.SrcOver
      layer.alpha = 1f
      layer.blendMode = BlendMode.SrcOver
      val localKey = key.copy(
        sampleSize = patch.coordinates.sampleSize,
        materialOrigin = patch.coordinates.materialOrigin,
        materialSize = patch.coordinates.materialSize,
      )
      updateInteractionDetailEffect(layer, localKey, patch.uniforms)
      if (
        layer !== recordedInteractionDetailLayer ||
        input !== recordedInteractionDetailInput ||
        localKey != recordedInteractionDetailKey
      ) {
        val origin = patch.bounds.topLeft
        layer.record(patch.bounds.size) {
          translate(Offset(-origin.x.toFloat(), -origin.y.toFloat())) { drawLayer(input) }
        }
        recordedInteractionDetailLayer = layer
        recordedInteractionDetailInput = input
        recordedInteractionDetailKey = localKey
      }
    }

  private fun DrawScope.recordInteractionRefractionDetailCoverage(
    input: GraphicsLayer,
    key: GlassRefractionDetailEffectKey,
    patch: GlassInteractionPatch,
  ): GraphicsLayer? = layers.interactionRefractionDetailCoverage
    ?.takeUnless { it.isReleased }
    ?.also { layer ->
      input.alpha = 1f
      input.blendMode = BlendMode.SrcOver
      layer.alpha = 1f
      layer.blendMode = BlendMode.SrcOver
      val localKey = key.copy(
        sampleSize = patch.coordinates.sampleSize,
        materialOrigin = patch.coordinates.materialOrigin,
        materialSize = patch.coordinates.materialSize,
      )
      updateInteractionDetailCoverageEffect(
        layer,
        localKey,
        patch.uniforms,
      )
      if (
        layer !== recordedInteractionDetailCoverageLayer ||
        input !== recordedInteractionDetailCoverageInput ||
        localKey != recordedInteractionDetailCoverageKey
      ) {
        val origin = patch.bounds.topLeft
        layer.record(patch.bounds.size) {
          translate(Offset(-origin.x.toFloat(), -origin.y.toFloat())) { drawLayer(input) }
        }
        recordedInteractionDetailCoverageLayer = layer
        recordedInteractionDetailCoverageInput = input
        recordedInteractionDetailCoverageKey = localKey
      }
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
        patch.bounds.size != recordedInteractionCompositeSize
      ) {
        layer.record(patch.bounds.size) {
          drawLayer(optical)
          drawLayer(coverage)
          drawLayer(detail)
        }
        recordedInteractionCompositeLayer = layer
        recordedInteractionCompositeOptical = optical
        recordedInteractionCompositeDetail = detail
        recordedInteractionCompositeCoverage = coverage
        recordedInteractionCompositeSize = patch.bounds.size
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
      layer.record(patch.bounds.size) {
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
  ) {
    val needsUpdate = key != interactionOpticalEffectKey ||
      uniforms != interactionOpticalEffectUniforms || interactionOpticalComposeEffect == null
    if (interactionOpticalEffect == null) {
      interactionOpticalEffect = traceCreateRenderEffect {
        createMutableRuntimeShaderRenderEffect(
          effect = GLASS_INTERACTION_OPTICAL_EFFECT,
          shaderNames = arrayOf("content"),
          inputs = arrayOf(null),
        )
      }
    }
    if (needsUpdate) {
      interactionOpticalPlatformEffect = checkNotNull(interactionOpticalEffect).updateUniforms {
        setOpticalUniforms(key)
        setInteractionOpticalUniforms(uniforms)
      }
      interactionOpticalComposeEffect =
        checkNotNull(interactionOpticalPlatformEffect).asComposeRenderEffect()
      interactionOpticalEffectKey = key
      interactionOpticalEffectUniforms = uniforms
    }
    layer.renderEffect = checkNotNull(interactionOpticalComposeEffect)
  }

  private fun updateInteractionDetailEffect(
    layer: GraphicsLayer,
    key: GlassRefractionDetailEffectKey,
    uniforms: GlassInteractionUniforms,
  ) {
    val needsUpdate = key != interactionDetailEffectKey ||
      uniforms != interactionDetailEffectUniforms || interactionDetailComposeEffect == null
    if (interactionDetailEffect == null) {
      interactionDetailEffect = traceCreateRenderEffect {
        createMutableRuntimeShaderRenderEffect(
          effect = GLASS_INTERACTION_REFRACTION_DETAIL_EFFECT,
          shaderNames = arrayOf("content"),
          inputs = arrayOf(null),
        )
      }
    }
    if (needsUpdate) {
      interactionDetailComposeEffect = checkNotNull(interactionDetailEffect).updateUniforms {
        setRefractionDetailUniforms(key)
        setInteractionDetailUniforms(uniforms)
      }.asComposeRenderEffect()
      interactionDetailEffectKey = key
      interactionDetailEffectUniforms = uniforms
    }
    if (needsUpdate || layer !== interactionDetailEffectLayer) {
      layer.renderEffect = checkNotNull(interactionDetailComposeEffect)
      interactionDetailEffectLayer = layer
    }
  }

  private fun updateInteractionDetailCoverageEffect(
    layer: GraphicsLayer,
    key: GlassRefractionDetailEffectKey,
    uniforms: GlassInteractionUniforms,
  ) {
    val needsUpdate = key != interactionDetailCoverageEffectKey ||
      uniforms != interactionDetailCoverageEffectUniforms ||
      interactionDetailCoverageComposeEffect == null
    if (interactionDetailCoverageEffect == null) {
      interactionDetailCoverageEffect = createMutableRuntimeShaderRenderEffect(
        effect = GLASS_INTERACTION_REFRACTION_DETAIL_COVERAGE_EFFECT,
        shaderNames = arrayOf("content"),
        inputs = arrayOf(null),
      )
    }
    if (needsUpdate) {
      interactionDetailCoverageComposeEffect = checkNotNull(interactionDetailCoverageEffect)
        .updateUniforms {
          setRefractionDetailUniforms(key)
          setInteractionDetailUniforms(uniforms)
        }.asComposeRenderEffect()
      interactionDetailCoverageEffectKey = key
      interactionDetailCoverageEffectUniforms = uniforms
    }
    if (needsUpdate || layer !== interactionDetailCoverageEffectLayer) {
      layer.renderEffect = checkNotNull(interactionDetailCoverageComposeEffect)
      interactionDetailCoverageEffectLayer = layer
    }
  }

  private fun updateInteractionLightingEffect(
    layer: GraphicsLayer,
    key: GlassInteractionLightingKey,
    uniforms: GlassInteractionUniforms,
  ) {
    val needsUpdate = key != interactionLightingEffectKey ||
      uniforms != interactionLightingEffectUniforms || interactionLightingComposeEffect == null
    ensureInteractionLightingEffect()
    if (needsUpdate) {
      interactionLightingComposeEffect = checkNotNull(interactionLightingEffect).updateUniforms {
        setInteractionLightingUniforms(key, uniforms)
      }.asComposeRenderEffect()
      interactionLightingEffectKey = key
      interactionLightingEffectUniforms = uniforms
    }
    if (needsUpdate || layer !== interactionLightingEffectLayer) {
      layer.renderEffect = checkNotNull(interactionLightingComposeEffect)
      interactionLightingEffectLayer = layer
    }
  }

  private fun updateInteractionOutputEffect(
    layer: GraphicsLayer,
    input: PlatformRenderEffect?,
    patch: GlassInteractionPatch,
    featherWidth: Float,
  ) {
    if (interactionOutputEffect == null || input != interactionOutputInput) {
      interactionOutputEffect = createMutableRuntimeShaderRenderEffect(
        effect = GLASS_INTERACTION_OUTPUT_EFFECT,
        shaderNames = arrayOf("content"),
        inputs = arrayOf(input),
      )
      interactionOutputInput = input
      interactionOutputUniforms = null
      interactionOutputFeatherWidth = Float.NaN
      interactionOutputComposeEffect = null
    }
    if (
      patch.uniforms != interactionOutputUniforms ||
      featherWidth != interactionOutputFeatherWidth ||
      interactionOutputComposeEffect == null
    ) {
      interactionOutputComposeEffect = checkNotNull(interactionOutputEffect).updateUniforms {
        setInteractionOutputUniforms(patch.uniforms, featherWidth)
      }.asComposeRenderEffect()
      interactionOutputUniforms = patch.uniforms
      interactionOutputFeatherWidth = featherWidth
    }
    layer.renderEffect = checkNotNull(interactionOutputComposeEffect)
  }

  private fun ensureInteractionLightingEffect() {
    if (interactionLightingEffect != null) return
    interactionLightingEffect = traceCreateRenderEffect {
      createMutableRuntimeShaderRenderEffect(
        effect = GLASS_INTERACTION_LIGHTING_EFFECT,
        shaderNames = arrayOf("content"),
        inputs = arrayOf(null),
      )
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

  private fun DrawScope.drawCompletedOutput(
    optical: GraphicsLayer,
    interactionOutput: GraphicsLayer?,
    interactionPatch: GlassInteractionPatch?,
    context: VisualEffectContext,
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
    context: VisualEffectContext,
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
    // Android mutations update a shared, live RuntimeShader, whereas Skiko snapshots uniforms
    // into each ImageFilter. Every effect mutated here must be reassigned before its retained
    // layer is recorded; never update uniforms on a path that presents retained output as-is.
    if (supportsFusedGlassRenderEffect) {
      return getFusedRenderEffects(render)
    }
    val nextBlurKey = render.blurKey
    if (nextBlurKey != blurKey) {
      blurEffects = nextBlurKey?.let(::updateBlurRenderEffects)
      blurKey = nextBlurKey
    }
    val nextOpticalKey = render.opticalKey
    if (nextOpticalKey != opticalKey || opticalEffect == null) {
      val shader = opticalShader ?: traceCreateRenderEffect {
        createGlassOpticalRenderEffect()
      }.also {
        opticalShader = it
      }
      opticalEffect = shader.updateUniforms { setOpticalUniforms(nextOpticalKey) }
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
          createRefractionDetailRenderEffect()
        }.also {
          refractionDetailShader = it
        }
        shader.updateUniforms { setRefractionDetailUniforms(key) }
      }
      refractionDetailCoverageEffect = nextRefractionDetailKey?.let { key ->
        val shader = refractionDetailCoverageShader
          ?: createSharedRefractionDetailCoverageRenderEffect().also {
            refractionDetailCoverageShader = it
          }
        shader.updateUniforms { setRefractionDetailUniforms(key) }
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
      mergeDepthIntoOptical = supportsMergedGlassDepthOpticalLayer &&
        blurEffects != null &&
        nextBlurKey?.progressive == null &&
        render.params.depth > 0f &&
        render.params.depth < 1f &&
        !render.interactionTopology.hasOptics,
      refractionDetail = currentRefractionDetail,
      rim = rimEffect,
      fused = null,
    )
  }

  private fun getFusedRenderEffects(render: GlassPreparedRender): GlassRenderEffects {
    updateRimEffect(render.rimKey)
    val nextFusedEffectKey = GlassFusedEffectKey(
      blur = render.blurKey,
      blurSigmaPx = render.params.blurSigmaPx,
      depth = render.params.depth,
      optical = render.opticalKey,
      detail = render.refractionDetailKey,
      interaction = render.interactionUniforms.takeIf {
        render.interactionTopology.hasAnyLayer
      },
    )
    if (nextFusedEffectKey != fusedEffectKey || fusedEffect == null) {
      fusedEffect = nextFusedEffectKey.let { key ->
        val nextInputKey = GlassDepthInputKey(
          blur = key.blur,
          blurSigmaPx = key.blurSigmaPx,
          depth = key.depth,
          interactionOptics = render.interactionTopology.hasOptics,
          interactionLighting = render.interactionTopology.hasLighting,
        )
        if (fusedShader == null || fusedInputKey != nextInputKey) {
          fusedShader = createFusedGlassRenderEffect(
            input = createFusedDepthInputRenderEffect(
              blur = key.blur,
              blurSigmaPx = key.blurSigmaPx,
              depth = key.depth,
              sampleSize = key.optical.coordinates.sampleSize,
            ),
            interactionOptics = nextInputKey.interactionOptics,
            interactionLighting = nextInputKey.interactionLighting,
          )
          fusedInputKey = nextInputKey
        }
        val shader = checkNotNull(fusedShader)
        shader.updateUniforms {
          setOpticalUniforms(key.optical)
          setFusedDetailUniforms(key.detail)
          key.interaction?.let {
            setFusedInteractionUniforms(
              uniforms = it,
              interactionOptics = nextInputKey.interactionOptics,
              interactionLighting = nextInputKey.interactionLighting,
            )
          }
        }
      }
      fusedEffectKey = nextFusedEffectKey
    }
    val currentFusedEffect = checkNotNull(fusedEffect)
    return GlassRenderEffects(
      blur = null,
      optical = currentFusedEffect,
      mergeDepthIntoOptical = false,
      refractionDetail = null,
      rim = rimEffect,
      fused = currentFusedEffect,
    )
  }

  private fun updateRimEffect(nextRimKey: GlassRimEffectKey?) {
    if (nextRimKey != rimKey) {
      rimEffect = nextRimKey?.takeIf { it.specularIntensity > 0f }?.let { key ->
        val shader = rimShader ?: traceCreateRenderEffect {
          createGlassRimRenderEffect()
        }.also { rimShader = it }
        shader.updateUniforms { setRimUniforms(key) }
      }
      rimKey = nextRimKey
    }
  }

  private fun createFusedDepthInputRenderEffect(
    blur: GlassBlurEffectKey?,
    blurSigmaPx: Float,
    depth: Float,
    sampleSize: Size,
  ): PlatformRenderEffect? {
    if (blurSigmaPx <= 0.0001f || depth <= 0.0001f) return null
    val plan = blur?.plan ?: return null
    val offsetScale = 1f / plan.scaleFactor
    val horizontalKernel = plan.horizontalKernel.copy(
      taps = plan.horizontalKernel.taps.map { tap ->
        tap.copy(offsetPx = tap.offsetPx * offsetScale)
      },
    )
    val verticalKernel = plan.verticalKernel.copy(
      taps = plan.verticalKernel.taps.map { tap ->
        tap.copy(offsetPx = tap.offsetPx * offsetScale)
      },
    )
    val prefilter = if (plan.requiresPrefilter) {
      // The retained path gets additional low-pass energy from rasterizing at half resolution
      // and scaling back up. Reproduce that response inside the one-layer graph.
      var result: PlatformRenderEffect? = null
      repeat(FUSED_DOWNSAMPLE_PREFILTER_PASSES) {
        result = createFusedGlassBlurPrefilterRenderEffect(result).updateUniforms {
          setFloatUniform("sampleSize", sampleSize.width, sampleSize.height)
          setFloatUniform("strength", 1f)
        }
      }
      result = createFusedGlassBlurPrefilterRenderEffect(result).updateUniforms {
        setFloatUniform("sampleSize", sampleSize.width, sampleSize.height)
        setFloatUniform("strength", FUSED_DOWNSAMPLE_FINAL_PREFILTER_STRENGTH)
      }
      result
    } else {
      null
    }
    val horizontal = createFusedGlassBlurRenderEffect(
      horizontal = true,
      progressive = blur.progressive != null,
      input = prefilter,
    ).updateUniforms {
      setFusedGlassBlurUniforms(blur, horizontalKernel, sampleSize)
      blur.progressive?.toShader(blur.maskSize)?.let { setChildShader("mask", it) }
    }
    val vertical = createFusedGlassBlurRenderEffect(
      horizontal = false,
      progressive = blur.progressive != null,
      input = horizontal,
    ).updateUniforms {
      setFusedGlassBlurUniforms(blur, verticalKernel, sampleSize)
      blur.progressive?.toShader(blur.maskSize)?.let { setChildShader("mask", it) }
    }
    return createGlassDepthInputRenderEffect(vertical, depth)
  }

  private fun updateBlurRenderEffects(key: GlassBlurEffectKey): GlassBlurRenderEffects? {
    if (key.plan.isIdentity) return null
    val progressive = key.progressive != null
    val horizontalShader = if (progressive) {
      progressiveBlurHorizontalShader ?: traceCreateRenderEffect {
        createGlassBlurRenderEffect(
          horizontal = true,
          progressive = true,
        )
      }.also { progressiveBlurHorizontalShader = it }
    } else {
      blurHorizontalShader ?: traceCreateRenderEffect {
        createGlassBlurRenderEffect(
          horizontal = true,
          progressive = false,
        )
      }.also { blurHorizontalShader = it }
    }
    val verticalShader = if (progressive) {
      progressiveBlurVerticalShader ?: traceCreateRenderEffect {
        createGlassBlurRenderEffect(
          horizontal = false,
          progressive = true,
        )
      }.also { progressiveBlurVerticalShader = it }
    } else {
      blurVerticalShader ?: traceCreateRenderEffect {
        createGlassBlurRenderEffect(
          horizontal = false,
          progressive = false,
        )
      }.also { blurVerticalShader = it }
    }
    val progressiveMask = key.progressive?.toShader(key.maskSize)
    val horizontal = horizontalShader.updateUniforms {
      setGlassBlurUniforms(
        key = key,
        kernel = key.plan.horizontalKernel,
        sampleWidth = key.plan.workingSize.width,
        sampleHeight = key.plan.workingSize.height,
      )
      progressiveMask?.let { setChildShader("mask", it) }
    }
    val vertical = verticalShader.updateUniforms {
      setGlassBlurUniforms(
        key = key,
        kernel = key.plan.verticalKernel,
        sampleWidth = key.plan.workingSize.width,
        sampleHeight = key.plan.workingSize.height,
      )
      progressiveMask?.let { setChildShader("mask", it) }
    }
    val prefilter = key.plan.takeIf { it.requiresPrefilter }?.let { plan ->
      val shader = blurPrefilterShader ?: traceCreateRenderEffect {
        createGlassBlurPrefilterRenderEffect()
      }.also {
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

  private inline fun <T> traceCreateRenderEffect(block: () -> T): T =
    trace(GlassTraceSection.CreateRenderEffect, block)
}

private class SharedGlassAtlasSlice(
  val source: GraphicsLayer,
  val target: GraphicsLayer,
  val tileOrigin: Offset,
  val tileSize: androidx.compose.ui.unit.IntSize,
) {
  fun matches(
    source: GraphicsLayer,
    target: GraphicsLayer,
    tileOrigin: Offset,
    tileSize: androidx.compose.ui.unit.IntSize,
  ): Boolean = this.source === source &&
    this.target === target &&
    this.tileOrigin == tileOrigin &&
    this.tileSize == tileSize
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
  val mergeDepthIntoOptical: Boolean,
  val refractionDetail: GlassRefractionDetailRenderEffect?,
  val rim: PlatformRenderEffect?,
  val fused: PlatformRenderEffect?,
)

private data class GlassFusedEffectKey(
  val blur: GlassBlurEffectKey?,
  val blurSigmaPx: Float,
  val depth: Float,
  val optical: GlassOpticalEffectKey,
  val detail: GlassRefractionDetailEffectKey?,
  val interaction: GlassInteractionUniforms?,
)

private data class GlassDepthInputKey(
  val blur: GlassBlurEffectKey?,
  val blurSigmaPx: Float,
  val depth: Float,
  val interactionOptics: Boolean = false,
  val interactionLighting: Boolean = false,
)

// RuntimeShader child sampling does not reproduce the retained graph's raster downscale/upscale
// transfer analytically. These values are calibrated against the Android semantic-blur and
// adversarial-downsample pixel invariants at the multiscale threshold.
private const val FUSED_DOWNSAMPLE_PREFILTER_PASSES = 8
private const val FUSED_DOWNSAMPLE_FINAL_PREFILTER_STRENGTH = 0.12f

@OptIn(InternalHazeApi::class)
internal data class GlassRefractionDetailRenderEffect(
  val key: GlassRefractionDetailEffectKey,
  val effect: PlatformRenderEffect,
  val coverageEffect: PlatformRenderEffect,
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

internal expect fun createGlassDepthInputRenderEffect(
  blur: PlatformRenderEffect?,
  depth: Float,
): PlatformRenderEffect?

internal fun createFusedGlassRenderEffect(
  input: PlatformRenderEffect? = null,
  interactionOptics: Boolean = false,
  interactionLighting: Boolean = false,
): MutableRuntimeShaderRenderEffect = createMutableRuntimeShaderRenderEffect(
  effect = fusedGlassRuntimeEffect(interactionOptics, interactionLighting),
  shaderNames = arrayOf("content"),
  inputs = arrayOf(input),
)

private fun fusedGlassRuntimeEffect(
  interactionOptics: Boolean,
  interactionLighting: Boolean,
) = when {
  interactionOptics && interactionLighting -> GLASS_INTERACTIVE_FUSED_EFFECT
  interactionOptics -> GLASS_INTERACTION_OPTICS_FUSED_EFFECT
  interactionLighting -> GLASS_INTERACTION_LIGHTING_FUSED_EFFECT
  else -> GLASS_FUSED_EFFECT
}

internal fun createFusedGlassBlurRenderEffect(
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

internal fun createFusedGlassBlurPrefilterRenderEffect(
  input: PlatformRenderEffect?,
): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_FUSED_DOWNSAMPLE_PREFILTER_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(input),
  )

internal expect val supportsFusedGlassRenderEffect: Boolean

/**
 * Whether depth blending can be recorded directly into the optical layer without changing output.
 */
internal expect val supportsMergedGlassDepthOpticalLayer: Boolean

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

internal fun createSharedGlassOpticalAtlasRenderEffect(): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_OPTICAL_ATLAS_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

internal fun createSharedRefractionDetailRenderEffect(): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_REFRACTION_DETAIL_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

internal fun createSharedRefractionDetailCoverageRenderEffect(): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_REFRACTION_DETAIL_COVERAGE_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

internal fun createSharedRefractionDetailAtlasRenderEffect(): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_REFRACTION_DETAIL_ATLAS_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

internal fun createSharedGlassRimRenderEffect(): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_RIM_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

internal fun RuntimeShaderUniformProvider.setGlassBlurUniforms(
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

private fun RuntimeShaderUniformProvider.setFusedGlassBlurUniforms(
  key: GlassBlurEffectKey,
  kernel: SemanticBlurKernel,
  sampleSize: Size,
) {
  setFloatUniform("sampleSize", sampleSize.width, sampleSize.height)
  setFloatUniform("materialOrigin", key.maskOrigin.x, key.maskOrigin.y)
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
private val GLASS_INTERACTION_LIGHTING_FUSED_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildFused(interactionLighting = true))
}
private val GLASS_INTERACTIVE_FUSED_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(
    GlassShaders.buildFused(
      interactionOptics = true,
      interactionLighting = true,
    ),
  )
}
private val GLASS_DOWNSAMPLE_PREFILTER_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildDownsamplePrefilter())
}
private val GLASS_FUSED_DOWNSAMPLE_PREFILTER_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildFusedDownsamplePrefilter())
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
private val GLASS_OPTICAL_ATLAS_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildOptical(tiled = true))
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
private val GLASS_REFRACTION_DETAIL_ATLAS_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(GlassShaders.buildRefractionDetail(tiled = true))
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

internal fun RuntimeShaderUniformProvider.setOpticalUniforms(
  key: GlassOpticalEffectKey,
) {
  setFloatUniform(
    "sampleSize",
    key.coordinates.sampleSize.width,
    key.coordinates.sampleSize.height,
  )
  setFloatUniform(
    "materialOrigin",
    key.coordinates.materialOrigin.x,
    key.coordinates.materialOrigin.y,
  )
  setOpticalStyleUniforms(key)
}

internal fun RuntimeShaderUniformProvider.setOpticalAtlasUniforms(
  key: GlassOpticalEffectKey,
  tileSize: IntSize,
  columns: Int,
  tileKeys: List<GlassOpticalEffectKey>,
) {
  require(tileKeys.size <= GlassShaders.SHARED_GLASS_ATLAS_TILE_CAPACITY)
  setFloatUniform("tileSize", tileSize.width.toFloat(), tileSize.height.toFloat())
  setFloatUniform("atlasColumns", columns.toFloat())
  repeat(GlassShaders.SHARED_GLASS_ATLAS_TILE_CAPACITY) { index ->
    val coordinates = tileKeys.getOrNull(index)?.coordinates
    setFloatUniform(
      "tileGeometry$index",
      coordinates?.sampleSize?.width ?: 0f,
      coordinates?.sampleSize?.height ?: 0f,
      coordinates?.materialOrigin?.x ?: 0f,
      coordinates?.materialOrigin?.y ?: 0f,
    )
  }
  setOpticalStyleUniforms(key)
}

private fun RuntimeShaderUniformProvider.setOpticalStyleUniforms(
  key: GlassOpticalEffectKey,
) {
  setFloatUniform(
    "materialSize",
    key.coordinates.materialSize.width,
    key.coordinates.materialSize.height,
  )
  setFloatUniform("sampleStep", key.sampleStepPx)
  setFloatUniform("edgeSoftness", key.edgeSoftnessPx)
  setFloatUniform(
    "cornerRadii",
    key.cornerRadii.topLeft,
    key.cornerRadii.topRight,
    key.cornerRadii.bottomRight,
    key.cornerRadii.bottomLeft,
  )
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
  setRefractionDetailStyleUniforms(key)
}

private fun RuntimeShaderUniformProvider.setFusedDetailUniforms(
  key: GlassRefractionDetailEffectKey?,
) {
  if (key != null) {
    setRefractionDetailUniforms(key)
  } else {
    setFloatUniform("detailWidth", 0f)
    setFloatUniform("detailIntensity", 0f)
    setFloatUniform("detailVisibility", 0f)
  }
}

internal fun RuntimeShaderUniformProvider.setRefractionDetailAtlasUniforms(
  key: GlassRefractionDetailEffectKey,
  tileSize: IntSize,
  columns: Int,
  tileKeys: List<GlassRefractionDetailEffectKey>,
) {
  require(tileKeys.size <= GlassShaders.SHARED_GLASS_ATLAS_TILE_CAPACITY)
  setFloatUniform("tileSize", tileSize.width.toFloat(), tileSize.height.toFloat())
  setFloatUniform("atlasColumns", columns.toFloat())
  repeat(GlassShaders.SHARED_GLASS_ATLAS_TILE_CAPACITY) { index ->
    val tileKey = tileKeys.getOrNull(index)
    setFloatUniform(
      "tileGeometry$index",
      tileKey?.sampleSize?.width ?: 0f,
      tileKey?.sampleSize?.height ?: 0f,
      tileKey?.materialOrigin?.x ?: 0f,
      tileKey?.materialOrigin?.y ?: 0f,
    )
  }
  setRefractionDetailStyleUniforms(key)
}

private fun RuntimeShaderUniformProvider.setRefractionDetailStyleUniforms(
  key: GlassRefractionDetailEffectKey,
) {
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

private fun RuntimeShaderUniformProvider.setFusedInteractionUniforms(
  uniforms: GlassInteractionUniforms,
  interactionOptics: Boolean,
  interactionLighting: Boolean,
) {
  setInteractionPositionUniforms(uniforms)
  if (interactionOptics) {
    setFloatUniform("interactionOpticalActive", if (uniforms.hasOptics) 1f else 0f)
    setFloatUniform("interactionRefractionMultiplier", uniforms.refractionMultiplier)
    setFloatUniform("interactionWhitePointDelta", uniforms.whitePointDelta)
  }
  if (interactionLighting) {
    setFloatUniform("interactionLightingIntensity", uniforms.lightingIntensity)
  }
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
