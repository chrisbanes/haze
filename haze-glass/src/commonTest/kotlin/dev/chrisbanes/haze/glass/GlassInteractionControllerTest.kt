// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performMultiModalInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectContentTransform
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRendererInteraction
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@OptIn(
  ExperimentalTestApi::class,
  ExperimentalHazeApi::class,
  InternalHazeApi::class,
)
class GlassInteractionControllerTest : ContextTest() {
  private var attachedRuntime: GlassRuntimeEffect? = null
  private val rendererFactories = mutableMapOf<GlassRuntimeEffect, HazeEffectFactory<GlassNodeConfiguration>>()

  @Test
  fun equivalentCustomInteraction_retainsSlotAndRevision() {
    val effect = GlassRuntimeEffect().apply { pressed { lightingIntensity(0.5f) } }
    val pressed = checkNotNull(effect.pressedSlot)

    effect.pressed { lightingIntensity(0.5f) }

    assertThat(effect.pressedSlot).isSameInstanceAs(pressed)
  }

  @Test
  fun changedCustomInteraction_replacesSlotAndRevision() {
    val effect = GlassRuntimeEffect().apply { pressed { lightingIntensity(0.5f) } }
    val pressed = checkNotNull(effect.pressedSlot)

    effect.pressed { lightingIntensity(0.6f) }

    assertThat(checkNotNull(effect.pressedSlot).revision).isGreaterThan(pressed.revision)
  }

  @Test
  fun controller_initialValidPosition_snapsToCenterBeforeAnimationFrames() = runComposeUiTest {
    mainClock.autoAdvance = false
    val effect = GlassRuntimeEffect().apply {
      testPressResponse()
      style = GlassStyle { interactionPositionAnimationSpec(tween(1_000)) }
    }
    setContent {
      Box(Modifier.size(100.dp).testGlass(effect))
    }

    mainClock.advanceTimeByFrame()

    assertThat(renderState(effect).position).isEqualTo(Offset(50f, 50f))
  }

