// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.VisualEffectContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
class GlassVisualEffectLifecycleTest {

  @Test
  fun prepareBudget_fractionalAlphaIncludesMaterialSizedGroupComposite() {
    val effect = GlassVisualEffect().apply { alpha = 0.5f }

    val decision = effect.prepareRenderBudget(
      context = TrackingVisualEffectContext(effectSize = Size(100f, 80f), layerSize = Size(120f, 100f)),
      runtimeShaderSupported = false,
    ) as GlassRenderBudgetDecision.Runtime

    assertThat(decision.plan.layers.last()).isEqualTo(
      GlassRetainedLayer(GlassRetainedLayerKind.GroupComposite, IntSize(100, 80)),
    )
  }

  @Test
  fun prepareBudget_materialSizeChangeUpdatesGroupComposite() {
    val effect = GlassVisualEffect().apply { alpha = 0.5f }
    val layerSize = Size(160f, 140f)

    effect.prepareRenderBudget(
      context = TrackingVisualEffectContext(
        effectSize = Size(100f, 80f),
        layerSize = layerSize,
      ),
      runtimeShaderSupported = false,
    )
    val decision = effect.prepareRenderBudget(
      context = TrackingVisualEffectContext(
        effectSize = Size(120f, 90f),
        layerSize = layerSize,
      ),
      runtimeShaderSupported = false,
    ) as GlassRenderBudgetDecision.Runtime

    assertThat(decision.plan.layers.last()).isEqualTo(
      GlassRetainedLayer(GlassRetainedLayerKind.GroupComposite, IntSize(120, 90)),
    )
  }

  @Test
  fun prepareBudget_interactionGroupCompositeContributesToScaleSelection() {
    val effect = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(
        refractionStrength = 0f,
        refractionScale = 0f,
        blurRadius = 0.dp,
      )
      specularIntensity = 0f
      pressed { refractionMultiplier(1.1f) }
    }
    val context = TrackingVisualEffectContext(
      effectSize = Size(2_000f, 2_000f),
      layerSize = Size(2_600f, 2_600f),
    )
    effect.attach(context)
    effect.update(context)
    effect.setPressedForTest(Offset(1_000f, 1_000f))

    val decision = effect.prepareRenderBudget(
      context = context,
      runtimeShaderSupported = true,
    ) as GlassRenderBudgetDecision.Runtime

