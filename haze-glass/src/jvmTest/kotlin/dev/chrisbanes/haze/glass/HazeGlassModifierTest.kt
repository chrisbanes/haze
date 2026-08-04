// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectContentTransform
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectLayoutScope
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRendererDrawHooks
import dev.chrisbanes.haze.HazeEffectRendererInteraction
import dev.chrisbanes.haze.HazeEffectRendererLifecycle
import dev.chrisbanes.haze.HazeEffectRendererRetainedOutput
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class, ExperimentalTestApi::class)
class HazeGlassModifierTest : ContextTest() {

  @Test
  fun structuralPolicies_reachTypedGlassRuntime() = runComposeUiTest {
    val hazeState = HazeState()
    val inputs = listOf(
      HazeInput.Content,
      HazeInput.Sources(
        state = hazeState,
        selection = HazeSourceSelection.All,
        retention = HazeSourceRetention.KeepLastFrame,
      ),
      HazeInput.Sources(
        state = hazeState,
        selection = HazeSourceSelection.All,
        retention = HazeSourceRetention.ClearWhenUnavailable,
      ),
    )
    val samplingPolicies = listOf(
      HazeSampling.Default,
      HazeSampling.FullResolution,
      HazeSampling.Fixed(0.5f),
    )
    val cases = buildList {
      inputs.forEach { input ->
        samplingPolicies.forEach { sampling ->
          listOf(true, false).forEach { expandLayerBounds ->
            add(StructuralCase(input, sampling, expandLayerBounds))
          }
        }
      }
    }

    setContent {
      Box(Modifier.size(100.dp)) {
        Spacer(Modifier.size(100.dp).hazeSource(hazeState))
        cases.forEachIndexed { index, case ->
          Spacer(
            Modifier
              .size(10.dp)
              .testTag("glass-$index")
              .hazeGlass(
                factory = case.factory,
                input = case.input,
                style = GlassStyle,
                sampling = case.sampling,
                expandLayerBounds = case.expandLayerBounds,
                interactionSource = null,
              ),
          )
        }
      }
    }
    waitForIdle()
    onRoot().captureToImage()

    cases.forEach { case ->
      val effect = case.factory.effects.single()
      assertThat(effect.sampling).isEqualTo(case.sampling)
      val boundsCalls = assertThat(
        effect.calculateLayerBoundsCalls,
        name = "${case.input}/${case.sampling}/expand=${case.expandLayerBounds}",
      )
      if (case.input is HazeInput.Sources && case.expandLayerBounds) {
        boundsCalls.isGreaterThan(0)
      } else {
        boundsCalls.isEqualTo(0)
      }
    }
  }

  @Test
  fun sourceRetention_reactsToUnavailableSourceWithoutReplacingRuntime() = runComposeUiTest {
    val hazeState = HazeState()
    val retention = mutableStateOf<HazeSourceRetention>(HazeSourceRetention.KeepLastFrame)
    val showSource = mutableStateOf(true)
    val factory = RecordingGlassFactory()

    setContent {
      Box(Modifier.size(100.dp)) {
        if (showSource.value) {
          Spacer(Modifier.size(100.dp).hazeSource(hazeState))
        }
        Spacer(
          Modifier
            .size(10.dp)
            .hazeGlass(
              factory = factory,
              input = HazeInput.Sources(
                hazeState,
                selection = HazeSourceSelection.All,
                retention = retention.value,
              ),
              style = GlassStyle,
              sampling = HazeSampling.Default,
              expandLayerBounds = true,
              interactionSource = null,
            ),
        )
      }
    }
    waitForIdle()
    onRoot().captureToImage()
    val effect = factory.effects.single()
    val clearCallsBeforeKeepUnavailable = effect.clearRetainedOutputCalls
    val retainedDecisionsBeforeKeepUnavailable = effect.retainedDrawDecisions

    showSource.value = false
    waitForIdle()
    onRoot().captureToImage()

    assertThat(factory.effects.single()).isSameInstanceAs(effect)
    assertThat(effect.clearRetainedOutputCalls).isEqualTo(clearCallsBeforeKeepUnavailable)
    assertThat(effect.retainedDrawDecisions)
      .isGreaterThan(retainedDecisionsBeforeKeepUnavailable)

    showSource.value = true
    waitForIdle()
    onRoot().captureToImage()

    retention.value = HazeSourceRetention.ClearWhenUnavailable
    waitForIdle()
    onRoot().captureToImage()
    val clearCallsBeforeClearUnavailable = effect.clearRetainedOutputCalls

    showSource.value = false
    waitForIdle()
    onRoot().captureToImage()

    assertThat(factory.effects.single()).isSameInstanceAs(effect)
    assertThat(effect.clearRetainedOutputCalls).isGreaterThan(clearCallsBeforeClearUnavailable)
  }

