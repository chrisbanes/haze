// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import kotlin.test.Test

class HazeSamplingTest {

  @Test
  fun policies_areDistinctAndFixedRetainsScale() {
    assertThat(HazeSampling.Default).isNotEqualTo(HazeSampling.FullResolution)
    assertThat(HazeSampling.Default).isNotEqualTo(HazeSampling.Adaptive)
    assertThat(HazeSampling.Fixed(0.5f).scale).isEqualTo(0.5f)
  }

  @Test
  fun fixed_rejectsInvalidScales() {
    listOf(
      0f,
      -0.1f,
      Float.NaN,
      Float.POSITIVE_INFINITY,
      1.1f,
    ).forEach { scale ->
      assertFailure { HazeSampling.Fixed(scale) }
        .isInstanceOf<IllegalArgumentException>()
    }
  }
}