    assertThat(decision.scaleFactor < 1f).isEqualTo(true)
    assertThat(decision.plan.layers.last()).isEqualTo(
      GlassRetainedLayer(GlassRetainedLayerKind.GroupComposite, IntSize(2_000, 2_000)),
    )
    assertThat(decision.plan.fitsGlassRenderBudget()).isEqualTo(true)
    effect.detach(context)
  }

  @Test
  fun prepareBudget_safeGraphPreservesRequestedScale() {
    val decision = GlassVisualEffect().resolveGlassRenderBudget(
      TrackingVisualEffectContext(effectSize = Size(100f, 100f), layerSize = Size(120f, 120f)),
    )

    assertThat((decision as GlassRenderBudgetDecision.Runtime).scaleFactor).isEqualTo(1f)
  }

  @Test
  fun prepareBudget_maxRefractionUsesFallbackBeforeRuntimePreparation() {
    val effect = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(
        refractionStrength = 1f,
        refractionScale = 16_384f,
        blurRadius = 0.dp,
      )
    }
    val decision = effect.resolveGlassRenderBudget(
      TrackingVisualEffectContext(
        effectSize = Size(100f, 100f),
        layerSize = Size(49_252f, 49_252f),
      ),
    )

    assertThat(decision).isInstanceOf(GlassRenderBudgetDecision.Fallback::class)
  }

  @Test
  fun prepareBudget_retainsSelectedRenderBundleWithoutInactiveBlurKey() {
    val effect = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(depth = 0f, blurRadius = 38.5.dp)
    }

    val decision = effect.prepareRenderBudget(
      TrackingVisualEffectContext(),
      runtimeShaderSupported = true,
    )
    val prepared = checkNotNull(effect.preparedRender)

    assertThat(prepared.plan).isEqualTo((decision as GlassRenderBudgetDecision.Runtime).plan)
    assertThat(prepared.blurKey).isNull()
  }

  @Test
  fun prepareBudget_runtimeUnavailableSkipsExactRenderBundle() {
    val effect = GlassVisualEffect()

    val decision = effect.prepareRenderBudget(
      TrackingVisualEffectContext(),
      runtimeShaderSupported = false,
    )

    assertThat(decision).isInstanceOf(GlassRenderBudgetDecision.Runtime::class)
    assertThat(effect.preparedRender).isNull()
  }

  @Test
  fun prepareBudget_scaleDependentDetailUsesExactSelectedPlan() {
    val effect = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(
        refractionStrength = 1f,
        refractionScale = 0.18f,
        depth = 0f,
        blurRadius = 0.dp,
      )
    }

    val decision = effect.prepareRenderBudget(
      TrackingVisualEffectContext(
        effectSize = Size(100f, 100f),
        layerSize = Size(8_800f, 8_800f),
      ),
      runtimeShaderSupported = true,
    ) as GlassRenderBudgetDecision.Runtime
    val prepared = checkNotNull(effect.preparedRender)

    assertThat(decision.scaleFactor > 0.25f).isEqualTo(true)
    assertThat(decision.plan).isEqualTo(prepared.plan)
    assertThat(prepared.plan.fitsGlassRenderBudget()).isEqualTo(true)
    assertThat(prepared.plan.layers.map { it.kind }).isEqualTo(
      listOf(
        GlassRetainedLayerKind.Source,
        GlassRetainedLayerKind.Optical,
        GlassRetainedLayerKind.Rim,
      ),
    )
  }

  @Test
  fun prepareBudget_runtimeToFallbackClearsPreparedRenderBundle() {
    val effect = GlassVisualEffect()

    assertThat(
      effect.prepareRenderBudget(
        TrackingVisualEffectContext(),
        runtimeShaderSupported = true,
      ),
    ).isInstanceOf(GlassRenderBudgetDecision.Runtime::class)
    assertThat(effect.preparedRender).isNotNull()

    assertThat(
      effect.prepareRenderBudget(
        TrackingVisualEffectContext(layerSize = Size(50_000f, 50_000f)),
        runtimeShaderSupported = true,
      ),
    ).isInstanceOf(GlassRenderBudgetDecision.Fallback::class)
    assertThat(effect.preparedRender).isNull()
  }

  @Test
  fun detach_clearsPreparedRenderBundle() {
    val effect = GlassVisualEffect()
    val context = TrackingVisualEffectContext()

    effect.prepareRenderBudget(context, runtimeShaderSupported = true)
    assertThat(effect.preparedRender).isNotNull()

    effect.detach(context)

    assertThat(effect.preparedRender).isNull()
  }

  @Test
  fun prepareBudget_unchangedBudgetInputsReuseDecisionAndSelectedPlan() {
    val effect = GlassVisualEffect()
    val context = TrackingVisualEffectContext()

    val first = effect.prepareRenderBudget(
      context,
      runtimeShaderSupported = true,
    ) as GlassRenderBudgetDecision.Runtime
    val firstPrepared = checkNotNull(effect.preparedRender)
    val second = effect.prepareRenderBudget(
      context,
      runtimeShaderSupported = true,
    ) as GlassRenderBudgetDecision.Runtime
    val secondPrepared = checkNotNull(effect.preparedRender)

    assertThat(second).isSameInstanceAs(first)
    assertThat(secondPrepared).isSameInstanceAs(firstPrepared)
    assertThat(secondPrepared.blurKey?.plan).isSameInstanceAs(firstPrepared.blurKey?.plan)
    assertThat(secondPrepared.plan).isSameInstanceAs(second.plan)
  }

  @Test
  fun prepareBudget_chromaticAberrationChangeInvalidatesBudgetDecision() {
    val effect = GlassVisualEffect()
    val context = TrackingVisualEffectContext()

    val first = effect.prepareRenderBudget(
      context,
      runtimeShaderSupported = true,
    )
    effect.chromaticAberrationStrength = 1f
    val second = effect.prepareRenderBudget(
      context,
      runtimeShaderSupported = true,
    )

    assertThat(second).isNotSameInstanceAs(first)
  }

  @Test
  fun stablePreparationAndClipping_doNotResolveCornerGeometryAgain() {
    val corner = CountingCornerSize(12f)
    val effect = GlassVisualEffect().apply {
      shape = RoundedCornerShape(corner)
    }
    val context = TrackingVisualEffectContext()

    effect.prepareRenderBudget(context, runtimeShaderSupported = true)
    val resolutionsAfterFirstPreparation = corner.resolutionCount

    effect.prepareRenderBudget(context, runtimeShaderSupported = true)

    assertThat(corner.resolutionCount).isEqualTo(resolutionsAfterFirstPreparation)

    effect.shouldClipToNodeBounds()
    val resolutionsAfterFirstClipDecision = corner.resolutionCount

    effect.shouldClipToNodeBounds()

    assertThat(corner.resolutionCount).isEqualTo(resolutionsAfterFirstClipDecision)
  }

  @Test
  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  fun prepareBudget_stateBackedCornerSizeInvalidatesResolvedStyle() = runTest {
    val cornerPx = mutableFloatStateOf(4f)
    val effect = GlassVisualEffect().apply {
      shape = RoundedCornerShape(StateBackedCornerSize { cornerPx.floatValue })
    }
    val context = TrackingVisualEffectContext(
      coroutineScope = CoroutineScope(coroutineContext),
    )
    effect.attach(context)

    effect.shouldClipToNodeBounds()
    effect.prepareRenderBudget(context, runtimeShaderSupported = true)
    assertThat(effect.preparedRender?.params?.cornerRadii?.topLeft).isEqualTo(4f)

    cornerPx.floatValue = 20f
    Snapshot.sendApplyNotifications()
    advanceUntilIdle()
    effect.prepareRenderBudget(context, runtimeShaderSupported = true)

    assertThat(effect.preparedRender?.params?.cornerRadii?.topLeft).isEqualTo(20f)
    assertThat(context.invalidateDrawCalls).isEqualTo(1)
    assertThat(context.invalidateLayerBoundsCalls).isEqualTo(1)
    effect.detach(context)
  }

  @Test
  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  fun stateBackedCornerInvalidation_doesNotCrossAttachments() = runTest {
    val cornerPx = mutableFloatStateOf(0f)
    val effect = GlassVisualEffect().apply {
      shape = RoundedCornerShape(StateBackedCornerSize { cornerPx.floatValue })
    }
    val firstContext = TrackingVisualEffectContext(
      coroutineScope = CoroutineScope(coroutineContext),
    )
    effect.attach(firstContext)
    effect.shouldClipToNodeBounds()

    cornerPx.floatValue = 20f
    Snapshot.sendApplyNotifications()
    effect.detach(firstContext)

    val secondContext = TrackingVisualEffectContext(
      coroutineScope = CoroutineScope(coroutineContext),
    )
    effect.attach(secondContext)
    effect.shouldClipToNodeBounds()
    advanceUntilIdle()

    assertThat(secondContext.invalidateDrawCalls).isEqualTo(0)
    assertThat(secondContext.invalidateLayerBoundsCalls).isEqualTo(0)
    effect.detach(secondContext)
  }

  @Test
  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  fun clipping_stateBackedCornerSizeInvalidatesDecisionBeforePreparation() = runTest {
    val cornerPx = mutableFloatStateOf(0f)
    val effect = GlassVisualEffect().apply {
      edgeSoftness = 0.dp
      shape = RoundedCornerShape(StateBackedCornerSize { cornerPx.floatValue })
    }
    val context = TrackingVisualEffectContext(
      coroutineScope = CoroutineScope(coroutineContext),
    )
    effect.attach(context)

    assertThat(effect.shouldClipToNodeBounds()).isEqualTo(false)

    cornerPx.floatValue = 20f
    Snapshot.sendApplyNotifications()
    advanceUntilIdle()

    assertThat(effect.shouldClipToNodeBounds()).isEqualTo(true)
    assertThat(context.invalidateDrawCalls).isEqualTo(1)
    assertThat(context.invalidateLayerBoundsCalls).isEqualTo(1)
    effect.detach(context)
  }

  @Test
  fun prepareBudget_alphaOnlyChangeReusesUnderlyingPreparedData() {
    val effect = GlassVisualEffect()
    val context = TrackingVisualEffectContext()

    effect.prepareRenderBudget(context, runtimeShaderSupported = true)
    val first = checkNotNull(effect.preparedRender)

    effect.alpha = 0.5f
    effect.prepareRenderBudget(context, runtimeShaderSupported = true)
    val second = checkNotNull(effect.preparedRender)

    assertThat(second).isNotSameInstanceAs(first)
    assertThat(second.params).isSameInstanceAs(first.params)
    assertThat(second.blurKey).isSameInstanceAs(first.blurKey)
    assertThat(second.opticalKey).isSameInstanceAs(first.opticalKey)
    assertThat(second.refractionDetailKey).isSameInstanceAs(first.refractionDetailKey)
    assertThat(second.rimKey).isSameInstanceAs(first.rimKey)
    assertThat(first.plan.layers.any { it.kind == GlassRetainedLayerKind.GroupComposite })
      .isEqualTo(false)
    assertThat(second.plan.layers.any { it.kind == GlassRetainedLayerKind.GroupComposite })
      .isEqualTo(true)
    assertThat(second.plan).isNotSameInstanceAs(first.plan)
  }

  @Test
  fun prepareBudget_interactionRadiusChangeRebuildsPlanButReusesBaseEffectKeys() {
    val effect = GlassVisualEffect().apply {
      pressed {
        lightingIntensity(1f)
        refractionMultiplier(1.1f)
      }
      interactionLightRadiusFraction = 0.2f
    }
    val context = TrackingVisualEffectContext(effectSize = Size(1000f, 600f))

    effect.prepareRenderBudget(context, runtimeShaderSupported = true)
    val first = checkNotNull(effect.preparedRender)

    effect.interactionLightRadiusFraction = 0.8f
    effect.prepareRenderBudget(context, runtimeShaderSupported = true)
    val second = checkNotNull(effect.preparedRender)

    assertThat(second.plan).isNotSameInstanceAs(first.plan)
    assertThat(second.params).isSameInstanceAs(first.params)
    assertThat(second.blurKey).isSameInstanceAs(first.blurKey)
    assertThat(second.opticalKey).isSameInstanceAs(first.opticalKey)
    assertThat(second.refractionDetailKey).isSameInstanceAs(first.refractionDetailKey)
    assertThat(second.rimKey).isSameInstanceAs(first.rimKey)
  }

  @Test
  fun prepareBudget_zeroInteractionRadiusMatchesNoInteractionResponses() {
    val configured = GlassVisualEffect().apply {
      pressed {
        lightingIntensity(1f)
        refractionMultiplier(1.1f)
      }
      interactionLightRadiusFraction = 0f
    }
    val context = TrackingVisualEffectContext(effectSize = Size(1000f, 600f))

    configured.prepareRenderBudget(context, runtimeShaderSupported = true)
    val configuredPlan = checkNotNull(configured.preparedRender).plan

    val baseline = GlassVisualEffect()
    baseline.prepareRenderBudget(context, runtimeShaderSupported = true)

    assertThat(configuredPlan).isEqualTo(checkNotNull(baseline.preparedRender).plan)
  }

  @Test
  fun prepareBudget_lightingOnlyChangeReusesUnchangedPreparedData() {
    val effect = GlassVisualEffect()
    val context = TrackingVisualEffectContext()

    effect.prepareRenderBudget(context, runtimeShaderSupported = true)
    val first = checkNotNull(effect.preparedRender)

    effect.lightPosition = Offset(20f, 30f)
    effect.prepareRenderBudget(context, runtimeShaderSupported = true)
    val second = checkNotNull(effect.preparedRender)

    assertThat(second.params).isNotSameInstanceAs(first.params)
    assertThat(second.blurKey).isSameInstanceAs(first.blurKey)
    assertThat(second.opticalKey).isSameInstanceAs(first.opticalKey)
    assertThat(second.refractionDetailKey).isSameInstanceAs(first.refractionDetailKey)
    assertThat(second.rimKey).isNotSameInstanceAs(first.rimKey)
    assertThat(second.plan).isSameInstanceAs(first.plan)
  }

  @Test
  fun prepareBudget_interactionOnlyChangeReusesBasePreparedData() {
    val effect = GlassVisualEffect()
    val context = TrackingVisualEffectContext()

    effect.prepareRenderBudget(context, runtimeShaderSupported = true)
    val first = checkNotNull(effect.preparedRender)

    effect.interactionLightRadiusFraction = 0.8f
    effect.prepareRenderBudget(context, runtimeShaderSupported = true)
    val second = checkNotNull(effect.preparedRender)

    assertThat(second.interactionUniforms).isNotSameInstanceAs(first.interactionUniforms)
    assertThat(second.params).isSameInstanceAs(first.params)
    assertThat(second.blurKey).isSameInstanceAs(first.blurKey)
    assertThat(second.opticalKey).isSameInstanceAs(first.opticalKey)
    assertThat(second.refractionDetailKey).isSameInstanceAs(first.refractionDetailKey)
    assertThat(second.rimKey).isSameInstanceAs(first.rimKey)
    assertThat(second.plan).isNotSameInstanceAs(first.plan)
  }

  @Test
  fun prepareBudget_configuredInteractionTopologyIsStableAcrossAnimatedValues() {
    val effect = GlassVisualEffect().apply {
      pressed {
        lightingIntensity(1f)
        refractionMultiplier(1.2f)
        whitePointDelta(0.04f)
      }
    }
    val context = TrackingVisualEffectContext()
    effect.attach(context)
    effect.update(context)

    val idle = effect.prepareRenderBudget(context, runtimeShaderSupported = true)
      as GlassRenderBudgetDecision.Runtime
    val idleKinds = checkNotNull(effect.preparedRender).plan.layers.map { it.kind }

    effect.setPressedForTest(Offset(50f, 50f))
    val pressed = effect.prepareRenderBudget(context, runtimeShaderSupported = true)
      as GlassRenderBudgetDecision.Runtime
    val pressedKinds = checkNotNull(effect.preparedRender).plan.layers.map { it.kind }

    assertThat(pressed.scaleFactor).isEqualTo(idle.scaleFactor)
    assertThat(pressedKinds).isEqualTo(idleKinds)
    assertThat(pressedKinds.any { it.name.startsWith("Interaction") }).isEqualTo(true)
    effect.detach(context)
  }

  @Test
  fun update_readsInjectedMotionScaleAndFullOverridesIt() {
    val effect = GlassVisualEffect().apply { pressed() }
    val context = TrackingVisualEffectContext(
      motionScale = 0f,
      effectSize = Size.Zero,
    )

    effect.attach(context)
    effect.update(context)
    val controller = checkNotNull(effect.interactionControllerForTest)

    assertThat(controller.configurationForTest.reducedMotion).isEqualTo(true)
    assertThat(controller.configurationForTest.forceFullMotion).isEqualTo(false)

    effect.interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
    effect.update(context)

    assertThat(controller.configurationForTest.reducedMotion).isEqualTo(false)
    assertThat(controller.configurationForTest.forceFullMotion).isEqualTo(true)
    effect.detach(context)
  }

  @Test
  fun attachAndUpdate_withoutInteractionsDoesNotAllocateController() {
    val effect = GlassVisualEffect()
    val context = TrackingVisualEffectContext()

    effect.attach(context)
    effect.update(context)

    assertThat(effect.interactionControllerForTest).isNull()
    effect.detach(context)
  }

  @Test
  fun detach_disposesInteractionController() {
    val effect = GlassVisualEffect().apply { pressed() }
    val context = TrackingVisualEffectContext()

    effect.attach(context)
    val controller = effect.interactionControllerForTest
    effect.detach(context)

    assertThat(controller).isNotNull()
    assertThat(controller?.isDisposedForTest).isEqualTo(true)
    assertThat(effect.interactionControllerForTest).isNull()
    assertThat(effect.attachedContextForTest).isNull()
  }

  @Test
  fun update_directShapeChangeInvalidatesLayerBounds() {
    val effect = GlassVisualEffect()
    val context = TrackingVisualEffectContext()

    effect.update(context)
    context.invalidateLayerBoundsCalls = 0

    effect.shape = RoundedCornerShape(24.dp)
    effect.update(context)

    assertThat(context.invalidateLayerBoundsCalls).isEqualTo(1)
  }

  @Test
  fun resettingConsumedDirtyFlags_doesNotNotifyObserver_butNextChangeDoes() {
    val effect = GlassVisualEffect().apply { resetDirtyTracker() }
    val context = TrackingVisualEffectContext()
    val observer = SnapshotStateObserver { command -> command() }
    var observerNotifications = 0
    val observationScope = Any()
    val onChanged: (Any) -> Unit = { observerNotifications++ }

    observer.start()
    try {
      observer.observeReads(observationScope, onChanged) {
        effect.update(context)
      }

      effect.alpha = 0.5f
      Snapshot.sendApplyNotifications()

      assertThat(observerNotifications).isEqualTo(1)

      observer.observeReads(observationScope, onChanged) {
        effect.update(context)
      }
      effect.resetDirtyTracker()
      Snapshot.sendApplyNotifications()

      assertThat(observerNotifications).isEqualTo(1)

      observer.observeReads(observationScope, onChanged) {
        effect.update(context)
      }
      effect.alpha = 0.6f
      Snapshot.sendApplyNotifications()

      assertThat(observerNotifications).isEqualTo(2)
    } finally {
      observer.stop()
    }
  }

  @Test
  fun markingAlreadyDirtyField_doesNotNotifyObserverAgain() {
    val effect = GlassVisualEffect().apply { resetDirtyTracker() }
    val context = TrackingVisualEffectContext()
    val observer = SnapshotStateObserver { command -> command() }
    var observerNotifications = 0
    val observationScope = Any()
    val onChanged: (Any) -> Unit = { observerNotifications++ }

    observer.start()
    try {
      observer.observeReads(observationScope, onChanged) {
        effect.update(context)
      }
      effect.alpha = 0.5f
      Snapshot.sendApplyNotifications()

      assertThat(observerNotifications).isEqualTo(1)

      observer.observeReads(observationScope, onChanged) {
        effect.update(context)
      }
      effect.alpha = 0.6f
      Snapshot.sendApplyNotifications()

      assertThat(observerNotifications).isEqualTo(1)
    } finally {
      observer.stop()
    }
  }

  @Test
  fun update_adaptiveToAbsoluteInvalidatesDrawAndLayerBounds() {
    val effect = GlassVisualEffect()
    val context = TrackingVisualEffectContext()

    effect.optics = GlassOptics.Absolute(refractionStrength = 0.4f)
    effect.update(context)

    assertThat(context.invalidateDrawCalls).isEqualTo(1)
    assertThat(context.invalidateLayerBoundsCalls).isEqualTo(1)
  }

  @Test
  fun update_replacingAbsoluteInvalidatesDrawAndLayerBounds() {
    val effect = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(refractionStrength = 0.4f)
      resetDirtyTracker()
    }
    val context = TrackingVisualEffectContext()

    effect.optics = (effect.optics as GlassOptics.Absolute).copy(refractionStrength = 0.8f)
    effect.update(context)

    assertThat(context.invalidateDrawCalls).isEqualTo(1)
    assertThat(context.invalidateLayerBoundsCalls).isEqualTo(1)
  }

  @Test
  fun update_interactionRenderingConfigurationChangesInvalidateDraw() {
    val changes = listOf<(GlassVisualEffect) -> Unit>(
      { it.interactionLightRadiusFraction = 1.2f },
      { it.interactionTransformTarget = GlassTransformTarget.MaterialAndContent },
      { it.interactionTransformPivot = GlassTransformPivot.Center },
    )

    changes.forEach { change ->
      val effect = GlassVisualEffect()
      val context = TrackingVisualEffectContext()

      change(effect)
      effect.update(context)

      assertThat(context.invalidateDrawCalls).isEqualTo(1)
    }
  }

  @Test
  fun update_clearingAbsoluteOverrideInvalidatesDrawAndLayerBounds() {
    val effect = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(refractionStrength = 0.4f)
      resetDirtyTracker()
    }
    val context = TrackingVisualEffectContext()

    effect.clearOpticsOverride()
    effect.update(context)

    assertThat(context.invalidateDrawCalls).isEqualTo(1)
    assertThat(context.invalidateLayerBoundsCalls).isEqualTo(1)
  }

  @Test
  fun calculateLayerBounds_usesMaximumConfiguredInteractionRefractionStrength() {
    val effect = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(refractionStrength = 0.6f)
      pressed { refractionMultiplier(2f) }
    }
    val rect = Rect(0f, 0f, 100f, 100f)
    val baseBounds = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(refractionStrength = 0.6f)
    }.calculateLayerBounds(rect, Density(1f))

    val interactionBounds = effect.calculateLayerBounds(rect, Density(1f))

    assertThat(-interactionBounds.left).isGreaterThan(-baseBounds.left)
  }

  @Test
  fun calculateLayerBounds_depthZeroDoesNotReserveBlurPadding() {
    val rect = Rect(0f, 0f, 100f, 100f)
    val effect = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(depth = 0f, blurRadius = 40.dp, refractionStrength = 0f)
      edgeSoftness = 0.dp
      specularIntensity = 0f
    }

    assertThat(effect.calculateLayerBounds(rect, Density(1f))).isEqualTo(rect)
  }

  @Test
  fun changingInteractionRefractionMaximum_invalidatesLayerBounds_butEquivalentDeclarationDoesNot() {
    val effect = GlassVisualEffect()
    val context = TrackingVisualEffectContext()
    effect.update(context)
    context.invalidateLayerBoundsCalls = 0

    effect.pressed { refractionMultiplier(2f) }
    effect.update(context)
    assertThat(context.invalidateLayerBoundsCalls).isEqualTo(1)
    effect.resetDirtyTracker()

    effect.pressed { refractionMultiplier(2f) }
    effect.update(context)
    assertThat(context.invalidateLayerBoundsCalls).isEqualTo(1)

    effect.clearPressed()
    effect.update(context)
    assertThat(context.invalidateLayerBoundsCalls).isEqualTo(2)
  }

  @Test
  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  fun controller_withoutFrameClock_snapsPositionAndFloatTargets_andInvalidatesDraw() = runTest {
    val context = TrackingVisualEffectContext(
      coroutineScope = CoroutineScope(coroutineContext),
    )
    val controller = GlassInteractionController(context)
    controller.updateConfiguration(
      GlassInteractionControllerConfiguration(
        slots = GlassInteractionSlots(
          pressed = GlassInteractionSlot(
            revision = 1,
            response = buildGlassInteractionResponse {
              animate(
                toSpec = androidx.compose.animation.core.tween(100),
                fromSpec = androidx.compose.animation.core.tween(100),
              ) {
                lightingIntensity(1f)
              }
            },
          ),
        ),
        positionAnimationSpec = androidx.compose.animation.core.tween(100),
        reducedMotion = false,
        forceFullMotion = false,
      ),
    )

    controller.updatePosition(Offset(24f, 36f))
    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    advanceUntilIdle()

    assertThat(controller.renderState.position).isEqualTo(Offset(24f, 36f))
    assertThat(controller.renderState.lightingIntensity).isEqualTo(1f)
    val stableRenderState = controller.renderState
    assertThat(controller.renderState).isSameInstanceAs(stableRenderState)

    context.invalidateDrawCalls = 0
    controller.updatePosition(Offset(48f, 72f))
    controller.updateConfiguration(
      GlassInteractionControllerConfiguration(
        slots = GlassInteractionSlots(
          pressed = GlassInteractionSlot(
            revision = 2,
            response = buildGlassInteractionResponse { lightingIntensity(0.4f) },
          ),
        ),
        positionAnimationSpec = androidx.compose.animation.core.tween(100),
        reducedMotion = false,
        forceFullMotion = false,
      ),
    )
    advanceUntilIdle()

    assertThat(controller.renderState.position).isEqualTo(Offset(48f, 72f))
    assertThat(controller.renderState.lightingIntensity).isEqualTo(0.4f)
    assertThat(context.invalidateDrawCalls).isGreaterThan(0)
  }
}

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
private class TrackingVisualEffectContext(
  motionScale: Float? = null,
  effectSize: Size = Size(100f, 100f),
  layerSize: Size = effectSize,
  coroutineScope: CoroutineScope? = null,
) : VisualEffectContext {
  override val position: Offset = Offset.Zero
  override val size: Size = effectSize
  override val layerSize: Size = layerSize
  override val layerOffset: Offset = Offset.Zero
  override val rootBounds: Rect = Rect(Offset.Zero, size)
  override val inputScale: HazeInputScale = HazeInputScale.None
  override val windowId: Any? = null
  override val areas: List<HazeArea> = emptyList()
  override val state: HazeState? = null
  override val coroutineScope: CoroutineScope = coroutineScope ?: CoroutineScope(
    motionScale?.let(::TestMotionDurationScale) ?: EmptyCoroutineContext,
  )

  var invalidateLayerBoundsCalls: Int = 0
  var invalidateDrawCalls: Int = 0

  override fun requirePlatformContext(): PlatformContext = error("Unused in lifecycle test")

  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = when (local) {
    LocalGlassStyle -> GlassDefaults.style
    LocalLayoutDirection -> LayoutDirection.Ltr
    else -> error("Unused composition local")
  } as T

  override fun requireGraphicsContext(): GraphicsContext = error("Unused in lifecycle test")

  override fun invalidateDraw() {
    invalidateDrawCalls++
  }

  override fun invalidateLayerBounds() {
    invalidateLayerBoundsCalls++
  }
}

private class TestMotionDurationScale(
  override val scaleFactor: Float,
) : MotionDurationScale

private class CountingCornerSize(
  private val value: Float,
) : CornerSize {
  var resolutionCount: Int = 0
    private set

  override fun toPx(shapeSize: Size, density: Density): Float {
    resolutionCount++
    return value
  }
}

private class StateBackedCornerSize(
  private val value: () -> Float,
) : CornerSize {
  override fun toPx(shapeSize: Size, density: Density): Float = value()
}
