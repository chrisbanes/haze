// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.unit.IntSize
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazeSampling
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class GlassInputScalePolicyTest {

  @Test
  fun fixedHalfPixels_usesSquareRootPerDimension() {
    assertThat(GlassInputScalePolicy().resolve(HazeSampling.Fixed(0.5f)))
      .isCloseTo(sqrt(0.5f), 0.0001f)
  }

  @Test
  fun adaptive_selectsTierFromBalancedRetainedPixelWorkload() {
    assertThat(
      GlassInputScalePolicy().resolve(
        sampling = HazeSampling.Adaptive,
        balancedPlan = retainedPlan(layerSize = IntSize(999, 500)),
      ),
    ).isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)
    assertThat(
      GlassInputScalePolicy().resolve(
        sampling = HazeSampling.Adaptive,
        balancedPlan = retainedPlan(layerSize = IntSize(1000, 500)),
      ),
    ).isEqualTo(0.5f)
  }

  @Test
  fun adaptive_holdsAggressiveTierUntilWorkloadExitsHysteresisMargin() {
    val policy = GlassInputScalePolicy()

    assertThat(
      policy.resolve(HazeSampling.Adaptive, retainedPlan(IntSize(1000, 500))),
    ).isEqualTo(0.5f)
    assertThat(
      policy.resolve(HazeSampling.Adaptive, retainedPlan(IntSize(950, 500))),
    ).isEqualTo(0.5f)
    assertThat(
      policy.resolve(HazeSampling.Adaptive, retainedPlan(IntSize(874, 500))),
    ).isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)
  }

  @Test
  fun explicitPolicies_areAuthoritativeAndResetAdaptiveHistory() {
    val policy = GlassInputScalePolicy()
    val boundaryNoise = retainedPlan(IntSize(950, 500))

    assertThat(
      policy.resolve(HazeSampling.Adaptive, retainedPlan(IntSize(1000, 500))),
    ).isEqualTo(0.5f)
    assertThat(policy.resolve(HazeSampling.FullResolution)).isEqualTo(1f)
    assertThat(policy.resolve(HazeSampling.Fixed(0.25f))).isEqualTo(0.5f)
    assertThat(policy.resolve(HazeSampling.Adaptive, boundaryNoise))
      .isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)
  }

  @Test
  fun adaptive_accumulatesRapidRetainedPixelUpdatesAndResetsAfterQuietPeriod() {
    val timeSource = TestTimeSource()
    val policy = GlassInputScalePolicy(timeSource)
    val halfMillionPixelPlan = retainedPlan(
      layerSize = IntSize(1000, 500),
      layerCount = 1,
    )

    policy.observeUpdate("frame-1")
    assertThat(policy.resolve(HazeSampling.Adaptive, halfMillionPixelPlan))
      .isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)

    timeSource += 16.milliseconds
    policy.observeUpdate("frame-2")
    assertThat(policy.resolve(HazeSampling.Adaptive, halfMillionPixelPlan))
      .isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)

    timeSource += 16.milliseconds
    policy.observeUpdate("frame-3")
    assertThat(policy.resolve(HazeSampling.Adaptive, halfMillionPixelPlan)).isEqualTo(0.5f)

    timeSource += 250.milliseconds
    policy.observeUpdate("settled")
    assertThat(policy.resolve(HazeSampling.Adaptive, halfMillionPixelPlan))
      .isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)
  }

  @Test
  fun adaptive_repeatedDrawsOfSameRetainedInputDoNotAccumulateWork() {
    val timeSource = TestTimeSource()
    val policy = GlassInputScalePolicy(timeSource)
    val halfMillionPixelPlan = retainedPlan(
      layerSize = IntSize(1000, 500),
      layerCount = 1,
    )

    repeat(3) {
      policy.observeUpdate("stable-input")
      assertThat(policy.resolve(HazeSampling.Adaptive, halfMillionPixelPlan))
        .isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)
      timeSource += 16.milliseconds
    }
  }

  private fun retainedPlan(
    layerSize: IntSize,
    layerCount: Int = 3,
  ): GlassRetainedLayerPlan = GlassRetainedLayerPlan(
    List(layerCount) { GlassRetainedLayer(GlassRetainedLayerKind.Source, layerSize) },
  )
}
