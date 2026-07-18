// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassInteractionControllerTest : ContextTest() {

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
    val effect = GlassVisualEffect().apply {
      hovered()
      pressed { scale(0.97f) }
    }

    val result = resolveGlassInteractionTargets(
      slots = effect.interactionSlots,
      signals = GlassInteractionSignals(rawHovered = true, rawPressed = true),
    )

    assertThat(result.lightingIntensity.value).isEqualTo(0.35f)
    assertThat(result.refractionMultiplier.value).isEqualTo(1.02f)
    assertThat(result.whitePointDelta.value).isEqualTo(0.01f)
    assertThat(result.scaleX.value).isEqualTo(0.97f)
  }

  @Test
  fun resolver_hiddenStateChangeDoesNotChangeOwner() {
    val effect = GlassVisualEffect().apply {
      hovered { lightingIntensity(0.4f) }
      pressed { lightingIntensity(0.8f) }
    }

    val pressedOnly = resolveGlassInteractionTargets(
      effect.interactionSlots,
      GlassInteractionSignals(rawPressed = true),
    )
    val pressedAndHovered = resolveGlassInteractionTargets(
      effect.interactionSlots,
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
    val effect = GlassVisualEffect().apply {
      hovered { animate(hoverTo, hoverFrom) { scale(0.99f) } }
      pressed { animate(pressTo, pressFrom) { scale(0.96f) } }
    }
    val idle = resolveGlassInteractionTargets(effect.interactionSlots, GlassInteractionSignals())
    val hover = resolveGlassInteractionTargets(
      effect.interactionSlots,
      GlassInteractionSignals(rawHovered = true),
    )
    val press = resolveGlassInteractionTargets(
      effect.interactionSlots,
      GlassInteractionSignals(rawHovered = true, rawPressed = true),
    )

    assertThat(
      selectTransitionSpec(
        previous = idle.scaleX,
        next = hover.scaleX,
        previousSlots = GlassInteractionSlots(),
        nextSlots = effect.interactionSlots,
        previousSignals = GlassInteractionSignals(),
        nextSignals = GlassInteractionSignals(rawHovered = true),
      ),
    ).isSameInstanceAs(hoverTo)
    assertThat(
      selectTransitionSpec(
        previous = hover.scaleX,
        next = press.scaleX,
        previousSlots = effect.interactionSlots,
        nextSlots = effect.interactionSlots,
        previousSignals = GlassInteractionSignals(rawHovered = true),
        nextSignals = GlassInteractionSignals(rawHovered = true, rawPressed = true),
      ),
    ).isSameInstanceAs(pressTo)
    assertThat(
      selectTransitionSpec(
        previous = press.scaleX,
        next = hover.scaleX,
        previousSlots = effect.interactionSlots,
        nextSlots = effect.interactionSlots,
        previousSignals = GlassInteractionSignals(rawHovered = true, rawPressed = true),
        nextSignals = GlassInteractionSignals(rawHovered = true),
      ),
    ).isSameInstanceAs(pressFrom)
  }

  @Test
  fun controller_animatesFromCurrentValueWithSelectedSpec() = runComposeUiTest {
    val effect = GlassVisualEffect().apply {
      pressed {
        animate(tween(100), tween(200)) {
          lightingIntensity(1f)
          scale(0.9f)
        }
      }
    }
    setContent {
      Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
    }
    waitForIdle()
    val controller = checkNotNull(effect.interactionControllerForTest)

    controller.updateConfiguration(effect.controllerConfiguration(systemMotionScale = 1f))
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
      val effect = GlassVisualEffect().apply {
        pressed {
          lightingIntensity(1f)
          refractionMultiplier(1.2f)
          whitePointDelta(0.2f)
          scale(0.9f)
        }
        interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
      }
      setContent {
        Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
      }
      waitForIdle()
      val controller = checkNotNull(effect.interactionControllerForTest)

      controller.updateConfiguration(effect.controllerConfiguration(systemMotionScale = 1f))
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
    val effect = GlassVisualEffect().apply {
      pressed { lightingIntensity(1f) }
    }
    setContent {
      Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
    }
    waitForIdle()
    val controller = checkNotNull(effect.interactionControllerForTest)
    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    waitForIdle()
    assertThat(controller.renderState.lightingIntensity).isEqualTo(1f)

    mainClock.autoAdvance = false
    effect.pressed {
      animate(tween(100), snap()) { lightingIntensity(0.2f) }
    }
    controller.updateConfiguration(effect.controllerConfiguration(systemMotionScale = 1f))
    mainClock.advanceTimeBy(50)

    assertThat(controller.renderState.lightingIntensity).isGreaterThan(0.2f)
    assertThat(controller.renderState.lightingIntensity).isLessThan(1f)
  }

  @Test
  fun controller_clearingActivePressUsesRemovedPressFromSpec() = runComposeUiTest {
    val effect = GlassVisualEffect().apply {
      hovered { lightingIntensity(0.2f) }
      pressed {
        animate(snap(), tween(100)) { lightingIntensity(1f) }
      }
    }
    setContent {
      Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
    }
    waitForIdle()
    val controller = checkNotNull(effect.interactionControllerForTest)
    controller.updateSignals(GlassInteractionSignals(rawHovered = true, rawPressed = true))
    waitForIdle()
    assertThat(controller.renderState.lightingIntensity).isEqualTo(1f)

    mainClock.autoAdvance = false
    effect.clearPressed()
    controller.updateConfiguration(effect.controllerConfiguration(systemMotionScale = 1f))
    mainClock.advanceTimeBy(50)

    assertThat(controller.renderState.lightingIntensity).isGreaterThan(0.2f)
    assertThat(controller.renderState.lightingIntensity).isLessThan(1f)
  }

  @Test
  fun controller_declarationOutsideAnimateSnapsImmediately() = runComposeUiTest {
    val effect = GlassVisualEffect().apply {
      pressed { lightingIntensity(0.7f) }
    }
    setContent {
      Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
    }
    waitForIdle()
    val controller = checkNotNull(effect.interactionControllerForTest)

    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    waitForIdle()

    assertThat(controller.renderState.lightingIntensity).isEqualTo(0.7f)
  }

  @Test
  fun controller_systemPolicyWithZeroScaleUsesReducedMotion() = runComposeUiTest {
    val effect = GlassVisualEffect().apply {
      pressed {
        lightingIntensity(1f)
        scale(0.9f)
      }
    }
    setContent {
      Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
    }
    waitForIdle()
    val controller = checkNotNull(effect.interactionControllerForTest)

    controller.updateConfiguration(effect.controllerConfiguration(systemMotionScale = 0f))
    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    waitForIdle()

    assertThat(controller.renderState.lightingIntensity).isEqualTo(1f)
    assertThat(controller.renderState.scaleX).isEqualTo(1f)
    assertThat(controller.renderState.scaleY).isEqualTo(1f)
  }

  @Test
  fun controller_fullPolicyOverridesZeroSystemScale() = runComposeUiTest {
    val effect = GlassVisualEffect().apply {
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
      pressed {
        animate(tween(100), tween(100)) {
          lightingIntensity(1f)
          scale(0.9f)
        }
      }
    }
    setContent {
      Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
    }
    waitForIdle()
    val controller = checkNotNull(effect.interactionControllerForTest)

    controller.updateConfiguration(effect.controllerConfiguration(systemMotionScale = 0f))
    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    mainClock.advanceTimeBy(50)

    assertThat(controller.renderState.lightingIntensity).isGreaterThan(0f)
    assertThat(controller.renderState.lightingIntensity).isLessThan(1f)
    assertThat(controller.renderState.scaleX).isGreaterThan(0.9f)
    assertThat(controller.renderState.scaleX).isLessThan(1f)
  }

  @Test
  fun controller_inFlightSystemAnimationRestartsWhenPolicyChangesToFull() = runComposeUiTest {
    val effect = GlassVisualEffect().apply {
      interactionPositionAnimationSpec = tween(200)
      pressed {
        animate(tween(200), tween(200)) { lightingIntensity(1f) }
      }
    }
    setContent {
      Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
    }
    waitForIdle()
    val controller = checkNotNull(effect.interactionControllerForTest)
    controller.updateConfiguration(effect.controllerConfiguration(systemMotionScale = 1f))
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
    val effect = GlassVisualEffect().apply {
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
      interactionPositionAnimationSpec = tween(200)
      pressed {
        animate(tween(200), tween(200)) { lightingIntensity(1f) }
      }
    }
    setContent {
      Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
    }
    waitForIdle()
    val controller = checkNotNull(effect.interactionControllerForTest)
    controller.updateConfiguration(effect.controllerConfiguration(systemMotionScale = 1f))
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

  @Test
  fun effect_motionPolicyUsesAttachedNodeCoroutineContextThroughLifecycleUpdate() =
    runComposeUiTest {
      val effect = GlassVisualEffect().apply { pressed() }
      setContent {
        Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
      }
      waitForIdle()
      val context = checkNotNull(effect.attachedContextForTest)
      val nodeMotionScale = context.coroutineScope.coroutineContext[MotionDurationScale]
      val controller = checkNotNull(effect.interactionControllerForTest)

      assertThat(nodeMotionScale).isNull()
      assertThat(controller.configurationForTest.reducedMotion).isEqualTo(false)
      assertThat(controller.configurationForTest.forceFullMotion).isEqualTo(false)

      effect.interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
      waitForIdle()

      assertThat(controller.configurationForTest.reducedMotion).isEqualTo(false)
      assertThat(controller.configurationForTest.forceFullMotion).isEqualTo(true)
    }

  @Test
  fun controller_leavingReducedMotionExposesActiveTransformTarget() = runComposeUiTest {
    val effect = GlassVisualEffect().apply {
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
      pressed {
        animate(tween(1_000), tween(1_000)) { scale(0.9f) }
      }
    }
    setContent {
      Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
    }
    waitForIdle()
    val controller = checkNotNull(effect.interactionControllerForTest)
    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    mainClock.advanceTimeBy(50)
    assertThat(controller.renderState.scaleX).isGreaterThan(0.9f)

    effect.interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    controller.updateConfiguration(effect.controllerConfiguration(systemMotionScale = 1f))
    mainClock.advanceTimeByFrame()
    assertThat(controller.renderState.scaleX).isEqualTo(1f)

    effect.interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
    controller.updateConfiguration(effect.controllerConfiguration(systemMotionScale = 1f))
    mainClock.advanceTimeByFrame()
    assertThat(controller.renderState.scaleX).isEqualTo(0.9f)
  }

  @Test
  fun controller_clearingFinalSlotDisposesAndImmediatelyExposesIdentity() = runComposeUiTest {
    val effect = GlassVisualEffect().apply { pressed { scale(0.9f) } }
    setContent {
      Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
    }
    waitForIdle()
    val context = checkNotNull(effect.attachedContextForTest)
    val controller = checkNotNull(effect.interactionControllerForTest)
    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    waitForIdle()

    effect.clearPressed()

    assertThat(effect.interactionControllerForTest).isNull()
    assertThat(effect.interactionRenderState(context)).isEqualTo(
      GlassInteractionRenderState(position = Offset(50f, 50f)),
    )
  }

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