  @Test
  fun recomposition_replacesConfigurationWithoutSharingNodeRuntime() = runComposeUiTest {
    val firstStyle = GlassStyle { tint(Color.Red) }
    val secondStyle = GlassStyle { contrast(0.25f) }
    val sharedStyle = mutableStateOf(firstStyle)
    val initialInteractionSource = MutableInteractionSource()
    val interactionSource = mutableStateOf<InteractionSource?>(initialInteractionSource)
    val factory = RecordingGlassFactory()

    setContent {
      Box {
        repeat(2) {
          Spacer(
            Modifier
              .size(10.dp)
              .hazeGlass(
                factory = factory,
                input = HazeInput.Content,
                style = sharedStyle.value,
                sampling = HazeSampling.Default,
                expandLayerBounds = true,
                interactionSource = interactionSource.value,
              ),
          )
        }
      }
    }
    waitForIdle()

    val first = factory.effects[0]
    val second = factory.effects[1]
    assertThat(first).isNotSameInstanceAs(second)
    assertThat(first.delegate).isNotSameInstanceAs(second.delegate)
    assertThat(first.delegate.style).isSameInstanceAs(firstStyle)
    assertThat(second.delegate.style).isSameInstanceAs(firstStyle)
    assertThat(first.delegate.interactionSource)
      .isSameInstanceAs(initialInteractionSource)
    assertThat(second.delegate.interactionSource)
      .isSameInstanceAs(initialInteractionSource)

    sharedStyle.value = secondStyle
    interactionSource.value = null
    waitForIdle()

    assertThat(factory.effects.size).isEqualTo(2)
    assertThat(first.delegate.style).isSameInstanceAs(secondStyle)
    assertThat(second.delegate.style).isSameInstanceAs(secondStyle)
    assertThat(first.delegate.interactionSource).isNull()
    assertThat(second.delegate.interactionSource).isNull()
  }

  @Test
  fun capturedStateMutation_requiresReplacementStyleToUpdateSharedNodes() = runComposeUiTest {
    val alpha = mutableFloatStateOf(0.2f)
    val sharedStyle = mutableStateOf(GlassStyle { alpha(alpha.floatValue) })
    val factory = RecordingGlassFactory()

    setContent {
      Box {
        repeat(2) {
          Spacer(
            Modifier
              .size(10.dp)
              .hazeGlass(
                factory = factory,
                input = HazeInput.Content,
                style = sharedStyle.value,
                sampling = HazeSampling.Default,
                expandLayerBounds = true,
                interactionSource = null,
              ),
          )
        }
      }
    }
    waitForIdle()

    val firstRuntime = factory.effects[0].delegate
    val secondRuntime = factory.effects[1].delegate
    assertThat(firstRuntime).isNotSameInstanceAs(secondRuntime)
    assertThat(firstRuntime.alpha).isEqualTo(0.2f)
    assertThat(secondRuntime.alpha).isEqualTo(0.2f)

    alpha.floatValue = 0.8f
    waitForIdle()

    assertThat(factory.effects.size).isEqualTo(2)
    assertThat(firstRuntime.alpha).isEqualTo(0.2f)
    assertThat(secondRuntime.alpha).isEqualTo(0.2f)

    sharedStyle.value = GlassStyle { alpha(alpha.floatValue) }
    waitForIdle()

    assertThat(factory.effects.size).isEqualTo(2)
    assertThat(firstRuntime.alpha).isEqualTo(0.8f)
    assertThat(secondRuntime.alpha).isEqualTo(0.8f)
  }

