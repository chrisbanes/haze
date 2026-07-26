// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isNotEqualTo
import kotlin.test.Test

class HazeInputScaleTest {

  @Test
  fun default_isDistinctFromExplicitNone() {
    assertThat(HazeInputScale.Default).isNotEqualTo(HazeInputScale.None)
  }
}
