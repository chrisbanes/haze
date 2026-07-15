// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A bounded, positive half-kernel; every tap is sampled at both signs. */
internal data class SemanticBlurKernel(
  val centerWeight: Float,
  val taps: List<Tap>,
) {
  data class Tap(
    val offsetPx: Float,
    val weight: Float,
  )

  val variance: Float
    get() = taps.sumOf { 2.0 * it.weight * it.offsetPx * it.offsetPx }.toFloat()

  companion object {
    /** At most 41 samples per pass (center + 20 symmetric pairs). */
    const val MAX_TAP_PAIRS: Int = 20
    const val MAX_SUPPORTED_RADIUS_PX: Float = 38.5f

    private const val SIGMA_SUPPORT: Float = 3f
    private const val MAX_INTEGRATION_SAMPLES_PER_TAP: Int = 8

    fun radiusToSigma(radiusPx: Float): Float =
      radiusPx * 0.57735f + 0.5f * radiusPx.coerceIn(0f, 1f)

    fun create(radiusPx: Float): SemanticBlurKernel = when {
      !radiusPx.isFinite() || radiusPx <= 0f -> SemanticBlurKernel(1f, emptyList())
      else -> createForSigma(radiusToSigma(radiusPx))
    }

    fun createForSigma(sigmaPx: Float): SemanticBlurKernel {
      if (!sigmaPx.isFinite() || sigmaPx <= 0f) return SemanticBlurKernel(1f, emptyList())
      if (sigmaPx <= 0.5f) {
        val variance = sigmaPx * sigmaPx
        return SemanticBlurKernel(
          centerWeight = 1f - variance,
          taps = listOf(Tap(offsetPx = 1f, weight = variance * 0.5f)),
        )
      }

      val support = ceil((sigmaPx * SIGMA_SUPPORT).toDouble()).toInt().coerceAtLeast(1)
      // Each group is replaced by its Gaussian-weighted centroid. Groups of two are exact under
      // bilinear sampling; wider groups are a bounded quadrature approximation.
      val samplesPerTap = maxOf(2, ceil(support / MAX_TAP_PAIRS.toDouble()).toInt())
      val center = gaussianWeight(0.0, sigmaPx)
      val unnormalized = buildList {
        var start = 1
        while (start <= support) {
          val end = minOf(start + samplesPerTap - 1, support)
          val sampleCount = minOf(end - start + 1, MAX_INTEGRATION_SAMPLES_PER_TAP)
          val sampleWidth = (end - start + 1).toDouble() / sampleCount
          var weight = 0.0
          var weightedOffset = 0.0
          repeat(sampleCount) { index ->
            val offset = start - 0.5 + (index + 0.5) * sampleWidth
            val sampleWeight = gaussianWeight(offset, sigmaPx) * sampleWidth
            weight += sampleWeight
            weightedOffset += offset * sampleWeight
          }
          add(Tap((weightedOffset / weight).toFloat(), weight.toFloat()))
          start = end + 1
        }
      }
      val normalization = center + 2.0 * unnormalized.sumOf { it.weight.toDouble() }
      val normalizedKernel = SemanticBlurKernel(
        centerWeight = (center / normalization).toFloat(),
        taps = unnormalized.map { it.copy(weight = (it.weight / normalization).toFloat()) },
      )
      if (sigmaPx <= 2f) {
        val offsetScale = sigmaPx / sqrt(normalizedKernel.variance)
        return normalizedKernel.copy(
          taps = normalizedKernel.taps.map { it.copy(offsetPx = it.offsetPx * offsetScale) },
        )
      }
      return normalizedKernel
    }

    private fun gaussianWeight(offset: Double, sigma: Float): Double {
      val normalized = offset / sigma
      return exp(-0.5 * normalized * normalized)
    }
  }
}

internal data class SemanticBlurPlan(
  val sampleSize: IntSize,
  val workingSize: IntSize,
  val effectiveRadiusPx: Float,
  val sigmaPx: Float,
  val scaleFactor: Float,
  val resamplingVariancePx2: Float,
  val horizontalKernel: SemanticBlurKernel,
  val verticalKernel: SemanticBlurKernel,
) {
  val isIdentity: Boolean get() = horizontalKernel.taps.isEmpty() && verticalKernel.taps.isEmpty()
  val requiresPrefilter: Boolean get() = scaleFactor < 1f

  companion object {
    const val DOWNSAMPLE_RADIUS_THRESHOLD_PX: Float =
      (SemanticBlurKernel.MAX_TAP_PAIRS * 2f / 3f - 0.5f) / 0.57735f

    fun create(sampleWidth: Int, sampleHeight: Int, radiusPx: Float): SemanticBlurPlan {
      val effectiveRadius = effectiveSemanticBlurRadiusPx(radiusPx)
      val sigma = if (effectiveRadius > 0f) SemanticBlurKernel.radiusToSigma(effectiveRadius) else 0f
      return createForSigma(sampleWidth, sampleHeight, effectiveRadius, sigma)
    }

    fun createForSigma(
      sampleWidth: Int,
      sampleHeight: Int,
      effectiveRadiusPx: Float,
      sigmaPx: Float,
      allowMultiscale: Boolean = true,
    ): SemanticBlurPlan {
      require(sampleWidth > 0 && sampleHeight > 0)
      // The explicit source-space low-pass makes this deterministic power-of-two decimation safe.
      val scale = if (allowMultiscale && effectiveRadiusPx > DOWNSAMPLE_RADIUS_THRESHOLD_PX) {
        0.5f
      } else {
        1f
      }
      val resamplingVariance = if (scale < 1f) SemanticBlurPrefilter.TOTAL_VARIANCE_PX2 else 0f
      val workingSize = IntSize(
        width = (sampleWidth * scale).roundToInt().coerceAtLeast(1),
        height = (sampleHeight * scale).roundToInt().coerceAtLeast(1),
      )
      val residualSigma = sqrt(
        (sigmaPx * sigmaPx - resamplingVariance).coerceAtLeast(0f),
      )
      val kernel = SemanticBlurKernel.createForSigma(residualSigma * scale)
      return SemanticBlurPlan(
        sampleSize = IntSize(sampleWidth, sampleHeight),
        workingSize = workingSize,
        effectiveRadiusPx = effectiveRadiusPx,
        sigmaPx = sigmaPx,
        scaleFactor = scale,
        resamplingVariancePx2 = resamplingVariance,
        horizontalKernel = kernel,
        verticalKernel = kernel,
      )
    }
  }
}

internal object SemanticBlurPrefilter {
  /** Variance per axis of the explicit [1, 2, 1] / 4 source-space kernel. */
  const val FILTER_VARIANCE_PX2: Float = 0.5f

  /** Variance per axis of the exact 2:1 bilinear center sampling that follows the prefilter. */
  const val BILINEAR_VARIANCE_PX2: Float = 0.25f

  const val TOTAL_VARIANCE_PX2: Float = FILTER_VARIANCE_PX2 + BILINEAR_VARIANCE_PX2

  fun transfer(frequencyRadiansPerPx: Float): Float =
    kotlin.math.cos(frequencyRadiansPerPx * 0.5f).let { halfFrequencyCosine ->
      halfFrequencyCosine * halfFrequencyCosine * halfFrequencyCosine
    }
}
