// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class HazeInputTest {

  @Test
  fun sources_defaultsToBehindAndKeepLastFrame() {
    val input = HazeInput.Sources(HazeState())

    assertThat(input.selection).isEqualTo(HazeSourceSelection.Behind)
    assertThat(input.retention).isEqualTo(HazeSourceRetention.KeepLastFrame)
  }

  @Test
  fun sources_acceptsAllAndClearWhenUnavailable() {
    val input = HazeInput.Sources(
      state = HazeState(),
      selection = HazeSourceSelection.All,
      retention = HazeSourceRetention.ClearWhenUnavailable,
    )

    assertThat(input.selection).isEqualTo(HazeSourceSelection.All)
    assertThat(input.retention).isEqualTo(HazeSourceRetention.ClearWhenUnavailable)
  }

  @Test
  fun backdrop_defaultsToBehindAndKeepLastFrame() {
    val state = HazeState()
    val input = HazeInput.Backdrop(state)

    assertThat(input.state).isEqualTo(state)
    assertThat(input.fallbackSelection).isEqualTo(HazeSourceSelection.Behind)
    assertThat(input.fallbackRetention).isEqualTo(HazeSourceRetention.KeepLastFrame)
  }

  @Test
  fun backdrop_acceptsAllAndClearWhenUnavailableFallback() {
    val input = HazeInput.Backdrop(
      state = HazeState(),
      fallbackSelection = HazeSourceSelection.All,
      fallbackRetention = HazeSourceRetention.ClearWhenUnavailable,
    )

    assertThat(input.fallbackSelection).isEqualTo(HazeSourceSelection.All)
    assertThat(input.fallbackRetention).isEqualTo(HazeSourceRetention.ClearWhenUnavailable)
  }

  @Test
  fun sourceMetadata_exposesOnlyStableValues() {
    val metadata = HazeSourceMetadata(key = "source", zIndex = 2f)

    assertThat(metadata.key).isEqualTo("source")
    assertThat(metadata.zIndex).isEqualTo(2f)
  }

  @Test
  fun sourceSelection_whereComposesRefinementsWithAnd() {
    val selection = HazeSourceSelection.All
      .where { it.key == "source" }
      .where { it.zIndex == 2f }

    assertThat(selection.matches(HazeSourceMetadata(key = "source", zIndex = 2f))).isTrue()
    assertThat(selection.matches(HazeSourceMetadata(key = "source", zIndex = 1f))).isFalse()
    assertThat(selection.matches(HazeSourceMetadata(key = "other", zIndex = 2f))).isFalse()
  }
}
