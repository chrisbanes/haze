// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class BlurRetainedOutputStateTest {

  @Test
  fun clear_invalidatesPendingOutputUntilFreshOutputCompletes() {
    val state = BlurRetainedOutputState()
    val staleGeneration = state.beginOutputUpdate()

    state.clear()

    assertThat(state.isAvailable).isFalse()
    assertThat(state.isPending).isFalse()
    assertThat(state.completeOutputUpdate(staleGeneration)).isFalse()
    assertThat(state.isAvailable).isFalse()

    val freshGeneration = state.beginOutputUpdate()

    assertThat(state.isPending).isTrue()
    assertThat(state.completeOutputUpdate(freshGeneration)).isTrue()
    assertThat(state.isAvailable).isTrue()
  }

  @Test
  fun repeatedClears_keepOutputUnavailableWithoutStartingWork() {
    val state = BlurRetainedOutputState()

    state.clear()
    state.clear()

    assertThat(state.isAvailable).isFalse()
    assertThat(state.isPending).isFalse()
    assertThat(state.generation).isEqualTo(2)
  }
}
