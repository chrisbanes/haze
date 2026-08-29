// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.createMutableRuntimeShaderRenderEffect
import dev.chrisbanes.haze.createRuntimeEffect
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import sun.misc.Unsafe

@OptIn(ExperimentalHazeApi::class, InternalComposeUiApi::class)
class RuntimeShaderGlassDelegateTrimMemoryTest {

  @Test
  fun invalidSkikoSksl_isReportedAsRuntimeShaderConstructionFailure() {
    assertFailure {
      createRuntimeEffect("not valid SKSL")
    }.isInstanceOf<RuntimeShaderRenderEffectException>()
  }

  @Test
  fun runtimeShaderUniformFailure_isNotReportedAsConstructionFailure() {
    val effect = createRuntimeEffect(
      "half4 main(float2 coord) { return half4(1); }",
    )
    val mutableEffect = createMutableRuntimeShaderRenderEffect(
      effect = effect,
      shaderNames = arrayOf("content"),
      inputs = arrayOf(null),
    )
    val unrelatedFailure = IllegalStateException("unrelated uniform failure")

    assertFailure {
      mutableEffect.updateUniforms {
        throw unrelatedFailure
      }
    }.isSameInstanceAs(unrelatedFailure)
  }

  @Test
  fun fractionalAlpha_reusesGroupLayerAndZeroReleasesIt() {
    val effect = GlassRuntimeEffect().apply { alpha = 0.5f }
    val delegate = RuntimeShaderGlassDelegate(effect)
    val context = RecordingVisualEffectContext(
      size = Size(100f, 100f),
      layerSize = Size(100f, 100f),
    )

    delegate.prepareDrawForTest(context, effect)
    val first = checkNotNull(delegate.layers.groupAlpha.layer)
    delegate.prepareDrawForTest(context, effect)
    assertThat(delegate.layers.groupAlpha.layer).isSameInstanceAs(first)

    effect.alpha = 0f
    delegate.prepareDrawForTest(context, effect)

    assertThat(delegate.layers.groupAlpha.layer).isNull()
    assertThat(first in context.graphicsContext.releasedLayers).isTrue()
  }

  @Test
  fun retainedGroupAlphaLayer_fractionalFramesReuseAllocation() {
    val owner = RetainedGlassGroupAlphaLayer()
    val graphicsContext = TestGraphicsContext()

    owner.prepare(required = true, graphicsContext)
    val first = checkNotNull(owner.layer)
    owner.prepare(required = true, graphicsContext)

    assertThat(owner.layer).isSameInstanceAs(first)
    assertThat(graphicsContext.events.filterIsInstance<LayerEvent.Create>().size).isEqualTo(1)
  }

  @Test
  fun retainedGroupAlphaLayer_zeroOrOversizedPathOwnsNoAllocation() {
    val owner = RetainedGlassGroupAlphaLayer()
    val graphicsContext = TestGraphicsContext()

    owner.prepare(required = false, graphicsContext)

    assertThat(owner.layer).isNull()
    assertThat(graphicsContext.events.filterIsInstance<LayerEvent.Create>()).isEqualTo(emptyList())
  }

  @Test
  fun prepareDraw_consumesTheSelectedPreparedRenderParamsInstance() {
    val effect = GlassRuntimeEffect().apply {
      optics = GlassOptics(depth = SizeValue.Fixed(0f))
    }
    val delegate = RuntimeShaderGlassDelegate(effect)
    val context = RecordingVisualEffectContext(
      size = Size(100f, 100f),
      layerSize = Size(100f, 100f),
    )
    effect.prepareRenderBudget(context, runtimeShaderSupported = true)
    val prepared = checkNotNull(effect.preparedRender)

    with(CanvasDrawScope()) {
      with(delegate) { prepareDraw(context) }
    }

    assertThat(delegate.preparedParamsForTest()).isSameInstanceAs(prepared.params)
  }

