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
  BlurPrefilter,
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

  val crossesBlurDownsampleThreshold =
    !minimumPlan.hasBlurPrefilter() && resolvedRequestedPlan.hasBlurPrefilter()
  if (!crossesBlurDownsampleThreshold) {
    return searchSafeScaleInterval(
      safeScale = minimumScale,
      safePlan = minimumPlan,
      unsafeScale = requestedScale,
      unsafePlan = resolvedRequestedPlan,
      buildPlan = buildPlan,
    ) ?: minimumPlan.fallbackDecision()
  }

  val transition = findBlurPrefilterTransition(
    belowScale = minimumScale,
    belowPlan = minimumPlan,
    aboveScale = requestedScale,
    abovePlan = resolvedRequestedPlan,
    buildPlan = buildPlan,
  )

  val belowDecision = searchSafeScaleInterval(
    safeScale = minimumScale,
    safePlan = minimumPlan,
    unsafeScale = transition.belowScale,
    unsafePlan = transition.belowPlan,
    buildPlan = buildPlan,
  )
  val aboveDecision = searchSafeScaleInterval(
    safeScale = transition.aboveScale,
    safePlan = transition.abovePlan,
    unsafeScale = requestedScale,
    unsafePlan = resolvedRequestedPlan,
    buildPlan = buildPlan,
  )
  return listOfNotNull(belowDecision, aboveDecision)
    .maxByOrNull { it.scaleFactor }
    ?: minimumPlan.fallbackDecision()
}

private data class BlurPrefilterTransition(
  val belowScale: Float,
  val belowPlan: GlassRetainedLayerPlan,
  val aboveScale: Float,
  val abovePlan: GlassRetainedLayerPlan,
)

private fun findBlurPrefilterTransition(
  belowScale: Float,
  belowPlan: GlassRetainedLayerPlan,
  aboveScale: Float,
  abovePlan: GlassRetainedLayerPlan,
  buildPlan: (Float) -> GlassRetainedLayerPlan,
): BlurPrefilterTransition {
  var lowerBits = belowScale.toBits()
  var lowerPlan = belowPlan
  var upperBits = aboveScale.toBits()
  var upperPlan = abovePlan
  while (upperBits - lowerBits > 1) {
    val candidateBits = lowerBits + (upperBits - lowerBits) / 2
    val candidatePlan = buildPlan(Float.fromBits(candidateBits))
    if (candidatePlan.hasBlurPrefilter()) {
      upperBits = candidateBits
      upperPlan = candidatePlan
    } else {
      lowerBits = candidateBits
      lowerPlan = candidatePlan
    }
  }
  return BlurPrefilterTransition(
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

private fun GlassRetainedLayerPlan.hasBlurPrefilter(): Boolean =
  layers.any { it.kind == GlassRetainedLayerKind.BlurPrefilter }

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
