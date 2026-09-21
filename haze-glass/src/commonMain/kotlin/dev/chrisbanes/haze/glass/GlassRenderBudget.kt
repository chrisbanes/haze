// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.unit.IntSize
import dev.chrisbanes.haze.Poko

internal const val MAX_GLASS_LAYER_DIMENSION_PX: Int = 4096
internal const val MAX_GLASS_RETAINED_PIXELS: Long = 16_777_216L
internal const val MIN_AUTOMATIC_GLASS_INPUT_SCALE: Float = 0.25f
private const val GLASS_BUDGET_SEARCH_ITERATIONS: Int = 16

internal enum class GlassRetainedLayerKind {
  Source,
  BlurHorizontal,
  Blurred,
  DepthMixed,
  Optical,
  RefractionDetail,
  RefractionDetailCoverage,
  RefractionComposite,
  Rim,
  InteractionOptical,
  InteractionDetail,
  InteractionDetailCoverage,
  InteractionComposite,
  InteractionLighting,
  BaseCoverage,
  GroupComposite,
}

internal data class GlassRetainedLayer(
  val kind: GlassRetainedLayerKind,
  val size: IntSize,
)

internal fun IntSize.fitsGlassLayerBudget(): Boolean =
  width in 1..MAX_GLASS_LAYER_DIMENSION_PX &&
    height in 1..MAX_GLASS_LAYER_DIMENSION_PX &&
    width.toLong() * height.toLong() <= MAX_GLASS_RETAINED_PIXELS

@Poko
internal class GlassRetainedLayerPlan(
  val layers: List<GlassRetainedLayer>,
  val allowsEmpty: Boolean = false,
) {
  fun retainedPixelCountOrNull(): Long? {
    var total = 0L
    for (layer in layers) {
      val width = layer.size.width
      val height = layer.size.height
      if (width <= 0 || height <= 0) return null
      val pixels = width.toLong() * height.toLong()
      if (Long.MAX_VALUE - total < pixels) return null
      total += pixels
    }
    return total
  }

  fun fitsGlassRenderBudget(): Boolean =
    (allowsEmpty || layers.isNotEmpty()) &&
      layers.all { it.size.fitsGlassLayerBudget() } &&
      (retainedPixelCountOrNull()?.let { it <= MAX_GLASS_RETAINED_PIXELS } == true)
}

internal enum class GlassRenderBudgetFallbackReason {
  InvalidGeometry,
  ExceedsLimits,
}

internal sealed interface GlassRenderBudgetDecision {
  data class Runtime(
    val scaleFactor: Float,
    val plan: GlassRetainedLayerPlan,
  ) : GlassRenderBudgetDecision

  data class Fallback(
    val reason: GlassRenderBudgetFallbackReason,
  ) : GlassRenderBudgetDecision
}

internal fun resolveGlassRenderBudget(
  requestedScale: Float,
  requestedPlan: GlassRetainedLayerPlan? = null,
  buildPlan: (Float) -> GlassRetainedLayerPlan,
): GlassRenderBudgetDecision {
  if (!requestedScale.isFinite() || requestedScale <= 0f) {
    return GlassRenderBudgetDecision.Fallback(GlassRenderBudgetFallbackReason.InvalidGeometry)
  }

  val resolvedRequestedPlan = requestedPlan ?: buildPlan(requestedScale)
  if (resolvedRequestedPlan.fitsGlassRenderBudget()) {
    return GlassRenderBudgetDecision.Runtime(requestedScale, resolvedRequestedPlan)
  }
  if (resolvedRequestedPlan.isInvalidGeometry()) {
    return GlassRenderBudgetDecision.Fallback(GlassRenderBudgetFallbackReason.InvalidGeometry)
  }

  val minimumScale = minOf(requestedScale, MIN_AUTOMATIC_GLASS_INPUT_SCALE)
  val minimumPlan = buildPlan(minimumScale)

  // Uniform blur changes once, from the full semantic layer pair to a native Gaussian output.
  // Search each side separately because layer count makes the overall budget non-monotone.
  val decisions = if (minimumPlan.blurTopology() == resolvedRequestedPlan.blurTopology()) {
    listOf(
      searchSafeScaleInterval(minimumScale, minimumPlan, requestedScale, resolvedRequestedPlan, buildPlan),
    )
  } else {
    val transition = findBlurTopologyTransition(
      minimumScale,
      minimumPlan,
      requestedScale,
      resolvedRequestedPlan,
      buildPlan,
    )
    listOf(
      searchSafeScaleInterval(minimumScale, minimumPlan, transition.belowScale, transition.belowPlan, buildPlan),
      searchSafeScaleInterval(transition.aboveScale, transition.abovePlan, requestedScale, resolvedRequestedPlan, buildPlan),
    )
  }
  return decisions.filterNotNull()
    .maxByOrNull { it.scaleFactor }
    ?: minimumPlan.fallbackDecision()
}