  @Test
  fun prepareDraw_budgetScaleReductionReleasesAndRebuildsRuntimeLayers() {
    val effect = GlassRuntimeEffect().apply {
      optics = GlassOptics(
        refractionStrength = 0f,
        refractionDisplacement = 0.dp,
        blurRadius = SizeValue.Fixed(0.dp),
      )
    }
    val delegate = RuntimeShaderGlassDelegate(effect)
    val graphicsContext = TestGraphicsContext()
    val initialContext = RecordingVisualEffectContext(
      size = Size(100f, 100f),
      layerSize = Size(100f, 100f),
      graphicsContext = graphicsContext,
    )
    delegate.prepareDrawForTest(initialContext, effect)
    val initialLayers = delegate.layers.allLayers()

    val oversizedContext = RecordingVisualEffectContext(
      size = Size(100f, 100f),
      layerSize = Size(5_000f, 5_000f),
      graphicsContext = graphicsContext,
    )
    delegate.prepareDrawForTest(oversizedContext, effect)
    val prepared = checkNotNull(effect.preparedRender)
    val decision = effect.preparedRenderBudget as GlassRenderBudgetDecision.Runtime

    assertThat(decision.scaleFactor < 1f).isTrue()
    assertThat(graphicsContext.releasedLayers).containsExactly(*initialLayers.toTypedArray())
    assertThat(delegate.layers.scaledSize).isEqualTo(
      prepared.params.coordinates.sampleSize.roundToIntSize(),
    )
    assertThat(delegate.layers.allLayers().all { !it.isReleased }).isTrue()
  }

  @Test
  fun prepareDraw_releasesObsoleteTopologyBeforeCreatingReplacementLayers() {
    val effect = GlassRuntimeEffect().apply {
      optics = GlassOptics(
        refractionStrength = 0.5f,
        refractionDisplacement = 20.dp,
        depth = SizeValue.Fixed(0f),
        blurRadius = SizeValue.Fixed(0.dp),
      )
      specularIntensity = 1f
    }
    val delegate = RuntimeShaderGlassDelegate(effect)
    val graphicsContext = TestGraphicsContext()
    val context = RecordingVisualEffectContext(
      size = Size(100f, 100f),
      layerSize = Size(100f, 100f),
      graphicsContext = graphicsContext,
    )
    delegate.prepareDrawForTest(context, effect)
    val retainedSource = checkNotNull(delegate.layers.source)
    val retainedOptical = checkNotNull(delegate.layers.optical)
    delegate.layers.interactionOptical = graphicsContext.createGraphicsLayer()
    delegate.layers.interactionRefractionDetail = graphicsContext.createGraphicsLayer()
    delegate.layers.interactionLighting = graphicsContext.createGraphicsLayer()
    val obsoleteLayers = listOf(
      checkNotNull(delegate.layers.refractionDetail),
      checkNotNull(delegate.layers.rim),
      checkNotNull(delegate.layers.interactionOptical),
      checkNotNull(delegate.layers.interactionRefractionDetail),
      checkNotNull(delegate.layers.interactionLighting),
    )
    graphicsContext.events.clear()

    effect.optics = GlassOptics(
      refractionStrength = 0f,
      refractionDisplacement = 0.dp,
      depth = SizeValue.Fixed(0.5f),
      blurRadius = SizeValue.Fixed(24.dp),
    )
    effect.specularIntensity = 0f
    delegate.prepareDrawForTest(context, effect)

    val firstCreate = graphicsContext.events.indexOfFirst { it is LayerEvent.Create }
    val lastObsoleteRelease = graphicsContext.events.indexOfLast {
      it is LayerEvent.Release && it.layer in obsoleteLayers
    }
    assertThat(lastObsoleteRelease).isGreaterThanOrEqualTo(0)
    assertThat(firstCreate).isGreaterThan(lastObsoleteRelease)
    assertThat(delegate.layers.source).isSameInstanceAs(retainedSource)
    assertThat(delegate.layers.optical).isSameInstanceAs(retainedOptical)
  }

  @Test
  fun prepareDraw_budgetDecisionTransitionsRuntimeToFallbackToFreshRuntime() {
    val effect = GlassRuntimeEffect()
    val graphicsContext = TestGraphicsContext()
    val safeContext = RecordingVisualEffectContext(
      size = Size(100f, 100f),
      layerSize = Size(100f, 100f),
      graphicsContext = graphicsContext,
    )

    effect.attach(safeContext)
    effect.prepareDrawForTest(safeContext)
    val firstRuntime = effect.delegate as RuntimeShaderGlassDelegate
    val firstLayers = firstRuntime.layers.allLayers()

    effect.prepareDrawForTest(
      RecordingVisualEffectContext(
        size = Size(100f, 100f),
        layerSize = Size(50_000f, 50_000f),
        graphicsContext = graphicsContext,
      ),
    )
    assertThat(effect.delegate is FallbackGlassDelegate).isTrue()
    assertThat(firstLayers.all { it in graphicsContext.releasedLayers }).isTrue()

    effect.prepareDrawForTest(safeContext)
    val secondRuntime = effect.delegate as RuntimeShaderGlassDelegate
    assertThat(secondRuntime !== firstRuntime).isTrue()
    assertThat(secondRuntime.layers.hasSource).isTrue()
    assertThat(secondRuntime.layers.hasOptical).isTrue()
    effect.detach()
  }

