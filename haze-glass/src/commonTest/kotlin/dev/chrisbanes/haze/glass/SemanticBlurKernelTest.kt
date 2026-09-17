// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.test.Test

class SemanticBlurKernelTest {
  @Test
  fun radiusZero_isIdentity() {
    val plan = SemanticBlurPlan.create(1080, 1920, 0f)

    assertThat(plan.isIdentity).isTrue()
    assertThat(plan.horizontalKernel.centerWeight).isEqualTo(1f)
  }

  @Test
  fun subpixelRadii_areContinuousAndMonotonic() {
    val plans = listOf(0f, 0.01f, 0.1f, 0.5f, 1f)
      .map { SemanticBlurPlan.create(1080, 1920, it) }
    val variances = plans.map { it.horizontalKernel.variance }

    assertThat(plans.first().isIdentity).isTrue()
    variances.zipWithNext().forEach { (smaller, larger) ->
      assertThat(larger).isGreaterThan(smaller)
    }
    plans.drop(1).zip(variances.drop(1)).forEach { (plan, variance) ->
      assertThat(abs(variance - plan.sigmaPx * plan.sigmaPx)).isLessThanOrEqualTo(0.0001f)
    }
  }

  @Test
  fun wideUniformBlur_usesNativeGaussianWithoutBuildingASemanticKernel() {
    val plan = SemanticBlurPlan.create(1_080, 1_920, 84f)

    assertThat(plan.usesNativeWideBlur).isTrue()
    assertThat(plan.isIdentity).isFalse()
    assertThat(plan.horizontalKernel.taps.isEmpty()).isTrue()
    assertThat(plan.verticalKernel.taps.isEmpty()).isTrue()
  }

  @Test
  fun threshold_selectsNativeOnlyAboveTheBoundedSemanticKernel() {
    val threshold = SemanticBlurPlan.NATIVE_BLUR_RADIUS_THRESHOLD_PX

    assertThat(SemanticBlurPlan.create(1080, 1920, threshold).usesNativeWideBlur).isFalse()
    assertThat(SemanticBlurPlan.create(1080, 1920, threshold + 0.001f).usesNativeWideBlur)
      .isTrue()
  }

  @Test
  fun progressivePlan_keepsLegacyCapAndSemanticKernel() {
    val plan = SemanticBlurPlan.createForSigma(
      sampleWidth = 1080,
      sampleHeight = 1920,
      effectiveRadiusPx = 84f,
      sigmaPx = SemanticBlurKernel.radiusToSigma(84f),
      allowNativeWideBlur = false,
    )

    assertThat(plan.effectiveRadiusPx).isEqualTo(SemanticBlurKernel.MAX_PROGRESSIVE_RADIUS_PX)
    assertThat(plan.usesNativeWideBlur).isFalse()
    assertThat(plan.horizontalKernel.taps.size).isLessThanOrEqualTo(SemanticBlurKernel.MAX_TAP_PAIRS)
  }

  @Test
  fun smallPlan_tracksGaussianFrequencyResponse() {
    listOf(11f, SemanticBlurPlan.NATIVE_BLUR_RADIUS_THRESHOLD_PX).forEach { radius ->
      val sigma = SemanticBlurKernel.radiusToSigma(radius)
      val plan = SemanticBlurPlan.createForSigma(1080, 1920, radius, sigma, allowNativeWideBlur = false)
      listOf(0.25f, 0.5f, 1f, 2f).forEach { normalizedFrequency ->
        val actual = plan.kernelFrequencyResponse(normalizedFrequency / sigma)
        val reference = exp(-0.5f * normalizedFrequency * normalizedFrequency)

        assertThat(abs(actual - reference)).isLessThanOrEqualTo(0.03f)
      }
    }
  }

  @Test
  fun weights_areNormalizedAndFinite() {
    listOf(0.25f, 1f, 8f, 38.5f, 128f).forEach { radius ->
      val kernel = SemanticBlurKernel.create(radius)
      val total = kernel.centerWeight + 2f * kernel.taps.sumOf { it.weight.toDouble() }.toFloat()

      assertThat(abs(total - 1f)).isLessThanOrEqualTo(1e-5f)
      assertThat(kernel.centerWeight.isFinite()).isTrue()
      kernel.taps.forEach { tap ->
        assertThat(tap.offsetPx.isFinite()).isTrue()
        assertThat(tap.weight.isFinite()).isTrue()
        assertThat(tap.offsetPx).isGreaterThan(0f)
        assertThat(tap.weight).isGreaterThan(0f)
      }
    }
  }

  @Test
  fun tapCount_isBounded() {
    listOf(1f, 38.5f, 1_000f).forEach { radius ->
      assertThat(SemanticBlurKernel.create(radius).taps.size)
        .isLessThanOrEqualTo(SemanticBlurKernel.MAX_TAP_PAIRS)
    }
    assertThat(SemanticBlurKernel.MAX_TAP_PAIRS).isLessThanOrEqualTo(20)
  }

  @Test
  fun blurPlan_capsUnsupportedRadiiDeterministically() {
    val maximum = SemanticBlurPlan.create(640, 480, SemanticBlurKernel.MAX_SUPPORTED_RADIUS_PX)
    val beyondMaximum = SemanticBlurPlan.create(640, 480, 1_000f)

    assertThat(beyondMaximum).isEqualTo(maximum)
  }

  @Test
  fun spread_increasesMonotonicallyWithRadius() {
    val spreads = listOf(1f, 4f, 16f, 38.5f, 96f)
      .map { SemanticBlurKernel.create(it).variance }

    spreads.zipWithNext().forEach { (smaller, larger) ->
      assertThat(larger).isGreaterThan(smaller)
    }
  }

  @Test
  fun radiusToSigma_matchesSharedSemanticConversion() {
    assertThat(abs(SemanticBlurKernel.radiusToSigma(38.5f) - 22.727975f))
      .isLessThanOrEqualTo(1e-5f)
  }

  private fun SemanticBlurPlan.kernelFrequencyResponse(frequency: Float): Float =
    horizontalKernel.centerWeight + 2f * horizontalKernel.taps.sumOf {
      (it.weight * cos(frequency * it.offsetPx)).toDouble()
    }.toFloat()
}
