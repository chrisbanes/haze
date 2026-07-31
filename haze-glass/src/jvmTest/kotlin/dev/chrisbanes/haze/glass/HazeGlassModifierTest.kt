// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
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
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
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
      HazeSampling.Adaptive,
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
  fun reusedStateBackedStyle_replaysInEachNodeWhenSnapshotChanges() = runComposeUiTest {
    val alpha = mutableFloatStateOf(0.2f)
    val sharedStyle = GlassStyle { alpha(alpha.floatValue) }
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
                style = sharedStyle,
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
    assertThat(firstRuntime.alpha).isEqualTo(0.8f)
    assertThat(secondRuntime.alpha).isEqualTo(0.8f)
  }

  @Test
  fun typedStyle_responseReplacement_updatesPointerTopologyWithoutReplacingRenderer() = runComposeUiTest {
    val style = mutableStateOf(GlassStyle { focused { lightingIntensity(0.2f) } })
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
    assertThat(effect.delegate.observesPointerEvents).isEqualTo(false)

    style.value = GlassStyle { pressed { lightingIntensity(0.8f) } }
    waitForIdle()

    assertThat(effect.delegate.observesPointerEvents).isEqualTo(true)

    style.value = GlassStyle
    waitForIdle()

    assertThat(effect.delegate.observesPointerEvents).isEqualTo(false)
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
