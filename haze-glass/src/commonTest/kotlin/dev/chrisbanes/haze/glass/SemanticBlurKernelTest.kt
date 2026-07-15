// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.unit.IntSize
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.test.Test

class SemanticBlurKernelTest {

  @Test
  fun radiusZero_isIdentity() {
    val kernel = SemanticBlurKernel.createForSigma(0f)

    assertThat(kernel.centerWeight).isEqualTo(1f)
    assertThat(kernel.taps.isEmpty()).isTrue()
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
  fun scaleSelection_usesExactRoundedWorkingDimensions() {
    assertThat(SemanticBlurPlan.create(101, 99, 11f).workingSize).isEqualTo(IntSize(101, 99))
    assertThat(SemanticBlurPlan.create(101, 99, 22f).workingSize).isEqualTo(IntSize(101, 99))
    assertThat(SemanticBlurPlan.create(101, 99, 38.5f).workingSize).isEqualTo(IntSize(51, 50))
  }

  @Test
  fun reducedScalePlan_accountsForPrefilterVariance() {
    val plan = SemanticBlurPlan.create(1080, 1920, 38.5f)
    val totalVariance = plan.resamplingVariancePx2 +
      plan.horizontalKernel.variance / (plan.scaleFactor * plan.scaleFactor)

    assertThat(plan.requiresPrefilter).isTrue()
    assertThat(abs(totalVariance / (plan.sigmaPx * plan.sigmaPx) - 1f))
      .isLessThanOrEqualTo(0.03f)
  }

  @Test
  fun prefilterRejectsNyquistStripesBeforeDecimation() {
    assertThat(abs(SemanticBlurPrefilter.transfer(PI.toFloat()))).isLessThanOrEqualTo(1e-6f)
    assertThat(SemanticBlurPrefilter.transfer((PI / 2.0).toFloat()))
      .isLessThanOrEqualTo(0.51f)
  }

  @Test
  fun scaleTransitionHasBoundedFrequencyResponseJump() {
    val threshold = SemanticBlurPlan.DOWNSAMPLE_RADIUS_THRESHOLD_PX
    val below = SemanticBlurPlan.create(1080, 1920, threshold - 0.001f)
    val at = SemanticBlurPlan.create(1080, 1920, threshold)
    val above = SemanticBlurPlan.create(1080, 1920, threshold + 0.001f)

    assertThat(below.scaleFactor).isEqualTo(1f)
    assertThat(at.scaleFactor).isEqualTo(1f)
    assertThat(above.scaleFactor).isEqualTo(0.5f)
    listOf(0.1f, 0.25f, 0.5f, 1f).forEach { normalizedFrequency ->
      val belowResponse = below.sourceFrequencyResponse(normalizedFrequency / below.sigmaPx)
      val aboveResponse = above.sourceFrequencyResponse(normalizedFrequency / above.sigmaPx)
      assertThat(abs(aboveResponse - belowResponse)).isLessThanOrEqualTo(0.02f)
    }
  }

  private fun SemanticBlurPlan.sourceFrequencyResponse(frequency: Float): Float {
    val kernelResponse = horizontalKernel.centerWeight + 2f * horizontalKernel.taps.sumOf {
      (it.weight * cos(frequency / scaleFactor * it.offsetPx)).toDouble()
    }.toFloat()
    return kernelResponse * if (requiresPrefilter) {
      SemanticBlurPrefilter.transfer(frequency)
    } else {
      1f
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
  fun blurPlan_preservesRadiusWithBoundedKernelAcrossLargeRadii() {
    listOf(0f, 11f, 22f, 38.5f, SemanticBlurKernel.MAX_SUPPORTED_RADIUS_PX).forEach { radius ->
      val plan = SemanticBlurPlan.create(
        sampleWidth = 1080,
        sampleHeight = 1920,
        radiusPx = radius,
      )
      val expectedVariance = SemanticBlurKernel.radiusToSigma(radius).let { it * it }
      val actualVariance = plan.horizontalKernel.variance /
        (plan.scaleFactor * plan.scaleFactor)

      if (radius == 0f) {
        assertThat(plan.isIdentity).isTrue()
      } else {
        assertThat(abs(actualVariance / expectedVariance - 1f)).isLessThanOrEqualTo(0.1f)
      }
      assertThat(plan.horizontalKernel.taps.size).isLessThanOrEqualTo(20)
      assertThat(plan.verticalKernel.taps.size).isLessThanOrEqualTo(20)
    }
  }

  @Test
  fun blurPlan_tracksHighResolutionGaussianFrequencyResponse() {
    listOf(11f, 22f, 38.5f, SemanticBlurKernel.MAX_SUPPORTED_RADIUS_PX).forEach { radius ->
      val sigma = SemanticBlurKernel.radiusToSigma(radius)
      val plan = SemanticBlurPlan.create(1080, 1920, radius)
      val kernel = plan.horizontalKernel
      listOf(0.25f, 0.5f, 1f, 2f).forEach { normalizedFrequency ->
        val frequency = normalizedFrequency / (sigma * plan.scaleFactor)
        val onePass = kernel.centerWeight + 2f * kernel.taps.sumOf {
          (it.weight * cos(frequency * it.offsetPx)).toDouble()
        }.toFloat()
        val actual = onePass
        val reference = exp(-0.5f * normalizedFrequency * normalizedFrequency)

        assertThat(abs(actual - reference)).isLessThanOrEqualTo(0.03f)
      }
    }
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
  fun spread_approximatesConfiguredGaussianSigma() {
    listOf(8f, 38.5f, 128f).forEach { radius ->
      val sigma = SemanticBlurKernel.radiusToSigma(radius)
      val relativeError = abs(SemanticBlurKernel.create(radius).variance / (sigma * sigma) - 1f)

      assertThat(relativeError).isLessThanOrEqualTo(0.08f)
    }
  }

  @Test
  fun radiusToSigma_matchesSharedSemanticConversion() {
    assertThat(abs(SemanticBlurKernel.radiusToSigma(38.5f) - 22.727975f))
      .isLessThanOrEqualTo(1e-5f)
  }
}
