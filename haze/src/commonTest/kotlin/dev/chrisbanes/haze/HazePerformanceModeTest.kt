// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class HazePerformanceModeTest {

  @Test
  fun default_pointsToAdaptive() {
    assertThat(HazePerformanceMode.Default).isSameInstanceAs(HazePerformanceMode.Adaptive)
  }

  @Test
  fun presets_useSpecifiedQualityFractions() {
    assertThat(HazePerformanceMode.Quality).isEqualTo(HazePerformanceMode.Fixed(1f))
    assertThat(HazePerformanceMode.Balanced).isEqualTo(HazePerformanceMode.Fixed(0.5f))
    assertThat(HazePerformanceMode.Performance).isEqualTo(HazePerformanceMode.Fixed(0f))
  }

  @Test
  fun fixed_retainsValidQualityFractions() {
    listOf(0f, 0.5f, 1f).forEach { qualityFraction ->
      assertThat(HazePerformanceMode.Fixed(qualityFraction).qualityFraction)
        .isEqualTo(qualityFraction)
    }
  }

  @Test
  fun fixed_rejectsInvalidQualityFractions() {
    listOf(
      -0.1f,
      Float.NaN,
      Float.NEGATIVE_INFINITY,
      Float.POSITIVE_INFINITY,
      1.1f,
    ).forEach { qualityFraction ->
      assertFailure { HazePerformanceMode.Fixed(qualityFraction) }
        .isInstanceOf<IllegalArgumentException>()
    }
  }
}