  @Test
  fun prepareDraw_runtimeConstructionFailureReleasesAndUsesFallbackUntilReattach() {
    var creationAttempts = 0
    val failingFactory = GlassRuntimeEffectFactory { create ->
      creationAttempts++
      if (creationAttempts % 2 == 0) {
        throw RuntimeShaderRenderEffectException(IllegalArgumentException("broken runtime effect"))
      }
      create()
    }
    val effect = GlassRuntimeEffect().apply {
      optics = GlassOptics(
        refractionStrength = 0.5f,
        refractionDisplacement = 20.dp,
        depth = SizeValue.Fixed(0f),
        blurRadius = SizeValue.Fixed(0.dp),
      )
      specularIntensity = 0f
      runtimeEffectFactory = failingFactory
    }
    val context = RecordingVisualEffectContext(
      size = Size(100f, 100f),
      layerSize = Size(100f, 100f),
    )
    val runtimeDelegate = RuntimeShaderGlassDelegate(effect, failingFactory)
    effect.delegate = runtimeDelegate
    runtimeDelegate.layers.populate(context.graphicsContext)
    val partialLayers = runtimeDelegate.layers.allLayers()
    runtimeDelegate.setGraphicsContextForTest(context.graphicsContext)

    effect.attach(context)
    effect.prepareDrawForTest(context)

    assertThat(effect.delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(context.graphicsContext.releasedLayers).containsExactly(*partialLayers.toTypedArray())
    assertThat(runtimeDelegate.layers.isEmpty).isTrue()
    assertThat(runtimeDelegate.opticalShader).isNull()
    assertThat(runtimeDelegate.refractionDetailShader).isNull()
    assertThat(creationAttempts).isEqualTo(2)
    assertThat(context.invalidateDrawCalls).isEqualTo(1)

    effect.prepareDrawForTest(context)

    assertThat(effect.delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(creationAttempts).isEqualTo(2)

    effect.detach()
    effect.attach(context)
    effect.prepareDrawForTest(context)

    assertThat(effect.delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(creationAttempts).isEqualTo(4)
    effect.detach()
  }

  @Test
  fun prepareDraw_unrelatedRuntimeDelegateExceptionPropagates() {
    val unrelatedFailure = IllegalStateException("unrelated")
    val failingFactory = GlassRuntimeEffectFactory {
      throw unrelatedFailure
    }
    val effect = GlassRuntimeEffect()
    val context = RecordingVisualEffectContext(
      size = Size(100f, 100f),
      layerSize = Size(100f, 100f),
    )
    effect.delegate = RuntimeShaderGlassDelegate(effect, failingFactory)
    effect.attach(context)

    assertFailure {
      effect.prepareDrawForTest(context)
    }.isInstanceOf<IllegalStateException>()
    assertThat(effect.delegate).isInstanceOf<RuntimeShaderGlassDelegate>()
    effect.detach()
  }

  @Test
  fun prepareDraw_impossibleMaximumRefractionGeometryCreatesNoRuntimeLayers() {
    val effect = GlassRuntimeEffect().apply {
      optics = GlassOptics(
        refractionStrength = 1f,
        refractionDisplacement = 16_384.dp,
        blurRadius = SizeValue.Fixed(0.dp),
      )
      edgeSoftness = 0.dp
      shape = RoundedCornerShape(0.dp)
    }
    val graphicsContext = TestGraphicsContext()
    val context = RecordingVisualEffectContext(
      size = Size(100f, 100f),
      layerSize = Size(49_252f, 49_252f),
      graphicsContext = graphicsContext,
    )

    effect.attach(context)
    effect.prepareDrawForTest(context)

    assertThat(effect.delegate is FallbackGlassDelegate).isTrue()
    assertThat(effect.preparedRender).isNull()
    assertThat(graphicsContext.events.filterIsInstance<LayerEvent.Create>()).isEqualTo(emptyList())
    effect.detach()
  }

  @Test
  fun releaseBlurred_releasesAtMostThreeBlurLayersAndSize() {
    val layers = GlassLayers()
    val graphicsContext = TestGraphicsContext()
    layers.populate(graphicsContext)
    val blurLayers = listOfNotNull(
      layers.blurPrefiltered,
      layers.blurHorizontal,
      layers.blurred,
    )
    layers.blurWorkingSize = IntSize(540, 960)

    layers.releaseBlurred(graphicsContext)

    assertThat(graphicsContext.releasedLayers).containsExactly(*blurLayers.toTypedArray())
    assertThat(blurLayers.size).isEqualTo(3)
    assertThat(layers.blurWorkingSize).isEqualTo(null)
    assertThat(layers.hasBlurHorizontal).isEqualTo(false)
    assertThat(layers.hasBlurPrefiltered).isEqualTo(false)
    assertThat(layers.hasBlurred).isEqualTo(false)
    assertThat(layers.hasSource).isTrue()
    assertThat(layers.hasRefractionDetail).isTrue()
  }

  @Test
  fun releaseBlurPrefiltered_keepsSeparableWorkingLayers() {
    val layers = GlassLayers()
    val graphicsContext = TestGraphicsContext()
    layers.populate(graphicsContext)
    val prefiltered = checkNotNull(layers.blurPrefiltered)

    layers.releaseBlurPrefiltered(graphicsContext)

    assertThat(graphicsContext.releasedLayers).containsExactly(prefiltered)
    assertThat(layers.hasBlurPrefiltered).isEqualTo(false)
    assertThat(layers.hasBlurHorizontal).isTrue()
    assertThat(layers.hasBlurred).isTrue()
    assertThat(layers.hasRefractionDetail).isTrue()
  }

  @Test
  fun updateBlurWorkingSize_recreatesBlurGraphWhenScaleSelectionChanges() {
    val layers = GlassLayers()
    val graphicsContext = TestGraphicsContext()
    layers.populate(graphicsContext)
    layers.blurWorkingSize = IntSize(540, 960)
    val oldBlurLayers = listOfNotNull(
      layers.blurPrefiltered,
      layers.blurHorizontal,
      layers.blurred,
    )

    layers.updateBlurWorkingSize(IntSize(1080, 1920), graphicsContext)

    assertThat(graphicsContext.releasedLayers).containsExactly(*oldBlurLayers.toTypedArray())
    assertThat(layers.blurWorkingSize).isEqualTo(IntSize(1080, 1920))
    assertThat(layers.hasBlurPrefiltered).isEqualTo(false)
    assertThat(layers.hasBlurHorizontal).isEqualTo(false)
    assertThat(layers.hasBlurred).isEqualTo(false)
    assertThat(layers.hasRefractionDetail).isTrue()
  }

  @Test
  fun releaseRefractionDetail_releasesDetailCompositeGraph() {
    val layers = GlassLayers()
    val graphicsContext = TestGraphicsContext()
    layers.populate(graphicsContext)
    val detail = checkNotNull(layers.refractionDetail)
    val coverage = checkNotNull(layers.refractionDetailCoverage)
    val composite = checkNotNull(layers.refractionComposite)

    layers.releaseRefractionDetail(graphicsContext)

    assertThat(graphicsContext.releasedLayers).containsExactly(detail, coverage, composite)
    assertThat(layers.hasRefractionDetail).isFalse()
    assertThat(layers.hasRefractionDetailCoverage).isFalse()
    assertThat(layers.hasRefractionComposite).isFalse()
    assertThat(layers.hasOptical).isTrue()
  }

  @Test
  fun onTrimMemory_backgroundKeepsRetainedOutputAvailability() {
    val delegate = RuntimeShaderGlassDelegate(GlassRuntimeEffect())
    val context = RecordingVisualEffectContext()
    delegate.layers.populate(context.graphicsContext)
    delegate.seedSuccessfulCacheMetadata()
    delegate.seedRetainedOutputAvailable()
    val retainedLayers = delegate.layers.allLayers()

    assertThat(retainedLayers.size).isEqualTo(16)
    assertThat(delegate.canDrawRetainedOutput()).isTrue()

    delegate.onTrimMemory(context, TrimMemoryLevel.BACKGROUND)

    assertThat(delegate.layers.hasSource).isTrue()
    assertThat(delegate.layers.hasBlurPrefiltered).isTrue()
    assertThat(delegate.layers.hasBlurHorizontal).isTrue()
    assertThat(delegate.layers.hasBlurred).isTrue()
    assertThat(delegate.layers.hasDepthMixed).isTrue()
    assertThat(delegate.layers.hasOptical).isTrue()
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.layers.hasRefractionDetailCoverage).isTrue()
    assertThat(delegate.layers.hasRefractionComposite).isTrue()
    assertThat(delegate.layers.hasInteractionOptical).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetail).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetailCoverage).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionComposite).isTrue()
    assertThat(delegate.layers.hasInteractionLighting).isTrue()
    assertThat(delegate.layers.hasRim).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(context.invalidateDrawCalls).isEqualTo(0)
  }

  @Test
  fun onTrimMemory_uiHiddenReleasesAllLayersAndInvalidatesDraw() {
    val delegate = RuntimeShaderGlassDelegate(GlassRuntimeEffect())
    val context = RecordingVisualEffectContext(size = Size(100f, 100f), layerSize = Size(100f, 100f))
    delegate.layers.populate(context.graphicsContext)
    val retainedLayers = delegate.layers.allLayers()
    delegate.setGraphicsContextForTest(context.graphicsContext)

    assertThat(retainedLayers.size).isEqualTo(16)

    delegate.onTrimMemory(context, TrimMemoryLevel.UI_HIDDEN)

    assertThat(context.graphicsContext.releasedLayers).containsExactly(*retainedLayers.toTypedArray())
    assertThat(delegate.layers.isEmpty).isTrue()
    assertThat(context.invalidateDrawCalls).isEqualTo(1)
  }

  @Test
  fun onTrimMemory_moderateClearsRetainedOutputAvailabilityAndInvalidatesDraw() {
    val delegate = RuntimeShaderGlassDelegate(GlassRuntimeEffect())
    val context = RecordingVisualEffectContext()
    delegate.layers.populate(context.graphicsContext)
    delegate.seedSuccessfulCacheMetadata()
    val retainedLayers = delegate.layers.allLayers()

    assertThat(delegate.layers.hasSource).isTrue()
    assertThat(delegate.layers.hasBlurPrefiltered).isTrue()
    assertThat(delegate.layers.hasBlurHorizontal).isTrue()
    assertThat(delegate.layers.hasBlurred).isTrue()
    assertThat(delegate.layers.hasDepthMixed).isTrue()
    assertThat(delegate.layers.hasOptical).isTrue()
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.layers.hasInteractionOptical).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetail).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetailCoverage).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionComposite).isTrue()
    assertThat(delegate.layers.hasInteractionLighting).isTrue()
    assertThat(delegate.layers.hasRim).isTrue()
    assertThat(retainedLayers.size).isEqualTo(16)

    delegate.onTrimMemory(context, TrimMemoryLevel.MODERATE)

    assertThat(context.graphicsContext.releasedLayers).containsExactly(*retainedLayers.toTypedArray())
    assertThat(delegate.layers.isEmpty).isTrue()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNull()
    assertThat(delegate.lastSuccessfulStageInputs).isNull()
    assertThat(context.invalidateDrawCalls).isEqualTo(1)
  }

  @Test
  fun onTrimMemory_completeClearsRetainedOutputAvailabilityAndInvalidatesDraw() {
    val delegate = RuntimeShaderGlassDelegate(GlassRuntimeEffect())
    val context = RecordingVisualEffectContext()
    delegate.layers.populate(context.graphicsContext)
    delegate.seedSuccessfulCacheMetadata()
    delegate.seedRetainedOutputAvailable()
    val retainedLayers = delegate.layers.allLayers()

    assertThat(retainedLayers.size).isEqualTo(16)
    assertThat(delegate.canDrawRetainedOutput()).isTrue()

    delegate.onTrimMemory(context, TrimMemoryLevel.COMPLETE)

    assertThat(context.graphicsContext.releasedLayers).containsExactly(*retainedLayers.toTypedArray())
    assertThat(delegate.layers.isEmpty).isTrue()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNull()
    assertThat(delegate.lastSuccessfulStageInputs).isNull()
    assertThat(delegate.canDrawRetainedOutput()).isFalse()
    assertThat(context.invalidateDrawCalls).isEqualTo(1)
  }

  @Test
  fun detach_clearsSuccessfulCacheMetadata() {
    val delegate = RuntimeShaderGlassDelegate(GlassRuntimeEffect())
    val context = RecordingVisualEffectContext()
    delegate.layers.populate(context.graphicsContext)
    val retainedLayers = delegate.layers.allLayers()
    delegate.setGraphicsContextForTest(context.graphicsContext)
    delegate.seedSuccessfulCacheMetadata()

    delegate.detach()

    assertThat(context.graphicsContext.releasedLayers).containsExactly(*retainedLayers.toTypedArray())
    assertThat(delegate.layers.isEmpty).isTrue()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNull()
    assertThat(delegate.lastSuccessfulStageInputs).isNull()
  }

  @Test
  fun clearRetainedOutput_releasesAllPrivateLayersIncludingDetail() {
    val delegate = RuntimeShaderGlassDelegate(GlassRuntimeEffect())
    val context = RecordingVisualEffectContext()
    delegate.layers.populate(context.graphicsContext)
    val retainedLayers = delegate.layers.allLayers()
    delegate.setGraphicsContextForTest(context.graphicsContext)
    delegate.seedSuccessfulCacheMetadata()
    delegate.seedRetainedOutputAvailable()

    delegate.clearRetainedOutput()

    assertThat(context.graphicsContext.releasedLayers).containsExactly(*retainedLayers.toTypedArray())
    assertThat(delegate.layers.isEmpty).isTrue()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNull()
    assertThat(delegate.lastSuccessfulStageInputs).isNull()
    assertThat(delegate.canDrawRetainedOutput()).isFalse()
  }

  @Test
  fun canDrawRetainedOutput_activeDetailRequiresAvailableDetailLayer() {
    val delegate = RuntimeShaderGlassDelegate(GlassRuntimeEffect())
    val context = RecordingVisualEffectContext()
    delegate.layers.populate(context.graphicsContext)
    delegate.seedSuccessfulCacheMetadata(detail = Any())
    delegate.seedRetainedOutputAvailable()

    delegate.layers.releaseRefractionDetail(context.graphicsContext)

    assertThat(delegate.canDrawRetainedOutput()).isFalse()
  }

  @Test
  fun canDrawRetainedOutput_inactiveDetailDoesNotRequireDetailLayer() {
    val delegate = RuntimeShaderGlassDelegate(GlassRuntimeEffect())
    val context = RecordingVisualEffectContext()
    delegate.layers.populate(context.graphicsContext)
    delegate.seedSuccessfulCacheMetadata(detail = null)
    delegate.seedRetainedOutputAvailable()

    delegate.layers.releaseRefractionDetail(context.graphicsContext)

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun prepareRefractionDetail_activeAllocatesLayerBeforeDraw() {
    val layers = GlassLayers()
    val graphicsContext = TestGraphicsContext()

    layers.prepareRefractionDetail(required = true, graphicsContext)

    assertThat(layers.hasRefractionDetail).isTrue()
  }

  @Test
  fun prepareRefractionDetail_inactiveReleasesLayer() {
    val layers = GlassLayers()
    val graphicsContext = TestGraphicsContext()
    layers.refractionDetail = graphicsContext.createGraphicsLayer()

    layers.prepareRefractionDetail(required = false, graphicsContext)

    assertThat(layers.hasRefractionDetail).isFalse()
  }

  @Test
  fun prepareDraw_invalidSizeReleasesResourcesBeforeGeometryCalibration() {
    listOf(
      RecordingVisualEffectContext(size = Size(0f, 100f), layerSize = Size(100f, 100f)),
      RecordingVisualEffectContext(size = Size(100f, 0f), layerSize = Size(100f, 100f)),
      RecordingVisualEffectContext(
        size = Size(100f, 100f),
        layerSize = Size(Float.POSITIVE_INFINITY, 100f),
        failIfBuildRenderParamsReached = true,
      ),
      RecordingVisualEffectContext(
        size = Size(100f, 100f),
        layerSize = Size(100f, Float.POSITIVE_INFINITY),
        failIfBuildRenderParamsReached = true,
      ),
    ).forEach { context ->
      val effect = GlassRuntimeEffect()
      val delegate = RuntimeShaderGlassDelegate(effect)
      val retainedLayers = delegate.prepareDrawWithRetainedLayers(context, effect)

      assertThat(retainedLayers.size).isEqualTo(16)
      assertThat(context.graphicsContext.releasedLayers)
        .containsExactly(*retainedLayers.toTypedArray())
      assertThat(delegate.layers.isEmpty).isTrue()
    }
  }

  @Test
  fun onTrimMemory_uiHiddenRecreatesSafeGraphWithFreshSourceAndOpticalLayers() {
    val effect = GlassRuntimeEffect()
    val delegate = RuntimeShaderGlassDelegate(effect)
    val context = RecordingVisualEffectContext(size = Size(100f, 100f), layerSize = Size(100f, 100f))

    delegate.prepareDrawForTest(context, effect)
    val firstLayers = delegate.layers.allLayers()
    val firstSource = checkNotNull(delegate.layers.source)
    val firstOptical = checkNotNull(delegate.layers.optical)

    delegate.onTrimMemory(context, TrimMemoryLevel.UI_HIDDEN)

    delegate.prepareDrawForTest(context, effect)
    val secondSource = checkNotNull(delegate.layers.source)
    val secondOptical = checkNotNull(delegate.layers.optical)

    assertThat(firstLayers).containsExactly(*context.graphicsContext.releasedLayers.toTypedArray())
    assertThat(secondSource.isReleased).isFalse()
    assertThat(secondOptical.isReleased).isFalse()
    assertThat(secondSource === firstSource).isFalse()
    assertThat(secondOptical === firstOptical).isFalse()
  }
}

