// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GlassInteractionDslTest {

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
}