private data class BlurTopologyTransition(
  val belowScale: Float,
  val belowPlan: GlassRetainedLayerPlan,
  val aboveScale: Float,
  val abovePlan: GlassRetainedLayerPlan,
)

private fun findBlurTopologyTransition(
  belowScale: Float,
  belowPlan: GlassRetainedLayerPlan,
  aboveScale: Float,
  abovePlan: GlassRetainedLayerPlan,
  buildPlan: (Float) -> GlassRetainedLayerPlan,
): BlurTopologyTransition {
  var lowerBits = belowScale.toBits()
  var lowerPlan = belowPlan
  val lowerTopology = belowPlan.blurTopology()
  var upperBits = aboveScale.toBits()
  var upperPlan = abovePlan
  while (upperBits - lowerBits > 1) {
    val candidateBits = lowerBits + (upperBits - lowerBits) / 2
    val candidatePlan = buildPlan(Float.fromBits(candidateBits))
    if (candidatePlan.blurTopology() != lowerTopology) {
      upperBits = candidateBits
      upperPlan = candidatePlan
    } else {
      lowerBits = candidateBits
      lowerPlan = candidatePlan
    }
  }
  return BlurTopologyTransition(
    belowScale = Float.fromBits(lowerBits),
    belowPlan = lowerPlan,
    aboveScale = Float.fromBits(upperBits),
    abovePlan = upperPlan,
  )
}

private fun searchSafeScaleInterval(
  safeScale: Float,
  safePlan: GlassRetainedLayerPlan,
  unsafeScale: Float,
  unsafePlan: GlassRetainedLayerPlan,
  buildPlan: (Float) -> GlassRetainedLayerPlan,
): GlassRenderBudgetDecision.Runtime? {
  if (!safePlan.fitsGlassRenderBudget()) return null
  if (unsafePlan.fitsGlassRenderBudget()) {
    return GlassRenderBudgetDecision.Runtime(unsafeScale, unsafePlan)
  }

  var selectedScale = safeScale
  var selectedPlan = safePlan
  var rejectedScale = unsafeScale
  repeat(GLASS_BUDGET_SEARCH_ITERATIONS) {
    val candidateScale = (selectedScale + rejectedScale) / 2f
    val candidatePlan = buildPlan(candidateScale)
    if (candidatePlan.fitsGlassRenderBudget()) {
      selectedScale = candidateScale
      selectedPlan = candidatePlan
    } else {
      rejectedScale = candidateScale
    }
  }
  return GlassRenderBudgetDecision.Runtime(selectedScale, selectedPlan)
}

private enum class GlassBlurTopology {
  Full,
  Native,
  None,
}

private fun GlassRetainedLayerPlan.blurTopology(): GlassBlurTopology = when {
  layers.none { it.kind == GlassRetainedLayerKind.Blurred } -> GlassBlurTopology.None
  layers.any { it.kind == GlassRetainedLayerKind.BlurHorizontal } -> GlassBlurTopology.Full
  else -> GlassBlurTopology.Native
}

private fun GlassRetainedLayerPlan.fallbackDecision(): GlassRenderBudgetDecision.Fallback =
  GlassRenderBudgetDecision.Fallback(
    if (isInvalidGeometry()) {
      GlassRenderBudgetFallbackReason.InvalidGeometry
    } else {
      GlassRenderBudgetFallbackReason.ExceedsLimits
    },
  )

private fun GlassRetainedLayerPlan.isInvalidGeometry(): Boolean =
  !allowsEmpty && layers.isEmpty() || retainedPixelCountOrNull() == null
