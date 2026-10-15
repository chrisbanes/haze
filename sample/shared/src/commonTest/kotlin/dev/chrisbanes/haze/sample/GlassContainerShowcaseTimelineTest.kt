// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import kotlin.test.Test

class GlassContainerShowcaseTimelineTest {
  @Test
  fun glassContainerShowcaseFrame_splitsSearchThenSelects() {
    assertThat(glassContainerShowcaseFrame(0.05f).searchDetachFraction).isEqualTo(0f)
    assertThat(glassContainerShowcaseFrame(0.27f).searchDetachFraction).isEqualTo(1f)
    assertThat(glassContainerShowcaseFrame(0.27f).selecting).isFalse()
    assertThat(glassContainerShowcaseFrame(0.5f).searchDetachFraction).isEqualTo(0f)
    assertThat(glassContainerShowcaseFrame(0.6f).selecting).isTrue()
    assertThat(glassContainerShowcaseFrame(0.8f).selecting).isFalse()
  }

  @Test
  fun glassContainerShowcaseFrame_loopsSeamlessly() {
    val start = glassContainerShowcaseFrame(0f)
    val end = glassContainerShowcaseFrame(1f)
    assertThat(end.searchDetachFraction).isEqualTo(start.searchDetachFraction)
    assertThat(end.selecting).isEqualTo(start.selecting)
    assertThat(end.gridDrift).isCloseTo(start.gridDrift, 0.0001f)
    assertThat(glassContainerShowcaseFrame(0.5f).gridDrift).isCloseTo(1f, 0.0001f)
  }

  @Test
  fun dragSearchBy_rejoinsSearchAfterInterruptingTheScriptedSplit() {
    val state = GlassContainerShowcaseState()
    state.seek(0.27f)
    state.pause()
    assertThat(state.searchOffset).isEqualTo(1f)

    state.dragSearchBy(-1f, maxOffset = 3f)
    assertThat(state.searchOffset).isEqualTo(0f)
  }

  @Test
  fun updateSelecting_pausesTheScriptSoItCannotUndoTheUsersAction() {
    val state = GlassContainerShowcaseState()
    state.seek(0.6f)
    assertThat(state.isPlaying).isTrue()

    state.updateSelecting(false)
    assertThat(state.isPlaying).isFalse()
    assertThat(state.selecting).isFalse()
  }

  @Test
  fun glassContainerShowcaseFrame_rejectsProgressOutsideTheLoop() {
    assertFailure { glassContainerShowcaseFrame(1.5f) }.isInstanceOf<IllegalArgumentException>()
    assertFailure { glassContainerShowcaseFrame(Float.NaN) }.isInstanceOf<IllegalArgumentException>()
  }
}