  @Test
  fun stylePrecedence_reachesRuntimeWithAtomicCompoundWrites() = runComposeUiTest {
    val localStyle = GlassStyle {
      alpha(0.3f)
      whitePoint(0.2f)
      optics(GlassOptics.Absolute(depth = 0.2f, blurRadius = 12.dp))
      pressed {
        lightingIntensity(0.2f)
        refractionMultiplier(1.2f)
      }
    }
    val explicitStyle = GlassStyle {
      alpha(0.6f)
      specularIntensity(0.6f)
      optics(GlassOptics.Absolute(depth = 0.5f, blurRadius = 16.dp))
      pressed {
        lightingIntensity(0.5f)
        refractionMultiplier(1.5f)
      }
    }.then {
      alpha(0.8f)
      optics(GlassOptics.Absolute(depth = 0.7f))
      pressed { lightingIntensity(0.9f) }
    }
    val factory = RecordingGlassFactory()

    setContent {
      CompositionLocalProvider(LocalGlassStyle provides localStyle) {
        Spacer(
          Modifier.size(10.dp).hazeGlass(
            factory = factory,
            input = HazeInput.Content,
            style = explicitStyle,
            sampling = HazeSampling.Default,
            expandLayerBounds = true,
            interactionSource = null,
          ),
        )
      }
    }
    waitForIdle()

    val runtime = factory.effects.single().delegate
    val pressed = runtime.resolvedInteractionSlots.pressed?.response
    assertThat(runtime.contrast).isEqualTo(GlassDefaults.contrast)
    assertThat(runtime.alpha).isEqualTo(0.8f)
    assertThat(runtime.whitePoint).isEqualTo(0.2f)
    assertThat(runtime.specularIntensity).isEqualTo(0.6f)
    assertThat(runtime.optics).isEqualTo(GlassOptics.Absolute(depth = 0.7f))
    assertThat(pressed?.lightingIntensity?.value).isEqualTo(0.9f)
    assertThat(pressed?.refractionMultiplier).isNull()
  }