@OptIn(InternalComposeUiApi::class)
private fun GlassLayers.populate(graphicsContext: GraphicsContext) {
  groupAlpha.prepare(required = true, graphicsContext)
  source = graphicsContext.createGraphicsLayer()
  blurPrefiltered = graphicsContext.createGraphicsLayer()
  blurHorizontal = graphicsContext.createGraphicsLayer()
  blurred = graphicsContext.createGraphicsLayer()
  depthMixed = graphicsContext.createGraphicsLayer()
  optical = graphicsContext.createGraphicsLayer()
  refractionDetail = graphicsContext.createGraphicsLayer()
  refractionDetailCoverage = graphicsContext.createGraphicsLayer()
  refractionComposite = graphicsContext.createGraphicsLayer()
  interactionOptical = graphicsContext.createGraphicsLayer()
  interactionRefractionDetail = graphicsContext.createGraphicsLayer()
  interactionRefractionDetailCoverage = graphicsContext.createGraphicsLayer()
  interactionRefractionComposite = graphicsContext.createGraphicsLayer()
  interactionLighting = graphicsContext.createGraphicsLayer()
  rim = graphicsContext.createGraphicsLayer()
}

private fun GlassLayers.allLayers(): List<GraphicsLayer> = listOfNotNull(
  groupAlpha.layer,
  source,
  blurPrefiltered,
  blurHorizontal,
  blurred,
  depthMixed,
  optical,
  refractionDetail,
  refractionDetailCoverage,
  refractionComposite,
  interactionOptical,
  interactionRefractionDetail,
  interactionRefractionDetailCoverage,
  interactionRefractionComposite,
  interactionLighting,
  rim,
)

