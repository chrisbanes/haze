// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.VisualEffect
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GlassInteractionDslTest {

  @Test
  fun glassEffect_replayingInteractableWithCustomPress_retainsFinalSlotsAndConfiguration() {
    val scope = TestHazeEffectScope()
    scope.glassEffect {
      interactable()
      pressed { lightingIntensity(0.6f) }
    }
    val effect = scope.visualEffect as GlassVisualEffect
    val hovered = checkNotNull(effect.hoveredSlot)
    val focused = checkNotNull(effect.focusedSlot)
    val pressed = checkNotNull(effect.pressedSlot)
    effect.resetDirtyTracker()

    scope.glassEffect {
      interactable()
      pressed { lightingIntensity(0.6f) }
    }

    assertThat(effect.hoveredSlot).isEqualTo(hovered)
    assertThat(effect.focusedSlot).isEqualTo(focused)
    assertThat(effect.pressedSlot).isEqualTo(pressed)
    assertThat(effect.dirtyTracker).isEqualTo(dev.chrisbanes.haze.Bitmask())
  }

  @Test
  fun glassEffect_replayingInteractableThenClearPressed_retainsFinalSlotsAndConfiguration() {
    val scope = TestHazeEffectScope()
    scope.glassEffect {
      interactable()
      clearPressed()
    }
    val effect = scope.visualEffect as GlassVisualEffect
    val hovered = checkNotNull(effect.hoveredSlot)
    val focused = checkNotNull(effect.focusedSlot)
    effect.resetDirtyTracker()

    scope.glassEffect {
      interactable()
      clearPressed()
    }

    assertThat(effect.hoveredSlot).isEqualTo(hovered)
    assertThat(effect.focusedSlot).isEqualTo(focused)
    assertThat(effect.pressedSlot).isEqualTo(null)
    assertThat(effect.dirtyTracker).isEqualTo(dev.chrisbanes.haze.Bitmask())
  }

  @Test
  fun glassEffect_exceptionRollsBackStagedInteractions() {
    val scope = TestHazeEffectScope()
    scope.glassEffect { interactable() }
    val effect = scope.visualEffect as GlassVisualEffect
    val hovered = checkNotNull(effect.hoveredSlot)
    val focused = checkNotNull(effect.focusedSlot)
    val pressed = checkNotNull(effect.pressedSlot)
    effect.resetDirtyTracker()

    assertFailsWith<IllegalStateException> {
      scope.glassEffect {
        pressed { lightingIntensity(0.6f) }
        throw IllegalStateException("rollback")
      }
    }

    assertThat(effect.hoveredSlot).isEqualTo(hovered)
    assertThat(effect.focusedSlot).isEqualTo(focused)
    assertThat(effect.pressedSlot).isEqualTo(pressed)
    assertThat(effect.dirtyTracker).isEqualTo(dev.chrisbanes.haze.Bitmask())
  }

  @Test
  fun directInteractionCalls_applyImmediatelyOutsideGlassEffect() {
    val effect = GlassVisualEffect()

    effect.pressed { lightingIntensity(0.6f) }

    assertThat(effect.pressedSlot?.response?.lightingIntensity?.value).isEqualTo(0.6f)
  }

  @Test
  fun glassEffect_nonLocalReturnCommitsStagedInteractions() {
    val scope = TestHazeEffectScope()

    assertThat(configureAndReturn(scope)).isEqualTo("returned")
    assertThat(
      (scope.visualEffect as GlassVisualEffect).pressedSlot?.response?.lightingIntensity?.value,
    ).isEqualTo(0.6f)
  }

  @Test
  fun noResponses_isNotInteractive() {
    val effect = GlassVisualEffect()
    assertThat(effect.hoveredSlot).isEqualTo(null)
    assertThat(effect.focusedSlot).isEqualTo(null)
    assertThat(effect.pressedSlot).isEqualTo(null)
    assertThat(effect.observesPointerEvents).isFalse()
  }

  @Test
  fun interactable_matchesIndividualPresets() {
    val individual = GlassVisualEffect().apply {
      hovered()
      focused()
      pressed()
    }
    val shortcut = GlassVisualEffect().apply { interactable() }

    assertThat(shortcut.hoveredSlot?.response).isEqualTo(individual.hoveredSlot?.response)
    assertThat(shortcut.focusedSlot?.response).isEqualTo(individual.focusedSlot?.response)
    assertThat(shortcut.pressedSlot?.response).isEqualTo(individual.pressedSlot?.response)
  }

  @Test
  fun customPressed_replacesPresetAndStartsFromIdentity() {
    val effect = GlassVisualEffect().apply {
      interactable()
      pressed { scale(0.97f) }
    }

    val response = checkNotNull(effect.pressedSlot?.response)
    assertThat(response.scaleX?.value).isEqualTo(0.97f)
    assertThat(response.scaleY?.value).isEqualTo(0.97f)
    assertThat(response.lightingIntensity).isEqualTo(null)
    assertThat(response.refractionMultiplier).isEqualTo(null)
    assertThat(response.whitePointDelta).isEqualTo(null)
    assertThat(effect.hoveredSlot).isNotNull()
    assertThat(effect.focusedSlot).isNotNull()
  }

  @Test
  fun declarations_areLastWriteWins() {
    val effect = GlassVisualEffect().apply {
      pressed {
        scale(0.99f)
        scale(0.96f, 0.97f)
        lightingIntensity(0.2f)
        lightingIntensity(0.8f)
      }
    }

    val response = checkNotNull(effect.pressedSlot?.response)
    assertThat(response.scaleX?.value).isEqualTo(0.96f)
    assertThat(response.scaleY?.value).isEqualTo(0.97f)
    assertThat(response.lightingIntensity?.value).isEqualTo(0.8f)
  }

  @Test
  fun clearingFinalPointerSlot_disablesPointerObservation() {
    val effect = GlassVisualEffect().apply { pressed() }
    assertThat(effect.observesPointerEvents).isTrue()
    effect.clearPressed()
    assertThat(effect.observesPointerEvents).isFalse()
  }

  @Test
  fun focusOnly_doesNotObservePointers() {
    val effect = GlassVisualEffect().apply { focused() }
    assertThat(effect.observesPointerEvents).isFalse()
  }

  @Test
  fun invalidValues_failDuringResponseCompilation() {
    assertFailsWith<IllegalArgumentException> {
      GlassVisualEffect().pressed { scale(Float.NaN) }
    }
    assertFailsWith<IllegalArgumentException> {
      GlassVisualEffect().pressed { lightingIntensity(1.1f) }
    }
    assertFailsWith<IllegalArgumentException> {
      GlassVisualEffect().pressed { refractionMultiplier(2.1f) }
    }
    assertFailsWith<IllegalArgumentException> {
      GlassVisualEffect().pressed { whitePointDelta(-1.1f) }
    }
  }

  private fun configureAndReturn(scope: TestHazeEffectScope): String {
    scope.glassEffect {
      pressed { lightingIntensity(0.6f) }
      return "returned"
    }
    error("Unreachable")
  }
}

private class TestHazeEffectScope : HazeEffectScope {
  override var visualEffect: VisualEffect = VisualEffect.Empty
  override var inputScale: HazeInputScale = HazeInputScale.Default
  override var canDrawArea: ((HazeArea) -> Boolean)? = null
  override var drawContentBehind: Boolean = false
  override var clipToAreasBounds: Boolean? = null
  override var expandLayerBounds: Boolean? = null
  override var retainOutputWhenSourceUnavailable: Boolean = true
  override var forceInvalidateOnPreDraw: Boolean = false
}