  @Test
  fun sharedInteractionStyle_preservesPerNodeMechanicsAndRuntimeState() = runComposeUiTest {
    val positionSpec = tween<androidx.compose.ui.geometry.Offset>(300)
    val sharedStyle = GlassStyle {
      hovered { lightingIntensity(0.2f) }
      focused { whitePointDelta(0.1f) }
      pressed { refractionMultiplier(1.4f) }
      interactionLightRadiusFraction(0.9f)
      interactionPositionAnimationSpec(positionSpec)
    }
    val firstSource = MutableInteractionSource()
    val secondSource = MutableInteractionSource()
    val factory = RecordingGlassFactory()

    setContent {
      Box {
        Spacer(
          Modifier.size(20.dp).hazeGlass(
            factory = factory,
            input = HazeInput.Content,
            style = sharedStyle,
            sampling = HazeSampling.Default,
            expandLayerBounds = true,
            interactionSource = firstSource,
            interactionTransformTarget = GlassTransformTarget.MaterialOnly,
            interactionTransformPivot = GlassTransformPivot.Pointer,
            interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
          ),
        )
        Spacer(
          Modifier.size(30.dp).hazeGlass(
            factory = factory,
            input = HazeInput.Content,
            style = sharedStyle,
            sampling = HazeSampling.Default,
            expandLayerBounds = true,
            interactionSource = secondSource,
            interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
            interactionTransformPivot = GlassTransformPivot.Center,
            interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
          ),
        )
      }
    }
    waitForIdle()

    val first = factory.effects[0].delegate
    val second = factory.effects[1].delegate
    assertThat(first.resolvedInteractionSlots).isEqualTo(second.resolvedInteractionSlots)
    assertThat(first.interactionLightRadiusFraction).isEqualTo(0.9f)
    assertThat(second.interactionLightRadiusFraction).isEqualTo(0.9f)
    assertThat(first.interactionPositionAnimationSpec).isEqualTo(positionSpec)
    assertThat(second.interactionPositionAnimationSpec).isEqualTo(positionSpec)
    assertThat(first.interactionSource).isSameInstanceAs(firstSource)
    assertThat(second.interactionSource).isSameInstanceAs(secondSource)
    assertThat(first.interactionTransformTarget).isEqualTo(GlassTransformTarget.MaterialOnly)
    assertThat(second.interactionTransformTarget)
      .isEqualTo(GlassTransformTarget.MaterialAndContent)
    assertThat(first.interactionTransformPivot).isEqualTo(GlassTransformPivot.Pointer)
    assertThat(second.interactionTransformPivot).isEqualTo(GlassTransformPivot.Center)
    assertThat(first.interactionReducedMotionPolicy).isEqualTo(GlassReducedMotionPolicy.System)
    assertThat(second.interactionReducedMotionPolicy).isEqualTo(GlassReducedMotionPolicy.Full)
    assertThat(first.attachedContextForTest?.modifierSize)
      .isNotEqualTo(second.attachedContextForTest?.modifierSize)
    assertThat(first.interactionControllerForTest)
      .isNotSameInstanceAs(second.interactionControllerForTest)
    assertThat(first.currentInteractionState).isNotSameInstanceAs(second.currentInteractionState)
    assertThat(first.delegate).isNotSameInstanceAs(second.delegate)

    runOnIdle { first.setPressedForTest(androidx.compose.ui.geometry.Offset(5f, 5f)) }
    waitForIdle()

    assertThat(first.currentInteractionSignals.rawPressed).isTrue()
    assertThat(second.currentInteractionSignals.rawPressed).isFalse()
  }

  @Test
  fun typedStyle_responseReplacement_updatesPointerTopologyWithoutReplacingRenderer() = runComposeUiTest {
    val initialPositionSpec = tween<androidx.compose.ui.geometry.Offset>(100)
    val replacementPositionSpec = tween<androidx.compose.ui.geometry.Offset>(200)
    val style = mutableStateOf(
      GlassStyle {
        focused { lightingIntensity(0.2f) }
        interactionLightRadiusFraction(0.8f)
        interactionPositionAnimationSpec(initialPositionSpec)
      },
    )
    val factory = RecordingGlassFactory()

    setContent {
      Spacer(
        Modifier.size(10.dp).hazeGlass(
          factory = factory,
          input = HazeInput.Content,
          style = style.value,
          sampling = HazeSampling.Default,
          expandLayerBounds = true,
          interactionSource = null,
        ),
      )
    }
    waitForIdle()

    val effect = factory.effects.single()
    val renderer = effect.delegate.delegate
    assertThat(effect.delegate.observesPointerEvents).isEqualTo(false)
    assertThat(effect.delegate.interactionLightRadiusFraction).isEqualTo(0.8f)
    assertThat(effect.delegate.interactionPositionAnimationSpec).isEqualTo(initialPositionSpec)

    style.value = GlassStyle {
      pressed { lightingIntensity(0.8f) }
      interactionLightRadiusFraction(1.2f)
      interactionPositionAnimationSpec(replacementPositionSpec)
    }
    waitForIdle()

    assertThat(factory.effects.single()).isSameInstanceAs(effect)
    assertThat(effect.delegate.delegate).isSameInstanceAs(renderer)
    assertThat(effect.delegate.observesPointerEvents).isEqualTo(true)
    assertThat(effect.delegate.interactionLightRadiusFraction).isEqualTo(1.2f)
    assertThat(effect.delegate.interactionPositionAnimationSpec)
      .isEqualTo(replacementPositionSpec)

    style.value = GlassStyle
    waitForIdle()

    assertThat(factory.effects.single()).isSameInstanceAs(effect)
    assertThat(effect.delegate.delegate).isSameInstanceAs(renderer)
    assertThat(effect.delegate.observesPointerEvents).isEqualTo(false)
    assertThat(effect.delegate.interactionLightRadiusFraction)
      .isEqualTo(GlassDefaults.interactionLightRadiusFraction)
    assertThat(effect.delegate.interactionPositionAnimationSpec)
      .isEqualTo(GlassDefaults.positionAnimationSpec)
  }

