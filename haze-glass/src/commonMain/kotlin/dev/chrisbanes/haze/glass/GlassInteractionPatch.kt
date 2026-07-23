// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor

internal data class GlassInteractionTopology(
  val hasOptics: Boolean,
  val hasLighting: Boolean,
  val maxRefractionMultiplier: Float,
) {
  val hasAnyLayer: Boolean get() = hasOptics || hasLighting
}

internal fun GlassInteractionSlots.resolveInteractionTopology(): GlassInteractionTopology {
  val responses = listOfNotNull(focused?.response, hovered?.response, pressed?.response)
  return GlassInteractionTopology(
    hasOptics = responses.any { response ->
      response.refractionMultiplier?.value?.let { it != 1f } == true ||
        response.whitePointDelta?.value?.let { it != 0f } == true
    },
    hasLighting = responses.any { response ->
      response.lightingIntensity?.value?.let { it > 0f } == true
    },
    maxRefractionMultiplier = maxOf(
      1f,
      responses.maxOfOrNull { it.refractionMultiplier?.value ?: 1f } ?: 1f,
    ),
  )
}

internal data class GlassInteractionPatch(
  val bounds: IntRect,
  val coordinates: GlassCoordinates,
  val uniforms: GlassInteractionUniforms,
)

internal fun calculateGlassInteractionPatchSize(
  params: GlassRenderParams,
  radiusFraction: Float,
  topology: GlassInteractionTopology,
): IntSize {
  if (!topology.hasAnyLayer || radiusFraction <= 0f || !radiusFraction.isFinite()) {
    return IntSize.Zero
  }
  val radiusPx = (params.coordinates.materialSize.minDimension * radiusFraction)
    .takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
  val extent = radiusPx + calculateInteractionSamplingPadding(params, topology)
  // A fractional center can independently round the left edge down and the right edge up.
  // Reserve one additional pixel so that retained layers always cover that outward rounding.
  val side = (ceil(extent * 2f).toInt() + 1).coerceAtLeast(0)
  val sampleSize = params.coordinates.sampleSize
  return IntSize(
    width = minOf(side, sampleSize.width.toInt().coerceAtLeast(0)),
    height = minOf(side, sampleSize.height.toInt().coerceAtLeast(0)),
  )
}

internal fun resolveGlassInteractionPatch(
  params: GlassRenderParams,
  uniforms: GlassInteractionUniforms,
  topology: GlassInteractionTopology,
): GlassInteractionPatch? {
  if (
    !topology.hasAnyLayer || uniforms.radiusPx <= 0f || !uniforms.radiusPx.isFinite() ||
    !uniforms.position.x.isFinite() || !uniforms.position.y.isFinite()
  ) {
    return null
  }
  val sampleSize = params.coordinates.sampleSize
  if (!sampleSize.isDrawable()) return null
  val extent = uniforms.radiusPx + calculateInteractionSamplingPadding(params, topology)
  val bounds = IntRect(
    left = floor(uniforms.position.x - extent).toInt().coerceIn(0, sampleSize.width.toInt()),
    top = floor(uniforms.position.y - extent).toInt().coerceIn(0, sampleSize.height.toInt()),
    right = ceil(uniforms.position.x + extent).toInt().coerceIn(0, sampleSize.width.toInt()),
    bottom = ceil(uniforms.position.y + extent).toInt().coerceIn(0, sampleSize.height.toInt()),
  )
  if (bounds.width <= 0 || bounds.height <= 0) return null
  val origin = Offset(bounds.left.toFloat(), bounds.top.toFloat())
  return GlassInteractionPatch(
    bounds = bounds,
    coordinates = params.coordinates.copy(
      sampleSize = Size(bounds.width.toFloat(), bounds.height.toFloat()),
      materialOrigin = params.coordinates.materialOrigin - origin,
    ),
    uniforms = uniforms.copy(
      position = uniforms.position - origin,
      refractionMultiplier = uniforms.refractionMultiplier.coerceIn(
        minimumValue = 0f,
        maximumValue = topology.maxRefractionMultiplier.coerceAtLeast(0f),
      ),
    ),
  )
}

private fun calculateInteractionSamplingPadding(
  params: GlassRenderParams,
  topology: GlassInteractionTopology,
): Float = (
  params.refractionScalePx * params.refractionStrength * topology.maxRefractionMultiplier *
    (1f + 0.5f * params.chromaticAberrationStrength) +
    maxOf(params.edgeSoftnessPx, params.sampleStepPx)
  ).takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
