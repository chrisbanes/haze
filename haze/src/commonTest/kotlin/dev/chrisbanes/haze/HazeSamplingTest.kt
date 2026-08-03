// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class HazeSamplingTest {

  @Test
  fun default_pointsToAdaptive() {
    assertThat(HazeSampling.Default).isSameInstanceAs(HazeSampling.Adaptive)
  }

  @Test
  fun fixed_retainsPixelFraction() {
    assertThat(HazeSampling.Fixed(0.5f).pixelFraction).isEqualTo(0.5f)
  }

  @Test
  fun fixed_rejectsInvalidPixelFractions() {
    listOf(
      0f,
      -0.1f,
      Float.NaN,
      Float.NEGATIVE_INFINITY,
      Float.POSITIVE_INFINITY,
      1.1f,
    ).forEach { pixelFraction ->
      assertFailure { HazeSampling.Fixed(pixelFraction) }
        .isInstanceOf<IllegalArgumentException>()
    }
  }
}