  @Test
  fun modifierMechanicsReplacement_updatesOnlyAffectedNode() = runComposeUiTest {
    val style = GlassStyle { focused { lightingIntensity(0.2f) } }
    val firstSource = mutableStateOf<InteractionSource?>(MutableInteractionSource())
    val firstTarget = mutableStateOf(GlassTransformTarget.MaterialOnly)
    val firstPivot = mutableStateOf(GlassTransformPivot.Pointer)
    val firstMotionPolicy = mutableStateOf(GlassReducedMotionPolicy.System)
    val secondSource = MutableInteractionSource()
    val factory = RecordingGlassFactory()

    setContent {
      Box {
        Spacer(
          Modifier.size(20.dp).hazeGlass(
            factory = factory,
            input = HazeInput.Content,
            style = style,
            sampling = HazeSampling.Default,
            expandLayerBounds = true,
            interactionSource = firstSource.value,
            interactionTransformTarget = firstTarget.value,
            interactionTransformPivot = firstPivot.value,
            interactionReducedMotionPolicy = firstMotionPolicy.value,
          ),
        )
        Spacer(
          Modifier.size(30.dp).hazeGlass(
            factory = factory,
            input = HazeInput.Content,
            style = style,
            sampling = HazeSampling.Default,
            expandLayerBounds = true,
            interactionSource = secondSource,
            interactionTransformTarget = GlassTransformTarget.MaterialOnly,
            interactionTransformPivot = GlassTransformPivot.Pointer,
            interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
          ),
        )
      }
    }
    waitForIdle()

    val first = factory.effects[0]
    val second = factory.effects[1]
    val firstRenderer = first.delegate.delegate
    val secondRenderer = second.delegate.delegate
    val firstController = first.delegate.interactionControllerForTest
    val secondController = second.delegate.interactionControllerForTest
    val replacementSource = MutableInteractionSource()

    firstSource.value = replacementSource
    firstTarget.value = GlassTransformTarget.MaterialAndContent
    firstPivot.value = GlassTransformPivot.Center
    firstMotionPolicy.value = GlassReducedMotionPolicy.Full
    waitForIdle()

    assertThat(factory.effects[0]).isSameInstanceAs(first)
    assertThat(factory.effects[1]).isSameInstanceAs(second)
    assertThat(first.delegate.delegate).isSameInstanceAs(firstRenderer)
    assertThat(second.delegate.delegate).isSameInstanceAs(secondRenderer)
    assertThat(first.delegate.interactionControllerForTest).isSameInstanceAs(firstController)
    assertThat(second.delegate.interactionControllerForTest).isSameInstanceAs(secondController)
    assertThat(first.delegate.interactionSource).isSameInstanceAs(replacementSource)
    assertThat(first.delegate.interactionTransformTarget)
      .isEqualTo(GlassTransformTarget.MaterialAndContent)
    assertThat(first.delegate.interactionTransformPivot).isEqualTo(GlassTransformPivot.Center)
    assertThat(first.delegate.interactionReducedMotionPolicy).isEqualTo(GlassReducedMotionPolicy.Full)
    assertThat(second.delegate.interactionSource).isSameInstanceAs(secondSource)
    assertThat(second.delegate.interactionTransformTarget)
      .isEqualTo(GlassTransformTarget.MaterialOnly)
    assertThat(second.delegate.interactionTransformPivot).isEqualTo(GlassTransformPivot.Pointer)
    assertThat(second.delegate.interactionReducedMotionPolicy)
      .isEqualTo(GlassReducedMotionPolicy.System)
  }
}

