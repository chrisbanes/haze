// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Size
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazeSampling
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class BlurInputScalePolicyTest {

  @Test
  fun explicitScales_areAuthoritative() {
    val policy = BlurInputScalePolicy()
    val largeWorkload = Size(
      width = BlurInputScalePolicy.AGGRESSIVE_AREA_PX,
      height = 1f,
    )

    assertThat(
      policy.resolve(HazeSampling.FullResolution, BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX, largeWorkload),
    ).isEqualTo(1f)
    assertThat(
      policy.resolve(
        HazeSampling.Fixed(0.25f),
        BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
        largeWorkload,
      ),
    ).isEqualTo(0.5f)
  }

  @Test
  fun fixedHalfPixels_usesSquareRootPerDimension() {
    assertThat(
      BlurInputScalePolicy().resolve(
        sampling = HazeSampling.Fixed(0.5f),
        blurRadiusPx = BlurInputScalePolicy.AGGRESSIVE_RADIUS_PX,
        layerSize = Size(1000f, 1000f),
      ),
    ).isCloseTo(sqrt(0.5f), 0.0001f)
  }

  @Test
  fun automatic_accumulatesRapidAreaUpdatesAndResetsAfterQuietPeriod() {
    val timeSource = TestTimeSource()
    val policy = BlurInputScalePolicy(timeSource)
    val rapidWorkload = Size(width = 100_000f, height = 1f)

    policy.observeUpdate("frame-1")
    assertThat(
      policy.resolve(HazeSampling.Adaptive, BlurInputScalePolicy.BALANCED_RADIUS_PX, rapidWorkload),
    ).isEqualTo(1f)

    timeSource += 16.milliseconds
    policy.observeUpdate("frame-2")
    assertThat(
      policy.resolve(HazeSampling.Adaptive, BlurInputScalePolicy.BALANCED_RADIUS_PX, rapidWorkload),
    ).isEqualTo(1f)

    timeSource += 16.milliseconds
    policy.observeUpdate("frame-3")
    assertThat(
      policy.resolve(HazeSampling.Adaptive, BlurInputScalePolicy.BALANCED_RADIUS_PX, rapidWorkload),
    ).isEqualTo(0.8f)

    timeSource += 250.milliseconds
    policy.observeUpdate("settled")
    assertThat(
      policy.resolve(HazeSampling.Adaptive, BlurInputScalePolicy.BALANCED_RADIUS_PX, rapidWorkload),
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
        policy.resolve(HazeSampling.Adaptive, BlurInputScalePolicy.BALANCED_RADIUS_PX, rapidWorkload),
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
      sampling = HazeSampling.FullResolution,
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
    sampling = HazeSampling.Adaptive,
    blurRadiusPx = radiusPx,
    layerSize = Size(areaPx, 1f),
    progressive = progressive,
  )
}