  @Test
  fun materialOnlyTransform_isNotExposedToHazeNode() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      pressed { scale(0.9f, 0.8f) }
      interactionTransformTarget = GlassTransformTarget.MaterialOnly
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
    }
    setContent {
      Box(Modifier.size(100.dp, 80.dp).testGlass(effect))
    }
    waitForIdle()
    runtime(effect).setPressedForTest(position = Offset(20f, 30f))
    waitForIdle()
    val context = checkNotNull(runtime(effect).attachedContextForTest)

    assertThat(runtime(effect).currentContentTransform())
      .isEqualTo(HazeEffectContentTransform.Identity)
    assertThat(runtime(effect).currentMaterialTransform(context.modifierSize))
      .isEqualTo(HazeEffectContentTransform(0.9f, 0.8f, Offset(20f, 30f)))
  }

  @Test
  fun materialAndContentTransform_usesConfiguredPivot() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      pressed { scale(0.9f) }
      interactionTransformTarget = GlassTransformTarget.MaterialAndContent
      interactionTransformPivot = GlassTransformPivot.Center
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
    }
    setContent {
      Box(Modifier.size(100.dp, 80.dp).testGlass(effect))
    }
    waitForIdle()
    runtime(effect).setPressedForTest(position = Offset(20f, 30f))
    waitForIdle()
    val context = checkNotNull(runtime(effect).attachedContextForTest)

    assertThat(runtime(effect).currentContentTransform())
      .isEqualTo(HazeEffectContentTransform(0.9f, 0.9f, context.modifierSize.center))
    assertThat(runtime(effect).currentMaterialTransform(context.modifierSize))
      .isEqualTo(HazeEffectContentTransform.Identity)
  }

  @Test
  fun invalidGeometry_returnsIdentityTransform() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      pressed { scale(0.9f) }
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
    }
    setContent {
      Box(Modifier.size(0.dp).testGlass(effect))
    }
    waitForIdle()
    runtime(effect).setPressedForTest(position = Offset.Zero)
    waitForIdle()
    val context = checkNotNull(runtime(effect).attachedContextForTest)

    assertThat(runtime(effect).currentContentTransform())
      .isEqualTo(HazeEffectContentTransform.Identity)
    assertThat(runtime(effect).currentMaterialTransform(context.modifierSize))
      .isEqualTo(HazeEffectContentTransform.Identity)
  }

  @Test
  fun rawInput_usesFirstPointerAndRetainsLastPositionThroughRelease() = runComposeUiTest {
    val effect = reducedPressEffect()
    setTaggedEffectContent(effect)

    onNodeWithTag("glass").performMultiModalInput {
      touch {
        down(0, Offset(20f, 30f))
        down(1, Offset(80f, 70f))
        moveTo(1, Offset(90f, 90f))
        up(1)
        up(0)
      }
    }

    assertThat(renderState(effect).position).isEqualTo(Offset(20f, 30f))
  }

  @Test
  fun rawInput_retainsMovedPrimaryPositionThroughRelease() = runComposeUiTest {
    val effect = reducedPressEffect()
    setTaggedEffectContent(effect)

    onNodeWithTag("glass").performTouchInput { down(Offset(20f, 30f)) }
    onNodeWithTag("glass").performTouchInput {
      updatePointerTo(0, Offset(70f, 60f))
      move()
    }
    waitForIdle()
    assertThat(renderState(effect).position).isEqualTo(Offset(70f, 60f))

    onNodeWithTag("glass").performTouchInput { up() }
    waitForIdle()

    assertThat(renderState(effect).position).isEqualTo(Offset(70f, 60f))
  }

  @Test
  fun rawInput_primaryUpAtZeroSizeClearsActivePress() = runComposeUiTest {
    val effect = reducedPressEffect()
    var size by mutableStateOf(100.dp)
    setContent {
      Box(
        Modifier
          .size(size)
          .testTag("glass")
          .testGlass(effect),
      )
    }

    onNodeWithTag("glass").performTouchInput { down(Offset(20f, 30f)) }
    waitForIdle()
    assertThat(runtime(effect).currentInteractionSignals.rawPressed).isEqualTo(true)

    size = 0.dp
    waitForIdle()
    onNodeWithTag("glass").performTouchInput { up() }
    waitForIdle()

    assertThat(runtime(effect).currentInteractionSignals.rawPressed).isEqualTo(false)
    assertThat(renderState(effect).lightingIntensity).isEqualTo(0f)
  }

  @Test
  fun rawInput_clampsAndRetainsPrimaryPositionThroughRelease() = runComposeUiTest {
    val effect = reducedPressEffect()
    setTaggedEffectContent(effect)

    runtime(effect).setPressedForTest(position = Offset(120f, -10f), pressed = false)
    waitForIdle()
    assertThat(renderState(effect).position).isEqualTo(Offset(100f, 0f))
  }

  @Test
  fun rawAndSourcePress_mergeWithoutDoubleStrengthOrPrematureRelease() = runComposeUiTest {
    val source = MutableInteractionSource()
    val press = PressInteraction.Press(Offset(60f, 40f))
    val effect = reducedPressEffect().apply { interactionSource = source }
    setTaggedEffectContent(effect)
    waitForIdle()

    source.tryEmit(press)
    onNodeWithTag("glass").performTouchInput {
      down(Offset(20f, 20f))
      up()
    }
    waitForIdle()

    assertThat(renderState(effect).lightingIntensity).isEqualTo(1f)
    assertThat(renderState(effect).position).isEqualTo(Offset(60f, 40f))

    source.tryEmit(PressInteraction.Release(press))
    waitForIdle()

    assertThat(renderState(effect).lightingIntensity).isEqualTo(0f)
  }

  @Test
  fun focusAndSourceOnlyPress_useNodeCenter() = runComposeUiTest {
    val source = MutableInteractionSource()
    val focus = FocusInteraction.Focus()
    val press = PressInteraction.Press(Offset.Unspecified)
    val effect = GlassRuntimeEffect().apply {
      testFocusResponse()
      testPressResponse()
      interactionSource = source
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setContent {
      Box(Modifier.size(100.dp).testGlass(effect))
    }
    waitForIdle()

    source.tryEmit(focus)
    source.tryEmit(press)
    waitForIdle()

    assertThat(renderState(effect).position).isEqualTo(Offset(50f, 50f))
    assertThat(renderState(effect).lightingIntensity).isEqualTo(1f)
  }

  @Test
  fun overlappingSourcePresses_useLatestSpecifiedActivePosition() = runComposeUiTest {
    val source = MutableInteractionSource()
    val specifiedPress = PressInteraction.Press(Offset(60f, 40f))
    val unspecifiedPress = PressInteraction.Press(Offset.Unspecified)
    val effect = reducedPressEffect().apply { interactionSource = source }
    setTaggedEffectContent(effect)
    waitForIdle()

    source.tryEmit(specifiedPress)
    source.tryEmit(unspecifiedPress)
    waitForIdle()

    assertThat(renderState(effect).position).isEqualTo(Offset(60f, 40f))

    source.tryEmit(PressInteraction.Release(unspecifiedPress))
    waitForIdle()
    assertThat(renderState(effect).position).isEqualTo(Offset(60f, 40f))
  }

  @Test
  fun mouseEnterMoveExit_updatesRawHoverAndRetainsLastPosition() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      testHoverResponse()
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setTaggedEffectContent(effect)

    onNodeWithTag("glass").performMouseInput {
      enter(Offset(10f, 10f))
      moveTo(Offset(30f, 40f))
    }
    waitForIdle()
    assertThat(renderState(effect).position).isEqualTo(Offset(30f, 40f))
    assertThat(renderState(effect).lightingIntensity).isEqualTo(0.35f)

    onNodeWithTag("glass").performMouseInput { exit() }
    waitForIdle()
    assertThat(renderState(effect).lightingIntensity).isEqualTo(0f)
    assertThat(renderState(effect).position).isEqualTo(Offset(30f, 40f))
  }

  @Test
  fun hoverExit_retainsLatestHoverPositionAfterAnEarlierPress() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      testHoverResponse()
      testPressResponse()
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setTaggedEffectContent(effect)

    onNodeWithTag("glass").performTouchInput { click(Offset(10f, 20f)) }
    onNodeWithTag("glass").performMouseInput {
      enter(Offset(30f, 40f))
      moveTo(Offset(70f, 60f))
      exit()
    }
    waitForIdle()

    assertThat(renderState(effect).position).isEqualTo(Offset(70f, 60f))
  }

  @Test
  fun inactiveHoverPosition_doesNotOverrideSubsequentFocus() = runComposeUiTest {
    val source = MutableInteractionSource()
    val focus = FocusInteraction.Focus()
    val effect = GlassRuntimeEffect().apply {
      testHoverResponse()
      testFocusResponse()
      interactionSource = source
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setTaggedEffectContent(effect)

    onNodeWithTag("glass").performMouseInput {
      enter(Offset(10f, 10f))
      moveTo(Offset(20f, 30f))
      exit()
    }
    waitForIdle()
    assertThat(renderState(effect).position).isEqualTo(Offset(20f, 30f))

    source.tryEmit(focus)
    waitForIdle()

    assertThat(renderState(effect).position).isEqualTo(Offset(50f, 50f))
  }

  @Test
  fun activePointerInteractions_overrideFocusPosition() = runComposeUiTest {
    val source = MutableInteractionSource()
    val focus = FocusInteraction.Focus()
    val press = PressInteraction.Press(Offset(20f, 10f))
    val effect = GlassRuntimeEffect().apply {
      testHoverResponse()
      testFocusResponse()
      testPressResponse()
      interactionSource = source
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setTaggedEffectContent(effect)
    source.tryEmit(focus)
    waitForIdle()

    onNodeWithTag("glass").performMouseInput { enter(Offset(30f, 40f)) }
    waitForIdle()
    assertThat(renderState(effect).position).isEqualTo(Offset(30f, 40f))

    source.tryEmit(press)
    waitForIdle()
    assertThat(renderState(effect).position).isEqualTo(Offset(20f, 10f))

    onNodeWithTag("glass").performTouchInput { down(Offset(15f, 25f)) }
    waitForIdle()

    assertThat(renderState(effect).position).isEqualTo(Offset(15f, 25f))
  }

  @Test
  fun pointerCancellation_doesNotAllowRetainedHoverToOverrideLaterFocus() = runComposeUiTest {
    val source = MutableInteractionSource()
    val focus = FocusInteraction.Focus()
    val effect = GlassRuntimeEffect().apply {
      testHoverResponse()
      testFocusResponse()
      interactionSource = source
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setTaggedEffectContent(effect)

    onNodeWithTag("glass").performMouseInput { enter(Offset(20f, 30f)) }
    interactive(effect).onCancelPointerInput(context(effect))
    waitForIdle()
    assertThat(renderState(effect).position).isEqualTo(Offset(20f, 30f))

    source.tryEmit(focus)
    waitForIdle()

    assertThat(renderState(effect).position).isEqualTo(Offset(50f, 50f))
  }

  @Test
  fun rawAndSourceHover_releaseIndependently() = runComposeUiTest {
    val source = MutableInteractionSource()
    val sourceHover = HoverInteraction.Enter()
    val effect = GlassRuntimeEffect().apply {
      testHoverResponse()
      interactionSource = source
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setTaggedEffectContent(effect)
    waitForIdle()

    source.tryEmit(sourceHover)
    onNodeWithTag("glass").performMouseInput { enter(Offset(30f, 40f)) }
    waitForIdle()
    assertThat(renderState(effect).lightingIntensity).isEqualTo(0.35f)

    onNodeWithTag("glass").performMouseInput { exit() }
    waitForIdle()
    assertThat(renderState(effect).lightingIntensity).isEqualTo(0.35f)

    source.tryEmit(HoverInteraction.Exit(sourceHover))
    waitForIdle()
    assertThat(renderState(effect).lightingIntensity).isEqualTo(0f)
  }

  @Test
  fun sourceHoverAndFocus_releaseIndependently() = runComposeUiTest {
    val source = MutableInteractionSource()
    val hover = HoverInteraction.Enter()
    val focus = FocusInteraction.Focus()
    val effect = GlassRuntimeEffect().apply {
      hovered { lightingIntensity(0.4f) }
      focused { lightingIntensity(0.2f) }
      interactionSource = source
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setTaggedEffectContent(effect)
    waitForIdle()

    source.tryEmit(focus)
    source.tryEmit(hover)
    waitForIdle()
    assertThat(renderState(effect).lightingIntensity).isEqualTo(0.4f)

    source.tryEmit(HoverInteraction.Exit(hover))
    waitForIdle()
    assertThat(renderState(effect).lightingIntensity).isEqualTo(0.2f)

    source.tryEmit(FocusInteraction.Unfocus(focus))
    waitForIdle()
    assertThat(renderState(effect).lightingIntensity).isEqualTo(0f)
  }

  @Test
  fun consumedPrimaryMovement_cancelsOnlyRawPress() = runComposeUiTest {
    val source = MutableInteractionSource()
    val sourcePress = PressInteraction.Press(Offset(60f, 40f))
    val effect = reducedPressEffect().apply { interactionSource = source }
    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testTag("glass")
          .pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                awaitPointerEvent(PointerEventPass.Main).changes.forEach { change ->
                  if (change.positionChanged()) change.consume()
                }
              }
            }
          }
          .testGlass(effect),
      )
    }
    waitForIdle()

    source.tryEmit(sourcePress)
    onNodeWithTag("glass").performTouchInput {
      down(Offset(20f, 20f))
      moveTo(Offset(30f, 30f))
    }
    waitForIdle()
    assertThat(renderState(effect).lightingIntensity).isEqualTo(1f)

    source.tryEmit(PressInteraction.Release(sourcePress))
    waitForIdle()
    assertThat(renderState(effect).lightingIntensity).isEqualTo(0f)
  }

  @Test
  fun nonFiniteSourcePosition_fallsBackToLastValidRawPosition() = runComposeUiTest {
    val source = MutableInteractionSource()
    val effect = reducedPressEffect().apply { interactionSource = source }
    setTaggedEffectContent(effect)
    waitForIdle()

    onNodeWithTag("glass").performTouchInput { click(Offset(20f, 30f)) }
    source.tryEmit(PressInteraction.Press(Offset(Float.NaN, Float.POSITIVE_INFINITY)))
    waitForIdle()

    assertThat(renderState(effect).position).isEqualTo(Offset(20f, 30f))
  }

  @Test
  fun zeroSizeGeometry_ignoresRawPositions() = runComposeUiTest {
    val source = MutableInteractionSource()
    val effect = reducedPressEffect().apply { interactionSource = source }
    setContent {
      Box(Modifier.size(0.dp).testGlass(effect))
    }
    waitForIdle()

    source.tryEmit(PressInteraction.Press(Offset(20f, 30f)))
    waitForIdle()

    assertThat(renderState(effect).position).isEqualTo(Offset.Zero)
  }

  @Test
  fun pointerCancellation_clearsOnlyRawSignals() = runComposeUiTest {
    val source = MutableInteractionSource()
    val sourcePress = PressInteraction.Press(Offset(60f, 40f))
    val effect = reducedPressEffect().apply { interactionSource = source }
    setTaggedEffectContent(effect)
    waitForIdle()

    source.tryEmit(sourcePress)
    val context = context(effect)
    onNodeWithTag("glass").performTouchInput { down(Offset(20f, 20f)) }
    interactive(effect).onCancelPointerInput(context)
    waitForIdle()
    assertThat(renderState(effect).lightingIntensity).isEqualTo(1f)

    source.tryEmit(PressInteraction.Release(sourcePress))
    waitForIdle()
    assertThat(renderState(effect).lightingIntensity).isEqualTo(0f)
  }

  @Test
  fun resolver_usesFixedPrecedencePerProperty() {
    val slots = testSlots(
      focused = response {
        lightingIntensity(0.2f)
        scale(0.99f)
      },
      hovered = response {
        lightingIntensity(0.4f)
        refractionMultiplier(1.03f)
      },
      pressed = response {
        scale(0.96f)
      },
    )

    val result = resolveGlassInteractionTargets(
      slots = slots,
      signals = GlassInteractionSignals(
        rawHovered = true,
        sourceFocused = true,
        rawPressed = true,
      ),
    )

    assertThat(result.lightingIntensity.value).isEqualTo(0.4f)
    assertThat(result.refractionMultiplier.value).isEqualTo(1.03f)
    assertThat(result.scaleX.value).isEqualTo(0.96f)
    assertThat(result.scaleY.value).isEqualTo(0.96f)
    assertThat(result.whitePointDelta.value).isEqualTo(0f)
  }

  @Test
  fun resolver_customScaleOnlyPressRetainsHoverLightingAndOptics() {
    val effect = GlassRuntimeEffect().apply {
      testHoverResponse()
      pressed { scale(0.97f) }
    }

    val result = resolveGlassInteractionTargets(
      slots = effect.runtimeSlots(),
      signals = GlassInteractionSignals(rawHovered = true, rawPressed = true),
    )

    assertThat(result.lightingIntensity.value).isEqualTo(0.35f)
    assertThat(result.refractionMultiplier.value).isEqualTo(1.02f)
    assertThat(result.whitePointDelta.value).isEqualTo(0.01f)
    assertThat(result.scaleX.value).isEqualTo(0.97f)
  }

  @Test
  fun resolver_hiddenStateChangeDoesNotChangeOwner() {
    val effect = GlassRuntimeEffect().apply {
      hovered { lightingIntensity(0.4f) }
      pressed { lightingIntensity(0.8f) }
    }

    val pressedOnly = resolveGlassInteractionTargets(
      effect.runtimeSlots(),
      GlassInteractionSignals(rawPressed = true),
    )
    val pressedAndHovered = resolveGlassInteractionTargets(
      effect.runtimeSlots(),
      GlassInteractionSignals(rawHovered = true, rawPressed = true),
    )

    assertThat(pressedAndHovered.lightingIntensity.owner)
      .isEqualTo(pressedOnly.lightingIntensity.owner)
  }

  @Test
  fun transitionSpec_usesEnteringToSpecAndDepartingFromSpec() {
    val hoverTo = tween<Float>(100)
    val hoverFrom = tween<Float>(200)
    val pressTo = tween<Float>(300)
    val pressFrom = tween<Float>(400)
    val effect = GlassRuntimeEffect().apply {
      hovered { animate(hoverTo, hoverFrom) { scale(0.99f) } }
      pressed { animate(pressTo, pressFrom) { scale(0.96f) } }
    }
    val idle = resolveGlassInteractionTargets(effect.runtimeSlots(), GlassInteractionSignals())
    val hover = resolveGlassInteractionTargets(
      effect.runtimeSlots(),
      GlassInteractionSignals(rawHovered = true),
    )
    val press = resolveGlassInteractionTargets(
      effect.runtimeSlots(),
      GlassInteractionSignals(rawHovered = true, rawPressed = true),
    )

    assertThat(
      selectTransitionSpec(
        previous = idle.scaleX,
        next = hover.scaleX,
        previousSlots = GlassInteractionSlots(),
        nextSlots = effect.runtimeSlots(),
        previousSignals = GlassInteractionSignals(),
        nextSignals = GlassInteractionSignals(rawHovered = true),
      ),
    ).isSameInstanceAs(hoverTo)
    assertThat(
      selectTransitionSpec(
        previous = hover.scaleX,
        next = press.scaleX,
        previousSlots = effect.runtimeSlots(),
        nextSlots = effect.runtimeSlots(),
        previousSignals = GlassInteractionSignals(rawHovered = true),
        nextSignals = GlassInteractionSignals(rawHovered = true, rawPressed = true),
      ),
    ).isSameInstanceAs(pressTo)
    assertThat(
      selectTransitionSpec(
        previous = press.scaleX,
        next = hover.scaleX,
        previousSlots = effect.runtimeSlots(),
        nextSlots = effect.runtimeSlots(),
        previousSignals = GlassInteractionSignals(rawHovered = true, rawPressed = true),
        nextSignals = GlassInteractionSignals(rawHovered = true),
      ),
    ).isSameInstanceAs(pressFrom)
  }

  @Test
  fun controller_animatesFromCurrentValueWithSelectedSpec() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      pressed {
        animate(tween(100), tween(200)) {
          lightingIntensity(1f)
          scale(0.9f)
        }
      }
    }
    setContent {
      Box(Modifier.size(100.dp).testGlass(effect))
    }
    waitForIdle()
    val controller = checkNotNull(runtime(effect).interactionControllerForTest)

    controller.updateConfiguration(effect.runtimeConfiguration(systemMotionScale = 1f))
    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    mainClock.advanceTimeBy(50)

    assertThat(controller.renderState.lightingIntensity).isGreaterThan(0f)
    assertThat(controller.renderState.lightingIntensity).isLessThan(1f)
    assertThat(controller.renderState.scaleX).isGreaterThan(0.9f)
    assertThat(controller.renderState.scaleX).isLessThan(1f)
  }

  @Test
  fun controller_reducedMotionSnapsLightingAndOpticsButKeepsIdentityTransform() =
    runComposeUiTest {
      val effect = GlassRuntimeEffect().apply {
        pressed {
          lightingIntensity(1f)
          refractionMultiplier(1.2f)
          whitePointDelta(0.2f)
          scale(0.9f)
        }
        interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
      }
      setContent {
        Box(Modifier.size(100.dp).testGlass(effect))
      }
      waitForIdle()
      val controller = checkNotNull(runtime(effect).interactionControllerForTest)

      controller.updateConfiguration(effect.runtimeConfiguration(systemMotionScale = 1f))
      controller.updateSignals(GlassInteractionSignals(rawPressed = true))
      waitForIdle()

      assertThat(controller.renderState).isEqualTo(
        GlassInteractionRenderState(
          position = Offset(50f, 50f),
          lightingIntensity = 1f,
          refractionMultiplier = 1.2f,
          whitePointDelta = 0.2f,
          scaleX = 1f,
          scaleY = 1f,
        ),
      )
    }

  @Test
  fun controller_replacingActiveResponseUsesReplacementToSpec() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      pressed { lightingIntensity(1f) }
    }
    setContent {
      Box(Modifier.size(100.dp).testGlass(effect))
    }
    waitForIdle()
    val controller = checkNotNull(runtime(effect).interactionControllerForTest)
    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    waitForIdle()
    assertThat(controller.renderState.lightingIntensity).isEqualTo(1f)

    mainClock.autoAdvance = false
    effect.pressed {
      animate(tween(100), snap()) { lightingIntensity(0.2f) }
    }
    controller.updateConfiguration(effect.runtimeConfiguration(systemMotionScale = 1f))
    mainClock.advanceTimeBy(50)

    assertThat(controller.renderState.lightingIntensity).isGreaterThan(0.2f)
    assertThat(controller.renderState.lightingIntensity).isLessThan(1f)
  }

  @Test
  fun controller_declarationOutsideAnimateSnapsImmediately() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      pressed { lightingIntensity(0.7f) }
    }
    setContent {
      Box(Modifier.size(100.dp).testGlass(effect))
    }
    waitForIdle()
    val controller = checkNotNull(runtime(effect).interactionControllerForTest)

    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    waitForIdle()

    assertThat(controller.renderState.lightingIntensity).isEqualTo(0.7f)
  }

  @Test
  fun controller_systemPolicyWithZeroScaleUsesReducedMotion() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      pressed {
        lightingIntensity(1f)
        scale(0.9f)
      }
    }
    setContent {
      Box(Modifier.size(100.dp).testGlass(effect))
    }
    waitForIdle()
    val controller = checkNotNull(runtime(effect).interactionControllerForTest)

    controller.updateConfiguration(effect.runtimeConfiguration(systemMotionScale = 0f))
    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    waitForIdle()

    assertThat(controller.renderState.lightingIntensity).isEqualTo(1f)
    assertThat(controller.renderState.scaleX).isEqualTo(1f)
    assertThat(controller.renderState.scaleY).isEqualTo(1f)
  }

  @Test
  fun controller_fullPolicyOverridesZeroSystemScale() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
      pressed {
        animate(tween(100), tween(100)) {
          lightingIntensity(1f)
          scale(0.9f)
        }
      }
    }
    setContent {
      Box(Modifier.size(100.dp).testGlass(effect))
    }
    waitForIdle()
    val controller = checkNotNull(runtime(effect).interactionControllerForTest)

    controller.updateConfiguration(effect.runtimeConfiguration(systemMotionScale = 0f))
    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    mainClock.advanceTimeBy(50)

    assertThat(controller.renderState.lightingIntensity).isGreaterThan(0f)
    assertThat(controller.renderState.lightingIntensity).isLessThan(1f)
    assertThat(controller.renderState.scaleX).isGreaterThan(0.9f)
    assertThat(controller.renderState.scaleX).isLessThan(1f)
  }

  @Test
  fun controller_inFlightPositionRestartsWhenAnimationSpecChanges() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      testPressResponse()
      style = GlassStyle { interactionPositionAnimationSpec(tween(1_000)) }
    }
    setContent {
      Box(Modifier.size(100.dp).testGlass(effect))
    }
    waitForIdle()
    val controller = checkNotNull(runtime(effect).interactionControllerForTest)
    mainClock.autoAdvance = false

    controller.updatePosition(Offset(100f, 100f))
    mainClock.advanceTimeBy(200)
    val positionBeforeSpecChange = controller.renderState.position.x
    assertThat(positionBeforeSpecChange).isGreaterThan(50f)
    assertThat(positionBeforeSpecChange).isLessThan(100f)

    controller.updateConfiguration(
      controller.configurationForTest.copy(positionAnimationSpec = tween(100)),
    )

    assertThat(controller.renderState.position.x).isEqualTo(positionBeforeSpecChange)
    mainClock.advanceTimeBy(50)
    assertThat(controller.renderState.position.x).isGreaterThan(positionBeforeSpecChange)
    assertThat(controller.renderState.position.x).isLessThan(100f)
    mainClock.advanceTimeBy(70)
    assertThat(controller.renderState.position.x).isEqualTo(100f)
  }

  @Test
  fun controller_inFlightSystemAnimationRestartsWhenPolicyChangesToFull() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle { interactionPositionAnimationSpec(tween(200)) }
      pressed {
        animate(tween(200), tween(200)) { lightingIntensity(1f) }
      }
    }
    setContent {
      Box(Modifier.size(100.dp).testGlass(effect))
    }
    waitForIdle()
    val controller = checkNotNull(runtime(effect).interactionControllerForTest)
    controller.updateConfiguration(effect.runtimeConfiguration(systemMotionScale = 1f))
    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    controller.updatePosition(Offset(100f, 100f))
    mainClock.advanceTimeBy(100)
    val valueBeforePolicyChange = controller.renderState.lightingIntensity
    val positionBeforePolicyChange = controller.renderState.position.x
    assertThat(valueBeforePolicyChange).isGreaterThan(0f)
    assertThat(valueBeforePolicyChange).isLessThan(1f)

    controller.updateConfiguration(
      controller.configurationForTest.copy(forceFullMotion = true),
    )
    mainClock.advanceTimeBy(120)

    assertThat(controller.renderState.lightingIntensity).isGreaterThan(valueBeforePolicyChange)
    assertThat(controller.renderState.lightingIntensity).isLessThan(1f)
    assertThat(controller.renderState.position.x).isGreaterThan(positionBeforePolicyChange)
    assertThat(controller.renderState.position.x).isLessThan(100f)
  }

  @Test
  fun controller_inFlightFullAnimationRestartsWhenPolicyChangesToSystem() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
      style = GlassStyle { interactionPositionAnimationSpec(tween(200)) }
      pressed {
        animate(tween(200), tween(200)) { lightingIntensity(1f) }
      }
    }
    setContent {
      Box(Modifier.size(100.dp).testGlass(effect))
    }
    waitForIdle()
    val controller = checkNotNull(runtime(effect).interactionControllerForTest)
    controller.updateConfiguration(effect.runtimeConfiguration(systemMotionScale = 1f))
    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    controller.updatePosition(Offset(100f, 100f))
    mainClock.advanceTimeBy(100)
    val valueBeforePolicyChange = controller.renderState.lightingIntensity
    val positionBeforePolicyChange = controller.renderState.position.x
    assertThat(valueBeforePolicyChange).isGreaterThan(0f)
    assertThat(valueBeforePolicyChange).isLessThan(1f)

    controller.updateConfiguration(
      controller.configurationForTest.copy(forceFullMotion = false),
    )
    mainClock.advanceTimeBy(120)

    assertThat(controller.renderState.lightingIntensity).isGreaterThan(valueBeforePolicyChange)
    assertThat(controller.renderState.lightingIntensity).isLessThan(1f)
    assertThat(controller.renderState.position.x).isGreaterThan(positionBeforePolicyChange)
    assertThat(controller.renderState.position.x).isLessThan(100f)
  }

  private fun reducedPressEffect(): GlassRuntimeEffect = GlassRuntimeEffect().apply {
    pressed {
      lightingIntensity(1f)
      refractionMultiplier(1.08f)
      whitePointDelta(0.04f)
      scale(0.98f)
    }
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  }

  private fun androidx.compose.ui.test.ComposeUiTest.setTaggedEffectContent(
    effect: GlassRuntimeEffect,
  ) {
    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testTag("glass")
          .testGlass(effect),
      )
    }
  }

  private fun Modifier.testGlass(effect: GlassRuntimeEffect): Modifier {
    val configuration = Snapshot.withoutReadObservation {
      GlassNodeConfiguration(
        style = effect.style,
        interactionSource = effect.interactionSource,
        interactionTransformTarget = effect.interactionTransformTarget,
        interactionTransformPivot = effect.interactionTransformPivot,
        interactionReducedMotionPolicy = effect.interactionReducedMotionPolicy,
      )
    }
    return hazeGlass(
      factory = rendererFactories.getOrPut(effect) {
        HazeEffectFactory {
          effect.also { attachedRuntime = it }
        }
      },
      input = HazeInput.Content,
      style = configuration.style,
      performanceMode = HazePerformanceMode.Default,
      expandLayerBounds = true,
      interactionSource = configuration.interactionSource,
      interactionTransformTarget = configuration.interactionTransformTarget,
      interactionTransformPivot = configuration.interactionTransformPivot,
      interactionReducedMotionPolicy = configuration.interactionReducedMotionPolicy,
    )
  }

  private fun GlassRuntimeEffect.update(context: HazeEffectLifecycleScope) {
    val configuration = GlassNodeConfiguration(
      style = this.style,
      interactionSource = this.interactionSource,
      interactionTransformTarget = this.interactionTransformTarget,
      interactionTransformPivot = this.interactionTransformPivot,
      interactionReducedMotionPolicy = this.interactionReducedMotionPolicy,
    )
    update(
      scope = context,
      style = configuration,
      sampling = HazeSampling.Default,
    )
  }

  private fun runtime(effect: GlassRuntimeEffect): GlassRuntimeEffect =
    checkNotNull(attachedRuntime).also { check(it === effect) }

  private fun interactive(effect: GlassRuntimeEffect): HazeEffectRendererInteraction =
    runtime(effect)

  private fun GlassRuntimeEffect.runtimeConfiguration(
    systemMotionScale: Float,
  ): GlassInteractionControllerConfiguration =
    controllerConfiguration(systemMotionScale)

  private fun GlassRuntimeEffect.runtimeSlots(): GlassInteractionSlots =
    interactionSlots

  private fun context(effect: GlassRuntimeEffect) =
    checkNotNull(runtime(effect).attachedContextForTest)

  private fun renderState(effect: GlassRuntimeEffect): GlassInteractionRenderState =
    runtime(effect).interactionRenderState(context(effect).modifierSize)

  private fun testSlots(
    focused: GlassInteractionResponse? = null,
    hovered: GlassInteractionResponse? = null,
    pressed: GlassInteractionResponse? = null,
  ): GlassInteractionSlots = GlassInteractionSlots(
    focused = focused?.let { GlassInteractionSlot(1L, it) },
    hovered = hovered?.let { GlassInteractionSlot(2L, it) },
    pressed = pressed?.let { GlassInteractionSlot(3L, it) },
  )

  private fun response(block: GlassInteractionScope.() -> Unit): GlassInteractionResponse =
    buildGlassInteractionResponse(block)
}

private class EqualInteractionSource : InteractionSource {
  private val events = MutableSharedFlow<Interaction>(extraBufferCapacity = 1)

  override val interactions: Flow<Interaction> = events

  fun tryEmit(interaction: Interaction): Boolean = events.tryEmit(interaction)

  override fun equals(other: Any?): Boolean = other is EqualInteractionSource

  override fun hashCode(): Int = 0
}
