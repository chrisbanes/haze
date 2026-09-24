// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.math.sqrt
import kotlin.test.Test

class GlassInputScalePolicyTest {

  @Test
  fun fixedQuality_interpolatesTotalPixelsAcrossSupportedRange() {
    for (step in 0..100) {
      val quality = step / 100f
      val scale = GlassInputScalePolicy().resolve(HazePerformanceMode.Fixed(quality))
      assertThat(scale * scale).isCloseTo(0.25f + 0.75f * quality, 0.000001f)
    }
  }

  @Test
  fun namedFixedModes_resolveValidatedProfiles() {
    val policy = GlassInputScalePolicy()

    assertThat(policy.resolve(HazePerformanceMode.Quality)).isEqualTo(1f)
    assertThat(policy.resolve(HazePerformanceMode.Balanced))
      .isCloseTo(sqrt(0.625f), 0.0001f)
    assertThat(policy.resolve(HazePerformanceMode.Performance)).isEqualTo(0.5f)
  }

  @Test
  fun fixedProfiles_areMonotonicAcrossAscendingQualityFractions() {
    val profiles = listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 1f)
      .map { GlassInputScalePolicy().resolve(HazePerformanceMode.Fixed(it)) }

    assertThat(profiles).containsExactly(0.5f, sqrt(0.325f), sqrt(0.4375f), sqrt(0.625f), sqrt(0.8125f), 1f)
  }

  @Test
  @Suppress("DEPRECATION")
  fun adaptiveCompatibilityAlias_usesBalancedProfile() {
    assertThat(GlassInputScalePolicy().resolve(HazePerformanceMode.Adaptive))
      .isEqualTo(GlassInputScalePolicy().resolve(HazePerformanceMode.Balanced))
  }
}