private fun RuntimeShaderGlassDelegate.prepareDrawWithRetainedLayers(
  context: RecordingVisualEffectContext,
  effect: GlassRuntimeEffect,
): List<GraphicsLayer> {
  layers.populate(context.graphicsContext)
  val retainedLayers = layers.allLayers()
  setGraphicsContextForTest(context.graphicsContext)

  effect.prepareRenderBudget(context, runtimeShaderSupported = true)
  with(CanvasDrawScope()) {
    with(this@prepareDrawWithRetainedLayers) { prepareDraw(context) }
  }
  return retainedLayers
}

private fun RuntimeShaderGlassDelegate.prepareDrawForTest(
  context: RecordingVisualEffectContext,
  effect: GlassRuntimeEffect,
) {
  effect.prepareRenderBudget(context, runtimeShaderSupported = true)
  with(CanvasDrawScope()) {
    with(this@prepareDrawForTest) { prepareDraw(context) }
  }
}

private fun GlassRuntimeEffect.prepareDrawForTest(context: RecordingVisualEffectContext) {
  val configuration = configuration()
  with(this) {
    with(context) {
      prepareDraw(configuration)
    }
  }
}

private fun GlassRuntimeEffect.update(context: RecordingVisualEffectContext) {
  update(
    scope = context,
    style = configuration(),
    sampling = HazeSampling.Default,
  )
}

