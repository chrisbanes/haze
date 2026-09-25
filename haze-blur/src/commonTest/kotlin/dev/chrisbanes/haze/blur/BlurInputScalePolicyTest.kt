// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import kotlin.math.sqrt
import kotlin.test.Test

class BlurInputScalePolicyTest {

  @Test
  fun fixedQuality_interpolatesTotalPixelsAcrossSupportedRange() {
    for (step in 0..100) {
      val quality = step / 100f
      val scale = HazePerformanceMode.Fixed(quality).resolveBlurInputScale()
      assertThat(scale * scale).isCloseTo(0.25f + 0.75f * quality, 0.000001f)
    }
  }

  @Test
  fun progressiveLayeredRoute_requiresFullResolutionLinearGradient() {
    assertThat(
      shouldDrawProgressiveWithLayers(
        progressive = HazeProgressive.horizontalGradient(),
        inputScale = 1f,
      ),
    ).isTrue()
    assertThat(
      shouldDrawProgressiveWithLayers(
        progressive = HazeProgressive.horizontalGradient(),
        inputScale = sqrt(0.625f),
      ),
    ).isFalse()
    assertThat(
      shouldDrawProgressiveWithLayers(
        progressive = HazeProgressive.RadialGradient(),
        inputScale = 1f,
      ),
    ).isFalse()
  }

  @Test
  fun fixedModes_resolveDeterministicProfiles() {
    assertThat(HazePerformanceMode.Quality.resolveBlurInputScale()).isEqualTo(1f)
    assertThat(HazePerformanceMode.Balanced.resolveBlurInputScale()).isEqualTo(sqrt(0.625f))
    assertThat(HazePerformanceMode.Performance.resolveBlurInputScale()).isEqualTo(0.5f)
  }

  @Test
  fun fixedQualityFraction_usesTheCorrespondingProfile() {
    assertThat(HazePerformanceMode.Fixed(0.5f).resolveBlurInputScale())
      .isEqualTo(sqrt(0.625f))
  }

  @Test
  fun increasingFixedQuality_neverLowersTheResolvedProfile() {
    val profiles = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { qualityFraction ->
      HazePerformanceMode.Fixed(qualityFraction).resolveBlurInputScale()
    }

    assertThat(profiles).containsExactly(0.5f, sqrt(0.4375f), sqrt(0.625f), sqrt(0.8125f), 1f)
  }

  @Test
  @Suppress("DEPRECATION")
  fun adaptiveCompatibilityAlias_usesBalancedProfile() {
    assertThat(HazePerformanceMode.Adaptive.resolveBlurInputScale())
      .isEqualTo(HazePerformanceMode.Balanced.resolveBlurInputScale())
  }
}
