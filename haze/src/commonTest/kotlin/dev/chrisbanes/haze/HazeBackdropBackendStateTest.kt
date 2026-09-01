// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class HazeBackdropBackendStateTest {

  @Test
  fun nativeSelection_remainsNativeWhileAvailable() {
    val state = HazeBackdropBackendState()

    assertThat(state.resolve(nativeAvailable = true))
      .isEqualTo(HazeBackdropBackendSelection.Native)
    assertThat(state.resolve(nativeAvailable = true))
      .isEqualTo(HazeBackdropBackendSelection.Native)
    assertThat(state.usesNative).isTrue()
    assertThat(state.usesFallback).isFalse()
  }

  @Test
  fun nativeSelection_downgradesWhenPlatformBecomesUnavailable() {
    val state = HazeBackdropBackendState()
    state.resolve(nativeAvailable = true)

    assertThat(state.resolve(nativeAvailable = false))
      .isEqualTo(HazeBackdropBackendSelection.FallbackUnavailable)
    assertThat(state.usesFallback).isTrue()
  }

  @Test
  fun unavailableSelection_remainsStickyWhenNativeBecomesAvailable() {
    val state = HazeBackdropBackendState()

    assertThat(state.resolve(nativeAvailable = false))
      .isEqualTo(HazeBackdropBackendSelection.FallbackUnavailable)
    assertThat(state.resolve(nativeAvailable = true))
      .isEqualTo(HazeBackdropBackendSelection.FallbackUnavailable)
    assertThat(state.usesFallback).isTrue()
  }

  @Test
  fun nativeFailure_remainsStickyAcrossLaterResolution() {
    val state = HazeBackdropBackendState()
    state.resolve(nativeAvailable = true)

    assertThat(state.fail()).isEqualTo(HazeBackdropBackendSelection.FallbackFailed)
    assertThat(state.resolve(nativeAvailable = true))
      .isEqualTo(HazeBackdropBackendSelection.FallbackFailed)
    assertThat(state.usesFallback).isTrue()
  }

  @Test
  fun reset_returnsToUndecidedForNewAttachment() {
    val state = HazeBackdropBackendState()
    state.resolve(nativeAvailable = false)

    state.reset()

    assertThat(state.selection).isEqualTo(HazeBackdropBackendSelection.Undecided)
    assertThat(state.usesNative).isFalse()
    assertThat(state.usesFallback).isFalse()
  }
}