private fun GlassRuntimeEffect.configuration(): GlassNodeConfiguration =
  GlassNodeConfiguration(
    style = style,
    interactionSource = interactionSource,
    interactionTransformTarget = interactionTransformTarget,
    interactionTransformPivot = interactionTransformPivot,
    interactionReducedMotionPolicy = interactionReducedMotionPolicy,
  )

private fun RuntimeShaderGlassDelegate.setGraphicsContextForTest(
  graphicsContext: GraphicsContext,
) {
  javaClass.getDeclaredField("graphicsContext").apply {
    isAccessible = true
    set(this@setGraphicsContextForTest, graphicsContext)
  }
}

private fun RuntimeShaderGlassDelegate.preparedParamsForTest(): GlassRenderParams? =
  javaClass.getDeclaredField("preparedParams").run {
    isAccessible = true
    get(this@preparedParamsForTest) as GlassRenderParams?
  }

private fun RuntimeShaderGlassDelegate.seedSuccessfulCacheMetadata(
  detail: Any? = Any(),
) {
  javaClass.getDeclaredField("lastSuccessfulSourceSnapshot").apply {
    isAccessible = true
    set(
      this@seedSuccessfulCacheMetadata,
      GlassRuntimeSourceSnapshot(
        captureScale = 1f,
        layerSize = Size(1f, 1f),
        layerOffset = Offset.Zero,
        inputSnapshot = TestInputSnapshot,
      ),
    )
  }
  javaClass.getDeclaredField("lastSuccessfulStageInputs").apply {
    isAccessible = true
    set(
      this@seedSuccessfulCacheMetadata,
      GlassStageInputs(
        blur = Any(),
        depth = .5f,
        optical = Any(),
        detail = detail,
        rim = Any(),
      ),
    )
  }
}

