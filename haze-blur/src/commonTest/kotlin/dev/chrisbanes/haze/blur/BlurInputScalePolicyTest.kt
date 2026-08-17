// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Size
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class BlurInputScalePolicyTest {

  @Test
  fun progressiveLayeredRoute_requiresFullResolutionLinearGradient() {
    assertThat(
      shouldDrawProgressiveWithLayers(
        progressive = HazeProgressive.horizontalGradient(),
        inputScale = BlurInputScalePolicy.NONE_SCALE,
      ),
    ).isEqualTo(true)
    assertThat(
      shouldDrawProgressiveWithLayers(
        progressive = HazeProgressive.horizontalGradient(),
        inputScale = BlurInputScalePolicy.BALANCED_SCALE,
      ),
    ).isEqualTo(false)
    assertThat(
      shouldDrawProgressiveWithLayers(
        progressive = HazeProgressive.RadialGradient(),
        inputScale = BlurInputScalePolicy.NONE_SCALE,
      ),
    ).isEqualTo(false)
  }

  @Test
  fun fixedModes_resolveDeterministicProfiles() {
    val policy = BlurInputScalePolicy()
    val smallWorkload = Size(1f, 1f)
    val largeWorkload = Size(
      width = BlurInputScalePolicy.AGGRESSIVE_AREA_PX,
      height = 1f,
    )

    assertThat(
      policy.resolve(
        HazePerformanceMode.Quality,
        blurRadiusPx = 1f,
        layerSize = smallWorkload,
      ),
    ).isEqualTo(1f)
    assertThat(
      policy.resolve(
        HazePerformanceMode.Balanced,
        blurRadiusPx = BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
        layerSize = largeWorkload,
      ),
    ).isEqualTo(0.8f)
    assertThat(
      policy.resolve(
        HazePerformanceMode.Performance,
        blurRadiusPx = 1f,
        layerSize = smallWorkload,
      ),
    ).isEqualTo(0.5f)
  }

  @Test
  fun fixedModes_areIndependentOfWorkload() {
    val policy = BlurInputScalePolicy()
    val largeWorkload = Size(
      width = BlurInputScalePolicy.AGGRESSIVE_AREA_PX,
      height = 1f,
    )

    assertThat(
      policy.resolve(HazePerformanceMode.Quality, BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX, largeWorkload),
    ).isEqualTo(1f)
    assertThat(
      policy.resolve(
        HazePerformanceMode.Performance,
        BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
        largeWorkload,
      ),
    ).isEqualTo(0.5f)
  }

  @Test
  fun fixedQualityFraction_usesTheCorrespondingProfile() {
    assertThat(
      BlurInputScalePolicy().resolve(
        performanceMode = HazePerformanceMode.Fixed(0.5f),
        blurRadiusPx = BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
        layerSize = Size(1000f, 1000f),
      ),
    ).isEqualTo(0.8f)
  }

  @Test
  fun increasingFixedQuality_neverLowersTheResolvedProfile() {
    val policy = BlurInputScalePolicy()
    val profiles = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { qualityFraction ->
      policy.resolve(
        performanceMode = HazePerformanceMode.Fixed(qualityFraction),
        blurRadiusPx = BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
        layerSize = Size(BlurInputScalePolicy.AGGRESSIVE_AREA_PX, 1f),
      )
    }

    assertThat(profiles).containsExactly(0.5f, 0.8f, 0.8f, 1f, 1f)
  }

  @Test
  fun adaptiveTiers_resolveThroughTheSameFixedProfiles() {
    val small = Size(1f, 1f)
    val balanced = Size(BlurInputScalePolicy.BALANCED_AREA_PX, 1f)
    val aggressive = Size(BlurInputScalePolicy.AGGRESSIVE_AREA_PX, 1f)

    assertThat(
      BlurInputScalePolicy().resolve(
        HazePerformanceMode.Adaptive,
        blurRadiusPx = 1f,
        layerSize = small,
      ),
    ).isEqualTo(
      BlurInputScalePolicy().resolve(HazePerformanceMode.Quality, 1f, small),
    )
    assertThat(
      BlurInputScalePolicy().resolve(
        HazePerformanceMode.Adaptive,
        blurRadiusPx = BlurInputScalePolicy.BALANCED_RADIUS_PX,
        layerSize = balanced,
      ),
    ).isEqualTo(
      BlurInputScalePolicy().resolve(
        HazePerformanceMode.Balanced,
        BlurInputScalePolicy.BALANCED_RADIUS_PX,
        balanced,
      ),
    )
    assertThat(
      BlurInputScalePolicy().resolve(
        HazePerformanceMode.Adaptive,
        blurRadiusPx = BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
        layerSize = aggressive,
      ),
    ).isEqualTo(
      BlurInputScalePolicy().resolve(
        HazePerformanceMode.Performance,
        BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
        aggressive,
      ),
    )
  }

  @Test
  fun automatic_accumulatesRapidAreaUpdatesAndResetsAfterQuietPeriod() {
    val timeSource = TestTimeSource()
    val policy = BlurInputScalePolicy(timeSource)
    val rapidWorkload = Size(width = 100_000f, height = 1f)

    policy.observeUpdate("frame-1")
    assertThat(
      policy.resolve(HazePerformanceMode.Adaptive, BlurInputScalePolicy.BALANCED_RADIUS_PX, rapidWorkload),
    ).isEqualTo(1f)

    timeSource += 16.milliseconds
    policy.observeUpdate("frame-2")
    assertThat(
      policy.resolve(HazePerformanceMode.Adaptive, BlurInputScalePolicy.BALANCED_RADIUS_PX, rapidWorkload),
    ).isEqualTo(1f)

    timeSource += 16.milliseconds
    policy.observeUpdate("frame-3")
    assertThat(
      policy.resolve(HazePerformanceMode.Adaptive, BlurInputScalePolicy.BALANCED_RADIUS_PX, rapidWorkload),
    ).isEqualTo(0.8f)

    timeSource += 250.milliseconds
    policy.observeUpdate("settled")
    assertThat(
      policy.resolve(HazePerformanceMode.Adaptive, BlurInputScalePolicy.BALANCED_RADIUS_PX, rapidWorkload),
    ).isEqualTo(1f)
  }

  @Test
  fun automatic_repeatedDrawsOfSameInputDoNotAccumulateWork() {
    val timeSource = TestTimeSource()
    val policy = BlurInputScalePolicy(timeSource)
    val rapidWorkload = Size(width = 100_000f, height = 1f)

    repeat(3) {
      policy.observeUpdate("stable-input")
      assertThat(
        policy.resolve(HazePerformanceMode.Adaptive, BlurInputScalePolicy.BALANCED_RADIUS_PX, rapidWorkload),
      ).isEqualTo(1f)
      timeSource += 16.milliseconds
    }
  }

  @Test
  fun ordinaryBlur_requiresRadiusAndAreaForEachTier() {
    val belowBalancedRadius = BlurInputScalePolicy.BALANCED_RADIUS_PX - 1f
    val belowBalancedArea = BlurInputScalePolicy.BALANCED_AREA_PX - 1f
    val belowAggressiveRadius = BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX - 1f
    val belowAggressiveArea = BlurInputScalePolicy.AGGRESSIVE_AREA_PX - 1f

    assertThat(BlurInputScalePolicy().auto(belowBalancedRadius, BlurInputScalePolicy.BALANCED_AREA_PX))
      .isEqualTo(1f)
    assertThat(BlurInputScalePolicy().auto(BlurInputScalePolicy.BALANCED_RADIUS_PX, belowBalancedArea))
      .isEqualTo(1f)
    assertThat(BlurInputScalePolicy().auto(belowAggressiveRadius, BlurInputScalePolicy.AGGRESSIVE_AREA_PX))
      .isEqualTo(0.8f)
    assertThat(BlurInputScalePolicy().auto(BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX, belowAggressiveArea))
      .isEqualTo(0.8f)
    assertThat(
      BlurInputScalePolicy().auto(
        BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
        BlurInputScalePolicy.AGGRESSIVE_AREA_PX,
      ),
    ).isEqualTo(0.5f)
  }

  @Test
  fun progressiveBlur_isCappedAtBalancedTier() {
    assertThat(
      BlurInputScalePolicy().auto(
        radiusPx = BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
        areaPx = BlurInputScalePolicy.AGGRESSIVE_AREA_PX,
        progressive = true,
      ),
    ).isEqualTo(0.8f)
  }

  @Test
  fun changingBlurMode_resetsAutomaticHysteresisHistory() {
    val policy = BlurInputScalePolicy()

    assertThat(
      policy.auto(
        radiusPx = BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
        areaPx = BlurInputScalePolicy.AGGRESSIVE_AREA_PX,
      ),
    ).isEqualTo(0.5f)
    assertThat(
      policy.auto(
        radiusPx = BlurInputScalePolicy.BALANCED_RADIUS_EXIT_PX,
        areaPx = BlurInputScalePolicy.BALANCED_AREA_EXIT_PX,
        progressive = true,
      ),
    ).isEqualTo(1f)
  }

  @Test
  fun increasingEitherWorkloadInput_neverSelectsLessAggressiveTier() {
    val radiusPolicy = BlurInputScalePolicy()
    val radiusSweep = listOf(
      0f,
      BlurInputScalePolicy.BALANCED_RADIUS_PX,
      BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
      BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX * 2f,
    ).map { radius ->
      radiusPolicy.auto(radius, BlurInputScalePolicy.AGGRESSIVE_AREA_PX)
    }
    val areaPolicy = BlurInputScalePolicy()
    val areaSweep = listOf(
      0f,
      BlurInputScalePolicy.BALANCED_AREA_PX,
      BlurInputScalePolicy.AGGRESSIVE_AREA_PX,
      BlurInputScalePolicy.AGGRESSIVE_AREA_PX * 2f,
    ).map { area ->
      areaPolicy.auto(BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX, area)
    }

    assertThat(radiusSweep).containsExactly(1f, 0.8f, 0.5f, 0.5f)
    assertThat(areaSweep).containsExactly(1f, 0.8f, 0.5f, 0.5f)
  }

  @Test
  fun hysteresis_holdsTierAcrossBoundaryNoise() {
    val policy = BlurInputScalePolicy()

    assertThat(
      policy.auto(
        BlurInputScalePolicy.BALANCED_RADIUS_PX,
        BlurInputScalePolicy.BALANCED_AREA_PX,
      ),
    ).isEqualTo(0.8f)
    assertThat(
      policy.auto(
        BlurInputScalePolicy.BALANCED_RADIUS_PX - 1f,
        BlurInputScalePolicy.BALANCED_AREA_PX - 1f,
      ),
    ).isEqualTo(0.8f)
    assertThat(
      policy.auto(
        BlurInputScalePolicy.BALANCED_RADIUS_EXIT_PX - 1f,
        BlurInputScalePolicy.BALANCED_AREA_EXIT_PX,
      ),
    ).isEqualTo(1f)
  }

  @Test
  fun explicitChoice_resetsAutomaticHysteresisHistory() {
    val policy = BlurInputScalePolicy()

    assertThat(
      policy.auto(
        BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
        BlurInputScalePolicy.AGGRESSIVE_AREA_PX,
      ),
    ).isEqualTo(0.5f)
    policy.resolve(
      performanceMode = HazePerformanceMode.Quality,
      blurRadiusPx = 0f,
      layerSize = Size.Zero,
    )

    assertThat(
      policy.auto(
        BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX - 1f,
        BlurInputScalePolicy.AGGRESSIVE_AREA_PX - 1f,
      ),
    ).isEqualTo(0.8f)
  }

  @Test
  fun reset_clearsAutomaticHysteresisHistory() {
    val policy = BlurInputScalePolicy()

    assertThat(
      policy.auto(
        BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
        BlurInputScalePolicy.AGGRESSIVE_AREA_PX,
      ),
    ).isEqualTo(0.5f)
    policy.reset()

    assertThat(
      policy.auto(
        BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX - 1f,
        BlurInputScalePolicy.AGGRESSIVE_AREA_PX - 1f,
      ),
    ).isEqualTo(0.8f)
  }

  private fun BlurInputScalePolicy.auto(
    radiusPx: Float,
    areaPx: Float,
    progressive: Boolean = false,
  ): Float = resolve(
    performanceMode = HazePerformanceMode.Adaptive,
    blurRadiusPx = radiusPx,
    layerSize = Size(areaPx, 1f),
    progressive = progressive,
  )
}
