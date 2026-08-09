// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.unit.IntSize
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class GlassInputScalePolicyTest {

  @Test
  fun namedFixedModes_resolveValidatedProfiles() {
    val policy = GlassInputScalePolicy()

    assertThat(policy.resolve(HazePerformanceMode.Quality)).isEqualTo(1f)
    assertThat(policy.resolve(HazePerformanceMode.Balanced))
      .isCloseTo(sqrt(0.5f), 0.0001f)
    assertThat(policy.resolve(HazePerformanceMode.Performance)).isEqualTo(0.5f)
  }

  @Test
  fun fixedProfiles_areMonotonicAcrossAscendingQualityFractions() {
    val profiles = listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 1f)
      .map { GlassInputScalePolicy().resolve(HazePerformanceMode.Fixed(it)) }

    assertThat(profiles).isEqualTo(listOf(0.5f, 0.5f, sqrt(0.5f), sqrt(0.5f), 1f, 1f))
  }

  @Test
  fun adaptive_selectsProfileFromBalancedRetainedPixelWorkload() {
    assertThat(
      GlassInputScalePolicy().resolve(
        performanceMode = HazePerformanceMode.Adaptive,
        balancedPlan = retainedPlan(layerSize = IntSize(999, 500)),
      ),
    ).isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)
    assertThat(
      GlassInputScalePolicy().resolve(
        performanceMode = HazePerformanceMode.Adaptive,
        balancedPlan = retainedPlan(layerSize = IntSize(1000, 500)),
      ),
    ).isEqualTo(0.5f)
  }

  @Test
  fun adaptive_holdsPerformanceProfileUntilWorkloadExitsHysteresisMargin() {
    val policy = GlassInputScalePolicy()

    assertThat(policy.resolve(HazePerformanceMode.Adaptive, retainedPlan(IntSize(1000, 500))))
      .isEqualTo(0.5f)
    assertThat(policy.resolve(HazePerformanceMode.Adaptive, retainedPlan(IntSize(950, 500))))
      .isEqualTo(0.5f)
    assertThat(policy.resolve(HazePerformanceMode.Adaptive, retainedPlan(IntSize(874, 500))))
      .isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)
  }

  @Test
  fun fixedProfiles_areIndependentOfAdaptiveHistoryAndResetIt() {
    val policy = GlassInputScalePolicy()
    val boundaryNoise = retainedPlan(IntSize(950, 500))

    assertThat(policy.resolve(HazePerformanceMode.Adaptive, retainedPlan(IntSize(1000, 500))))
      .isEqualTo(0.5f)
    assertThat(policy.resolve(HazePerformanceMode.Quality)).isEqualTo(1f)
    assertThat(policy.resolve(HazePerformanceMode.Performance)).isEqualTo(0.5f)
    assertThat(policy.resolve(HazePerformanceMode.Adaptive, boundaryNoise))
      .isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)
  }

  @Test
  fun adaptiveAndEquivalentFixedFractionResolveSameProfile() {
    val adaptive = GlassInputScalePolicy().resolve(
      HazePerformanceMode.Adaptive,
      retainedPlan(IntSize(1000, 500)),
    )

    assertThat(adaptive).isEqualTo(
      GlassInputScalePolicy().resolve(HazePerformanceMode.Fixed(0f)),
    )
  }

  @Test
  fun adaptive_accumulatesRapidRetainedPixelUpdatesAndResetsAfterQuietPeriod() {
    val timeSource = TestTimeSource()
    val policy = GlassInputScalePolicy(timeSource)
    val halfMillionPixelPlan = retainedPlan(IntSize(1000, 500), layerCount = 1)

    policy.observeUpdate("frame-1")
    assertThat(policy.resolve(HazePerformanceMode.Adaptive, halfMillionPixelPlan))
      .isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)

    timeSource += 16.milliseconds
    policy.observeUpdate("frame-2")
    assertThat(policy.resolve(HazePerformanceMode.Adaptive, halfMillionPixelPlan))
      .isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)

    timeSource += 16.milliseconds
    policy.observeUpdate("frame-3")
    assertThat(policy.resolve(HazePerformanceMode.Adaptive, halfMillionPixelPlan))
      .isEqualTo(0.5f)

    timeSource += 250.milliseconds
    policy.observeUpdate("settled")
    assertThat(policy.resolve(HazePerformanceMode.Adaptive, halfMillionPixelPlan))
      .isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)
  }

  private fun retainedPlan(
    layerSize: IntSize,
    layerCount: Int = 3,
  ): GlassRetainedLayerPlan = GlassRetainedLayerPlan(
    List(layerCount) { GlassRetainedLayer(GlassRetainedLayerKind.Source, layerSize) },
  )
}