private fun RuntimeShaderGlassDelegate.seedRetainedOutputAvailable() {
  javaClass.getDeclaredField("retainedOutputAvailable").apply {
    isAccessible = true
    set(this@seedRetainedOutputAvailable, true)
  }
}

@OptIn(InternalComposeUiApi::class, InternalHazeApi::class)
private class RecordingVisualEffectContext(
  override val size: Size = Size.Zero,
  override val layerSize: Size = Size.Zero,
  private val failIfBuildRenderParamsReached: Boolean = false,
  val graphicsContext: TestGraphicsContext = TestGraphicsContext(),
) : HazeEffectRuntimeDrawScope,
  HazeEffectLifecycleScope,
  androidx.compose.ui.graphics.drawscope.DrawScope by CanvasDrawScope() {
  var invalidateDrawCalls = 0
    private set

  override val modifierSize: Size get() = size
  override val modifierBounds: Rect get() = Rect(Offset.Zero, size)
  override val sampling: HazeSampling = HazeSampling.Default
  override val layerOffset: Offset = Offset.Zero
  override val hasDrawableInput: Boolean = true
  override val inputSnapshot: HazeEffectInputSnapshot = TestInputSnapshot
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun requirePlatformContext(): PlatformContext = error("Unused in trim-memory tests")
  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T {
    if (failIfBuildRenderParamsReached) {
      error("buildRenderParams reached for invalid raw sample size")
    }
    return LayoutDirection.Ltr as T
  }
  override fun requireGraphicsContext(): GraphicsContext = graphicsContext

  override fun drawInput() = Unit

  override fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInput() = Unit

  override fun invalidateDraw() {
    invalidateDrawCalls++
  }

  override fun invalidateLayerBounds() = Unit
}

@OptIn(InternalHazeApi::class)
private object TestInputSnapshot : HazeEffectInputSnapshot

@OptIn(InternalComposeUiApi::class)
private class TestGraphicsContext : GraphicsContext {
  val releasedLayers = mutableListOf<GraphicsLayer>()
  val events = mutableListOf<LayerEvent>()

  override fun createGraphicsLayer(): GraphicsLayer =
    (unsafe.allocateInstance(GraphicsLayer::class.java) as GraphicsLayer).also {
      events += LayerEvent.Create(it)
    }

  override fun releaseGraphicsLayer(layer: GraphicsLayer) {
    releasedLayers += layer
    events += LayerEvent.Release(layer)
  }
}

private sealed interface LayerEvent {
  val layer: GraphicsLayer

  data class Create(override val layer: GraphicsLayer) : LayerEvent

  data class Release(override val layer: GraphicsLayer) : LayerEvent
}

private val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
  isAccessible = true
  get(null) as Unsafe
}
