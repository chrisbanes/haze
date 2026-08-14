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
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.MutableRuntimeShaderRenderEffect
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.asComposeRenderEffect
import dev.chrisbanes.haze.createBlendRenderEffect
import dev.chrisbanes.haze.createMutableRuntimeShaderRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import dev.chrisbanes.haze.trace

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class RuntimeShaderGlassDelegate(
  private val effect: GlassRuntimeEffect,
  runtimeEffectFactory: GlassRuntimeEffectFactory = PlatformGlassRuntimeEffectFactory,
) : GlassRuntimeEffect.Delegate, RetainedOutputDelegate {
  private var runtimeEffectFactory = runtimeEffectFactory

  internal val runtimeEffectFactoryForTest: GlassRuntimeEffectFactory
    get() = runtimeEffectFactory

  internal fun updateRuntimeEffectFactory(value: GlassRuntimeEffectFactory) {
    runtimeEffectFactory = value
  }

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
  private var fusedDepthInputShaders: FusedDepthInputShaders? = null
  private var fusedEffect: PlatformRenderEffect? = null
  private var interactionOpticalEffect: MutableRuntimeShaderRenderEffect? = null
  private var interactionDetailEffect: MutableRuntimeShaderRenderEffect? = null
  private var interactionDetailCoverageEffect: MutableRuntimeShaderRenderEffect? = null
  private var interactionLightingEffect: MutableRuntimeShaderRenderEffect? = null
  private var interactionOpticalEffectKey: GlassOpticalEffectKey? = null
  private var interactionOpticalEffectUniforms: GlassInteractionUniforms? = null
  private var interactionOpticalPlatformEffect: PlatformRenderEffect? = null
  private var interactionOpticalComposeEffect: RenderEffect? = null
  private var interactionOpticalEffectLayer: GraphicsLayer? = null
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
  private var recordedInteractionCompositeOpticalRecordCount: Int = -1
  private var recordedInteractionCompositeDetailRecordCount: Int = -1
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
  private var retainedOutputAvailable: Boolean = false
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
        layers.release(currentGraphicsContext)
        layers.scaledSize = scaledSize
        clearInteractionLayerMetadata()
        clearRetainedMetadata()
      }

      preparedSourceAvailable = layers.hasSource
      preparedStageAvailability = if (supportsFusedGlassRenderEffect) {
        GlassStageAvailability(
          blur = true,
          depth = true,
          optical = layers.hasOptical,
          detail = true,
          rim = currentRenderEffects.rim == null || layers.hasRim,
        )
      } else {
        stageAvailability(params = params, effects = currentRenderEffects)
      }

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
        layers.updateBlurWorkingSize(blurEffects.key.plan.workingSize, currentGraphicsContext)
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
      layers.groupAlpha.prepare(
        required = currentPreparedRender.groupCompositeSize != null,
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
      )
      val detail = effects.refractionDetail
      val outputLayer = if (detail != null) {
        val localDetailKey = detail.key.copy(
          sampleSize = patch.coordinates.sampleSize,
          materialOrigin = patch.coordinates.materialOrigin,
          materialSize = patch.coordinates.materialSize,
        )
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
        input = if (detail == null) checkNotNull(interactionOpticalPlatformEffect) else null,
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
        key = GlassInteractionLightingKey(
          coordinates = patch.coordinates,
          edgeSoftnessPx = params.edgeSoftnessPx,
          cornerRadii = params.cornerRadii,
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
        val currentInputs = GlassStageInputs(
          blur = if (supportsFusedGlassRenderEffect) {
            render.blurKey
          } else {
            render.blurKey?.takeIf { shouldBlur(params, effects) }
          },
          depth = params.depth,
          optical = if (supportsFusedGlassRenderEffect && render.interactionTopology.hasOptics) {
            render.opticalKey to interactionUniforms
          } else {
            render.opticalKey
          },
          detail = render.refractionDetailKey,
          rim = render.rimKey,
        )
        val sourceState = context.resolveGlassRuntimeSourceState(
          captureScale = params.coordinates.scaleFactor,
          backgroundColor = params.backgroundColor,
          previousSnapshot = lastSuccessfulSourceSnapshot,
        )
        if (
          !sourceState.hasDrawableSource &&
          lastSuccessfulSourceSnapshot?.backgroundColor != params.backgroundColor
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
          previous = lastSuccessfulStageInputs,
          current = currentInputs,
          sourceChanged = shouldRecordSource,
          availability = preparedStageAvailability ?: stageAvailability(params, effects),
        )
        if (supportsFusedGlassRenderEffect) {
          val previousBaseInputs = lastSuccessfulStageInputs?.copy(rim = currentInputs.rim)
          val baseInputsChanged = previousBaseInputs != currentInputs
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
              trace(GlassTraceSection.Rim) { recordRimIfNeeded(params, effects) }
            } else {
              retainedRim(effects)
            },
            ::clearRetainedOutput,
          ) ?: return
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
                inputContentChanged = invalidation.depth,
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

  override fun DrawScope.drawForeground(context: HazeEffectRuntimeDrawScope): Unit =
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

  override fun clearRetainedOutput() {
    releaseRetainedResources(releaseShaderHandles = false)
  }

  private fun clearRetainedMetadata() {
    retainedOutputAvailable = false
    lastSuccessfulSourceSnapshot = null
    lastSuccessfulStageInputs = null
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
    interactionOpticalEffectLayer = null
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
    recordedInteractionCompositeOpticalRecordCount = -1
    recordedInteractionCompositeDetailRecordCount = -1
    interactionDetailEffectLayer = null
    interactionDetailCoverageEffectLayer = null
  }

  private fun clearInteractionLightingLayerMetadata() {
    recordedInteractionLightingLayer = null
    recordedInteractionLightingSize = null
    interactionLightingEffectLayer = null
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
      blurPrefilterShader = null
      opticalShader = null
      refractionDetailShader = null
      refractionDetailCoverageShader = null
      rimShader = null
      fusedShader = null
      fusedInputKey = null
      fusedDepthInputShaders = null
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
      blur = blur == null ||
        layers.hasBlurred && layers.hasBlurHorizontal &&
        (!blur.key.plan.requiresPrefilter || layers.hasBlurPrefiltered),
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
    if (!context.hasDrawableInput) {
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
      backgroundColor = params.backgroundColor,
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
    params: GlassRenderParams,
    effects: GlassRenderEffects,
  ): GraphicsLayer? = layers.optical?.takeUnless { it.isReleased }?.also { layer ->
    input.alpha = 1f
    input.blendMode = BlendMode.SrcOver
    layer.alpha = 1f
    layer.renderEffect = effects.optical.asComposeRenderEffect()
    layer.compositingStrategy = CompositingStrategy.Auto
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
    inputContentChanged: Boolean,
  ): GraphicsLayer? = layers.interactionOptical?.takeUnless { it.isReleased }?.also { layer ->
    val localKey = key.copy(coordinates = patch.coordinates)
    input.alpha = 1f
    input.blendMode = BlendMode.SrcOver
    layer.alpha = 1f
    updateInteractionOpticalEffect(layer, localKey, patch.uniforms)
    if (
      layer !== recordedInteractionOpticalLayer ||
      input !== recordedInteractionOpticalInput ||
      localKey != recordedInteractionOpticalKey ||
      inputContentChanged
    ) {
      val origin = patch.bounds.topLeft
      layer.record(patch.bounds.size) {
        translate(Offset(-origin.x.toFloat(), -origin.y.toFloat())) { drawLayer(input) }
      }
      recordedInteractionOpticalLayer = layer
      recordedInteractionOpticalInput = input
      recordedInteractionOpticalKey = localKey
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
      val localKey = key.copy(
        sampleSize = patch.coordinates.sampleSize,
        materialOrigin = patch.coordinates.materialOrigin,
        materialSize = patch.coordinates.materialSize,
      )
      updateInteractionDetailEffect(layer, localKey, patch.uniforms)
      if (
        layer !== recordedInteractionDetailLayer ||
        input !== recordedInteractionDetailInput ||
        localKey != recordedInteractionDetailKey ||
        inputContentChanged
      ) {
        val origin = patch.bounds.topLeft
        layer.record(patch.bounds.size) {
          translate(Offset(-origin.x.toFloat(), -origin.y.toFloat())) { drawLayer(input) }
        }
        recordedInteractionDetailLayer = layer
        recordedInteractionDetailInput = input
        recordedInteractionDetailKey = localKey
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
        localKey != recordedInteractionDetailCoverageKey ||
        inputContentChanged
      ) {
        val origin = patch.bounds.topLeft
        layer.record(patch.bounds.size) {
          translate(Offset(-origin.x.toFloat(), -origin.y.toFloat())) { drawLayer(input) }
        }
        recordedInteractionDetailCoverageLayer = layer
        recordedInteractionDetailCoverageInput = input
        recordedInteractionDetailCoverageKey = localKey
        interactionDetailRecordCount++
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
        patch.bounds.size != recordedInteractionCompositeSize ||
        interactionOpticalRecordCount != recordedInteractionCompositeOpticalRecordCount ||
        interactionDetailRecordCount != recordedInteractionCompositeDetailRecordCount
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
    if (needsUpdate || layer !== interactionOpticalEffectLayer) {
      layer.renderEffect = checkNotNull(interactionOpticalComposeEffect)
      interactionOpticalEffectLayer = layer
    }
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
      interactionDetailCoverageEffect = traceCreateRenderEffect {
        createMutableRuntimeShaderRenderEffect(
          effect = GLASS_INTERACTION_REFRACTION_DETAIL_COVERAGE_EFFECT,
          shaderNames = arrayOf("content"),
          inputs = arrayOf(null),
        )
      }
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
      interactionOutputEffect = traceCreateRenderEffect {
        createMutableRuntimeShaderRenderEffect(
          effect = GLASS_INTERACTION_OUTPUT_EFFECT,
          shaderNames = arrayOf("content"),
          inputs = arrayOf(input),
        )
      }
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
        createRetainedGlassOpticalRenderEffect()
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
          createRetainedRefractionDetailRenderEffect()
        }.also {
          refractionDetailShader = it
        }
        shader.updateUniforms { setRefractionDetailUniforms(key) }
      }
      refractionDetailCoverageEffect = nextRefractionDetailKey?.let { key ->
        val shader = refractionDetailCoverageShader
          ?: traceCreateRenderEffect {
            createRetainedRefractionDetailCoverageRenderEffect()
          }.also {
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
      refractionDetail = currentRefractionDetail,
      rim = rimEffect,
    )
  }

  private fun getFusedRenderEffects(render: GlassPreparedRender): GlassRenderEffects {
    updateRimEffect(render.rimKey)
    val nextFusedEffectKey = GlassFusedEffectKey(
      blur = render.blurKey,
      depth = render.params.depth,
      optical = render.opticalKey,
      detail = render.refractionDetailKey,
      interaction = render.interactionUniforms.takeIf { render.interactionTopology.hasOptics },
    )
    if (nextFusedEffectKey != fusedEffectKey || fusedEffect == null) {
      fusedEffect = nextFusedEffectKey.let { key ->
        val nextInputKey = GlassDepthInputKey(
          blur = key.blur,
          depth = key.depth,
          interactionOptics = render.interactionTopology.hasOptics,
          sharpDetail = key.detail != null,
        )
        val previousInputKey = fusedInputKey
        val inputChanged = previousInputKey != nextInputKey
        val topologyChanged = previousInputKey == null ||
          previousInputKey.interactionOptics != nextInputKey.interactionOptics ||
          previousInputKey.sharpDetail != nextInputKey.sharpDetail
        val nextInput = if (inputChanged) {
          updateFusedDepthInputRenderEffect(
            blur = key.blur,
            depth = key.depth,
            sampleSize = key.optical.coordinates.sampleSize,
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
            )
          }
          refractionDetailShader = null
        }
        val shader = checkNotNull(fusedShader)
        val updateUniforms: RuntimeShaderUniformProvider.() -> Unit = {
          setOpticalUniforms(key.optical)
          key.detail?.let { setRefractionDetailUniforms(it) }
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
        val detailShader = refractionDetailShader ?: traceCreateRenderEffect {
          createRefractionDetailRenderEffect(
            interactive = nextInputKey.interactionOptics,
            coverageOnly = false,
          )
        }.also {
          refractionDetailShader = it
        }
        val detail = detailShader.updateUniforms {
          setRefractionDetailUniforms(detailKey)
          key.interaction?.let(::setInteractionDetailUniforms)
        }
        wrapGlassRuntimeEffectConstruction {
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
      rimEffect = nextRimKey?.takeIf { it.specularIntensity > 0f }?.let { key ->
        val shader = rimShader ?: traceCreateRenderEffect {
          createRetainedGlassRimRenderEffect()
        }.also { rimShader = it }
        shader.updateUniforms { setRimUniforms(key) }
      }
      rimKey = nextRimKey
    }
  }

  private fun updateFusedDepthInputRenderEffect(
    blur: GlassBlurEffectKey?,
    depth: Float,
    sampleSize: Size,
  ): PlatformRenderEffect? {
    if (depth <= 0.0001f) return null
    val plan = blur?.plan?.takeUnless { it.isIdentity } ?: return null
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
    val progressive = blur.progressive != null
    val shaders = fusedDepthInputShaders
      ?.takeIf {
        it.progressive == progressive &&
          it.prefilters.isNotEmpty() == plan.requiresPrefilter
      }
      ?: FusedDepthInputShaders(
        progressive = progressive,
        prefilters = if (plan.requiresPrefilter) {
          List(FUSED_DOWNSAMPLE_PREFILTER_PASSES + 1) {
            traceCreateRenderEffect {
              createFusedGlassBlurPrefilterRenderEffect(input = null)
            }
          }
        } else {
          emptyList()
        },
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
    val prefilter = if (plan.requiresPrefilter) {
      // The retained path gets additional low-pass energy from rasterizing at half resolution
      // and scaling back up. Reproduce that response inside the one-layer graph.
      var result: PlatformRenderEffect? = null
      shaders.prefilters.forEachIndexed { index, shader ->
        result = shader.updateInputs(inputs = arrayOf(result)) {
          setFloatUniform("sampleSize", sampleSize.width, sampleSize.height)
          setFloatUniform(
            "strength",
            if (index < FUSED_DOWNSAMPLE_PREFILTER_PASSES) {
              1f
            } else {
              FUSED_DOWNSAMPLE_FINAL_PREFILTER_STRENGTH
            },
          )
        }
      }
      result
    } else {
      null
    }
    val progressiveMask = blur.progressive?.toShader(blur.maskSize)
    val horizontal = shaders.horizontal.updateInputs(inputs = arrayOf(prefilter)) {
      setGlassBlurUniforms(blur, horizontalKernel, sampleSize.width, sampleSize.height)
      progressiveMask?.let { setChildShader("mask", it) }
    }
    val vertical = shaders.vertical.updateInputs(inputs = arrayOf(horizontal)) {
      setGlassBlurUniforms(blur, verticalKernel, sampleSize.width, sampleSize.height)
      progressiveMask?.let { setChildShader("mask", it) }
    }
    return createGlassDepthInputRenderEffect(vertical, depth)
  }

  private fun updateBlurRenderEffects(key: GlassBlurEffectKey): GlassBlurRenderEffects? {
    if (key.plan.isIdentity) return null
    val progressive = key.progressive != null
    val horizontalShader = if (progressive) {
      progressiveBlurHorizontalShader ?: traceCreateRenderEffect {
        createRetainedGlassBlurRenderEffect(
          horizontal = true,
          progressive = true,
        )
      }.also { progressiveBlurHorizontalShader = it }
    } else {
      blurHorizontalShader ?: traceCreateRenderEffect {
        createRetainedGlassBlurRenderEffect(
          horizontal = true,
          progressive = false,
        )
      }.also { blurHorizontalShader = it }
    }
    val verticalShader = if (progressive) {
      progressiveBlurVerticalShader ?: traceCreateRenderEffect {
        createRetainedGlassBlurRenderEffect(
          horizontal = false,
          progressive = true,
        )
      }.also { progressiveBlurVerticalShader = it }
    } else {
      blurVerticalShader ?: traceCreateRenderEffect {
        createRetainedGlassBlurRenderEffect(
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
        sampleWidth = key.plan.workingSize.width.toFloat(),
        sampleHeight = key.plan.workingSize.height.toFloat(),
      )
      progressiveMask?.let { setChildShader("mask", it) }
    }
    val vertical = verticalShader.updateUniforms {
      setGlassBlurUniforms(
        key = key,
        kernel = key.plan.verticalKernel,
        sampleWidth = key.plan.workingSize.width.toFloat(),
        sampleHeight = key.plan.workingSize.height.toFloat(),
      )
      progressiveMask?.let { setChildShader("mask", it) }
    }
    val prefilter = key.plan.takeIf { it.requiresPrefilter }?.let { plan ->
      val shader = blurPrefilterShader ?: traceCreateRenderEffect {
        createRetainedGlassBlurPrefilterRenderEffect()
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

  private fun traceCreateRenderEffect(
    block: () -> MutableRuntimeShaderRenderEffect,
  ): MutableRuntimeShaderRenderEffect =
    trace(GlassTraceSection.CreateRenderEffect) {
      runtimeEffectFactory.create(block)
    }
}

@OptIn(InternalHazeApi::class)
internal inline fun <T> wrapGlassRuntimeEffectConstruction(block: () -> T): T = try {
  block()
} catch (failure: RuntimeShaderRenderEffectException) {
  throw failure
} catch (failure: RuntimeException) {
  throw RuntimeShaderRenderEffectException(failure)
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
private class GlassFusedEffectKey(
  val blur: GlassBlurEffectKey?,
  val depth: Float,
  val optical: GlassOpticalEffectKey,
  val detail: GlassRefractionDetailEffectKey?,
  val interaction: GlassInteractionUniforms?,
)

@Poko
private class GlassDepthInputKey(
  val blur: GlassBlurEffectKey?,
  val depth: Float,
  val interactionOptics: Boolean = false,
  val sharpDetail: Boolean = false,
)

private class FusedDepthInputShaders(
  val progressive: Boolean,
  val prefilters: List<MutableRuntimeShaderRenderEffect>,
  val horizontal: MutableRuntimeShaderRenderEffect,
  val vertical: MutableRuntimeShaderRenderEffect,
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

internal expect fun createGlassDepthInputRenderEffect(
  blur: PlatformRenderEffect?,
  depth: Float,
): PlatformRenderEffect?

internal fun createFusedGlassRenderEffect(
  input: PlatformRenderEffect? = null,
  interactionOptics: Boolean = false,
  sharpDetail: Boolean = true,
): MutableRuntimeShaderRenderEffect = createMutableRuntimeShaderRenderEffect(
  effect = fusedGlassRuntimeEffect(interactionOptics, sharpDetail),
  shaderNames = arrayOf("content"),
  inputs = arrayOf(input),
)

private fun fusedGlassRuntimeEffect(
  interactionOptics: Boolean,
  sharpDetail: Boolean,
) = when {
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

internal fun createFusedGlassBlurPrefilterRenderEffect(
  input: PlatformRenderEffect?,
): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_FUSED_DOWNSAMPLE_PREFILTER_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(input),
  )

internal expect val supportsFusedGlassRenderEffect: Boolean

internal fun createRetainedGlassBlurRenderEffect(
  horizontal: Boolean,
  progressive: Boolean,
): MutableRuntimeShaderRenderEffect = createGlassBlurRenderEffectWithInput(
  horizontal = horizontal,
  progressive = progressive,
  input = null,
)

internal fun createRetainedGlassBlurPrefilterRenderEffect(): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_DOWNSAMPLE_PREFILTER_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

internal fun createRetainedGlassOpticalRenderEffect(): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_OPTICAL_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

internal fun createRetainedRefractionDetailRenderEffect(): MutableRuntimeShaderRenderEffect =
  createRefractionDetailRenderEffect(interactive = false, coverageOnly = false)

internal fun createRetainedRefractionDetailCoverageRenderEffect(): MutableRuntimeShaderRenderEffect =
  createRefractionDetailRenderEffect(interactive = false, coverageOnly = true)

private fun createRefractionDetailRenderEffect(
  interactive: Boolean,
  coverageOnly: Boolean,
): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = when {
      interactive && coverageOnly -> GLASS_INTERACTION_REFRACTION_DETAIL_COVERAGE_EFFECT
      interactive -> GLASS_INTERACTION_REFRACTION_DETAIL_EFFECT
      coverageOnly -> GLASS_REFRACTION_DETAIL_COVERAGE_EFFECT
      else -> GLASS_REFRACTION_DETAIL_EFFECT
    },
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
  )

internal fun createRetainedGlassRimRenderEffect(): MutableRuntimeShaderRenderEffect =
  createMutableRuntimeShaderRenderEffect(
    effect = GLASS_RIM_EFFECT,
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
  setFloatUniform("refractionFoldStrength", key.refractionFoldStrength)
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
  setFloatUniform("refractionFoldStrength", key.refractionFoldStrength)
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
