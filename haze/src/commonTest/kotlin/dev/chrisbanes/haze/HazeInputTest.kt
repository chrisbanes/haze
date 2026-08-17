// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
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
  fun sourceSelection_whereExposesStableSourceMetadata() {
    val acceptsSourceMetadata: (HazeSourceMetadata) -> Boolean = { metadata ->
      metadata.key == "source" && metadata.zIndex == 2f
    }
    val selection = HazeSourceSelection.All.where(acceptsSourceMetadata)

    assertThat(selection).isNotEqualTo(HazeSourceSelection.All)
  }
}
