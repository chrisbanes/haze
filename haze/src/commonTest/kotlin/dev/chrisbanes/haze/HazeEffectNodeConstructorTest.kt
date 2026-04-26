// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class)
class HazeEffectNodeConstructorTest {

  @Test
  fun constructor_supportsStateOnlyCallSite() {
    val state = HazeState()
    val node = HazeEffectNode(state = state)

    assertThat(node.state).isEqualTo(state)
    assertThat(node.block).isEqualTo(null)
  }

  @Test
  fun constructor_supportsBlockOnlyCallSite() {
    val block: HazeEffectScope.() -> Unit = {}
    val node = HazeEffectNode(block = block)

    assertThat(node.state).isEqualTo(null)
    assertThat(node.block).isEqualTo(block)
  }
}