private class StructuralCase(
  val input: HazeInput,
  val sampling: HazeSampling,
  val expandLayerBounds: Boolean,
) {
  val factory = RecordingGlassFactory()
}

private class RecordingGlassFactory : HazeEffectFactory<GlassNodeConfiguration> {
  val effects = mutableListOf<RecordingGlassRuntimeEffect>()

  override fun createRenderer(): HazeEffectRenderer<GlassNodeConfiguration> {
    return RecordingGlassRuntimeEffect(GlassRuntimeEffect()).also(effects::add)
  }
}

private class RecordingGlassRuntimeEffect(
  val delegate: GlassRuntimeEffect,
) :
  HazeEffectRenderer<GlassNodeConfiguration>,
  HazeEffectRendererLifecycle<GlassNodeConfiguration>,
  HazeEffectRendererDrawHooks<GlassNodeConfiguration>,
  HazeEffectRendererRetainedOutput,
  HazeEffectRendererInteraction {
  var sampling: HazeSampling = HazeSampling.Default
    private set

  var calculateLayerBoundsCalls = 0
  var clearRetainedOutputCalls = 0
  var retainedDrawDecisions = 0

  override fun HazeEffectDrawScope.draw(style: GlassNodeConfiguration) {
    with(delegate) { draw(style) }
  }

  override fun HazeEffectLayoutScope.calculateLayerBounds(
    style: GlassNodeConfiguration,
  ): Rect {
    calculateLayerBoundsCalls++
    return with(delegate) { calculateLayerBounds(style) }
  }

  override fun attach(scope: HazeEffectLifecycleScope) {
    delegate.attach(scope)
  }

  override fun update(
    scope: HazeEffectLifecycleScope,
    style: GlassNodeConfiguration,
    sampling: HazeSampling,
  ) {
    this.sampling = sampling
    delegate.update(scope, style, sampling)
  }

  override fun detach() {
    delegate.detach()
  }

  override fun HazeEffectRuntimeDrawScope.prepareDraw(style: GlassNodeConfiguration) {
    with(delegate) { prepareDraw(style) }
  }

  override fun HazeEffectRuntimeDrawScope.drawForeground(style: GlassNodeConfiguration) {
    with(delegate) { drawForeground(style) }
  }

  override fun shouldDrawContentBehind(): Boolean = delegate.shouldDrawContentBehind()

  override fun shouldClipToNodeBounds(): Boolean = delegate.shouldClipToNodeBounds()

  override fun shouldPreferClipToInputBounds(): Boolean =
    delegate.shouldPreferClipToInputBounds()

  override val observesPointerEvents: Boolean
    get() = delegate.observesPointerEvents

  override fun onPointerEvent(event: PointerEvent, scope: HazeEffectLifecycleScope) {
    delegate.onPointerEvent(event, scope)
  }

  override fun onCancelPointerInput(scope: HazeEffectLifecycleScope) {
    delegate.onCancelPointerInput(scope)
  }

  override fun currentContentTransform(): HazeEffectContentTransform =
    delegate.currentContentTransform()

  override fun onTrimMemory(level: TrimMemoryLevel) {
    delegate.onTrimMemory(level)
  }

  override fun dispose() {
    delegate.dispose()
  }

  override fun canDrawRetainedOutput(): Boolean = delegate.canDrawRetainedOutput()

  override fun shouldDrawRetainedOutput(): Boolean {
    return delegate.shouldDrawRetainedOutput().also { shouldDraw ->
      if (shouldDraw) retainedDrawDecisions++
    }
  }

  override fun clearRetainedOutput() {
    clearRetainedOutputCalls++
    delegate.clearRetainedOutput()
  }
}
