// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.HazeProgressive
import kotlin.math.abs

internal data class GlassCoordinates(
  val sampleSize: Size,
  val materialOrigin: Offset,
  val materialSize: Size,
  val scaleFactor: Float,
)

internal fun GlassCoordinates.withRoundedSampleSize(): GlassCoordinates {
  val rounded = sampleSize.roundToIntSize()
  return copy(sampleSize = Size(rounded.width.toFloat(), rounded.height.toFloat()))
}

internal fun resolveGlassCoordinates(
  layerSize: Size,
  layerOffset: Offset,
  materialSize: Size,
  scaleFactor: Float,
): GlassCoordinates = GlassCoordinates(
  sampleSize = layerSize * scaleFactor,
  materialOrigin = layerOffset * scaleFactor,
  materialSize = materialSize * scaleFactor,
  scaleFactor = scaleFactor,
)

internal fun calculateGlassSamplePaddingPx(
  blurRadiusPx: Float,
  refractionScale: Float,
  refractionStrength: Float,
  chromaticAberrationStrength: Float,
  edgeSoftnessPx: Float,
  foregroundOutsetPx: Float,
): Float {
  val displacement = refractionScale * refractionStrength *
    (1f + 0.5f * chromaticAberrationStrength)
  return blurRadiusPx + displacement + maxOf(edgeSoftnessPx, foregroundOutsetPx)
}

internal fun effectiveSemanticBlurRadiusPx(radiusPx: Float): Float =
  radiusPx.coerceIn(0f, SemanticBlurKernel.MAX_SUPPORTED_RADIUS_PX)

internal fun calculateRefractionDetailWidthPx(
  refractionHeightPx: Float,
  edgeSoftnessPx: Float,
  sampleStepPx: Float,
): Float = minOf(
  refractionHeightPx,
  maxOf(edgeSoftnessPx * 2f, sampleStepPx * 20f),
).coerceAtLeast(0f)

internal fun calculateRefractionDetailVisibility(
  refractionStrength: Float,
  refractionScalePx: Float,
  sampleStepPx: Float,
): Float {
  val displacementFraction = (
    abs(refractionScalePx * refractionStrength) / maxOf(sampleStepPx, 1f)
    ).coerceIn(0f, 1f)
  return displacementFraction * displacementFraction * displacementFraction *
    (displacementFraction * (displacementFraction * 6f - 15f) + 10f)
}

internal data class AdaptiveGeometryResponse(
  val blurScale: Float,
  val displacementScale: Float,
  val reachScale: Float,
  val toneGain: Float,
  val neutralLiftWeight: Float,
) {
  fun resolve(refractionStrength: Float): AdaptiveGeometryResponse {
    val strength = refractionStrength.coerceIn(0f, 1f)
    return AdaptiveGeometryResponse(
      blurScale = lerp(1f, blurScale, strength),
      displacementScale = lerp(1f, displacementScale, strength),
      reachScale = lerp(1f, reachScale, strength),
      toneGain = lerp(1f, toneGain, strength),
      neutralLiftWeight = neutralLiftWeight * strength,
    )
  }

  companion object {
    val Identity: AdaptiveGeometryResponse = AdaptiveGeometryResponse(
      blurScale = 1f,
      displacementScale = 1f,
      reachScale = 1f,
      toneGain = 1f,
      neutralLiftWeight = 0f,
    )
  }
}

internal fun calculateAdaptiveGeometryResponse(
  materialSizePx: Size,
  density: Density,
  cornerRadiiPx: CornerRadii,
): AdaptiveGeometryResponse {
  val densityValue = density.density
  if (!densityValue.isFinite() || densityValue <= 0f) return AdaptiveGeometryResponse.Identity
  if (
    !materialSizePx.width.isFinite() ||
    !materialSizePx.height.isFinite() ||
    materialSizePx.width <= 0f ||
    materialSizePx.height <= 0f
  ) {
    return AdaptiveGeometryResponse.Identity
  }

  if (
    !cornerRadiiPx.topLeft.isFinite() ||
    !cornerRadiiPx.topRight.isFinite() ||
    !cornerRadiiPx.bottomRight.isFinite() ||
    !cornerRadiiPx.bottomLeft.isFinite()
  ) {
    return AdaptiveGeometryResponse.Identity
  }
  val minimumRadiusPx = minOf(
    cornerRadiiPx.topLeft,
    cornerRadiiPx.topRight,
    cornerRadiiPx.bottomRight,
    cornerRadiiPx.bottomLeft,
  )

  val shortestSidePx = materialSizePx.minDimension
  return calculateAdaptiveGeometryResponseForLogicalGeometry(
    shortestSideDp = shortestSidePx / densityValue,
    aspectRatio = materialSizePx.maxDimension / shortestSidePx,
    symmetricRoundness = minimumRadiusPx / (shortestSidePx * 0.5f),
  )
}

internal fun calculateAdaptiveGeometryResponseForLogicalGeometry(
  shortestSideDp: Float,
  aspectRatio: Float,
  symmetricRoundness: Float,
): AdaptiveGeometryResponse {
  if (
    !shortestSideDp.isFinite() ||
    !aspectRatio.isFinite() ||
    !symmetricRoundness.isFinite() ||
    shortestSideDp <= 0f ||
    aspectRatio <= 0f
  ) {
    return AdaptiveGeometryResponse.Identity
  }

  val size = smoothstepFeature(shortestSideDp, 48f, 240f)
  val aspect = smoothstepFeature(aspectRatio, 1f, 3.5f)
  val roundness = smoothstepFeature(symmetricRoundness, 0f, 1f)
  return AdaptiveGeometryResponse(
    blurScale = lerp(0.72f, 1f, size) * lerp(1f, 0.92f, aspect),
    displacementScale = lerp(1.4f, 0.9f, size) * lerp(1f, 1.1f, aspect),
    reachScale = lerp(1.2f, 0.9f, size) * lerp(0.95f, 1.05f, roundness),
    toneGain = 1f,
    neutralLiftWeight = 0f,
  )
}

private fun smoothstepFeature(value: Float, minimum: Float, maximum: Float): Float {
  val normalized = ((value - minimum) / (maximum - minimum)).coerceIn(0f, 1f)
  return normalized * normalized * (3f - 2f * normalized)
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
  start + (stop - start) * fraction

internal data class ResolvedAdaptiveGeometryOptics(
  val blurRadiusPx: Float,
  val blurSigmaPx: Float,
  val refractionScalePx: Float,
  val refractionHeightPx: Float,
  val toneGain: Float,
  val neutralLiftWeight: Float,
)

internal fun resolveAdaptiveGeometryOptics(
  response: AdaptiveGeometryResponse,
  refractionStrength: Float,
  shortestSidePx: Float,
  blurRadiusPx: Float,
  refractionScalePx: Float,
  refractionHeight: Float,
): ResolvedAdaptiveGeometryOptics {
  val resolved = response.resolve(refractionStrength)
  val resolvedBlurRadiusPx = if (blurRadiusPx > 0f) {
    blurRadiusPx * resolved.blurScale
  } else {
    0f
  }
  val resolvedRefractionScalePx = if (refractionScalePx > 0f) {
    refractionScalePx * resolved.displacementScale
  } else {
    0f
  }
  val validShortestSidePx = shortestSidePx.takeIf { it.isFinite() && it > 0f } ?: 0f
  val baselineReachPx = validShortestSidePx * refractionHeight.coerceIn(0f, 1f)
  return ResolvedAdaptiveGeometryOptics(
    blurRadiusPx = resolvedBlurRadiusPx,
    blurSigmaPx = if (resolvedBlurRadiusPx > 0f) {
      SemanticBlurKernel.radiusToSigma(resolvedBlurRadiusPx)
    } else {
      0f
    },
    refractionScalePx = resolvedRefractionScalePx,
    refractionHeightPx = (baselineReachPx * resolved.reachScale)
      .coerceIn(0f, validShortestSidePx),
    toneGain = resolved.toneGain,
    neutralLiftWeight = resolved.neutralLiftWeight,
  )
}

private val AdaptiveOpticsBaseline = GlassOptics.Absolute()

internal data class ResolvedGlassOptics(
  val refractionStrength: Float,
  val refractionHeightPx: Float,
  val refractionScalePx: Float,
  val depth: Float,
  val blurRadiusPx: Float,
  val blurSigmaPx: Float,
  val progressive: HazeProgressive?,
  val toneGain: Float,
  val neutralLiftWeight: Float,
)

internal fun resolveGlassOptics(
  optics: GlassOptics,
  materialSizePx: Size,
  density: Density,
  cornerRadiiPx: CornerRadii,
): ResolvedGlassOptics {
  val absolute = when (optics) {
    GlassOptics.Adaptive -> AdaptiveOpticsBaseline
    is GlassOptics.Absolute -> optics
  }
  val response = when (optics) {
    GlassOptics.Adaptive -> calculateAdaptiveGeometryResponse(
      materialSizePx = materialSizePx,
      density = density,
      cornerRadiiPx = cornerRadiiPx,
    )
    is GlassOptics.Absolute -> AdaptiveGeometryResponse.Identity
  }
  val resolved = resolveAdaptiveGeometryOptics(
    response = response,
    refractionStrength = absolute.refractionStrength,
    shortestSidePx = materialSizePx.minDimension,
    blurRadiusPx = effectiveSemanticBlurRadiusPx(with(density) { absolute.blurRadius.toPx() }),
    refractionScalePx = absolute.refractionScale,
    refractionHeight = absolute.refractionHeight,
  )
  return ResolvedGlassOptics(
    refractionStrength = absolute.refractionStrength,
    refractionHeightPx = resolved.refractionHeightPx,
    refractionScalePx = resolved.refractionScalePx,
    depth = absolute.depth,
    blurRadiusPx = resolved.blurRadiusPx,
    blurSigmaPx = resolved.blurSigmaPx,
    progressive = absolute.progressive,
    toneGain = resolved.toneGain,
    neutralLiftWeight = resolved.neutralLiftWeight,
  )
}

internal data class GlassRenderParams(
  val coordinates: GlassCoordinates,
  val refractionStrength: Float,
  val specularIntensity: Float,
  val depth: Float,
  val ambientResponse: Float,
  val tint: Color,
  val edgeSoftnessPx: Float,
  val blurRadiusPx: Float,
  val blurSigmaPx: Float,
  val progressive: HazeProgressive?,
  val refractionHeightPx: Float,
  val chromaticAberrationStrength: Float,
  val surfaceProfile: Float,
  val chromaticAberrationMode: Float,
  val contrast: Float,
  val whitePoint: Float,
  val chromaMultiplier: Float,
  val refractionScalePx: Float,
  val contentNormalBlend: Float,
  val specularExponent: Float,
  val fresnelExponent: Float,
  val geometryToneGain: Float,
  val geometryNeutralLift: Float,
  val cornerRadii: CornerRadii,
  val lightPosition: Offset,
  val sampleStepPx: Float,
)

internal data class GlassBlurEffectKey(
  val plan: SemanticBlurPlan,
  val progressive: HazeProgressive?,
  val materialOrigin: Offset,
  val materialSize: Size,
)

internal fun GlassRenderParams.blurEffectKey(): GlassBlurEffectKey {
  val sampleSize = coordinates.sampleSize
  val plan = SemanticBlurPlan.createForSigma(
    sampleWidth = sampleSize.width.toInt().coerceAtLeast(1),
    sampleHeight = sampleSize.height.toInt().coerceAtLeast(1),
    effectiveRadiusPx = blurRadiusPx,
    sigmaPx = blurSigmaPx,
    allowMultiscale = progressive == null,
  )
  return GlassBlurEffectKey(
    plan = plan,
    progressive = progressive,
    materialOrigin = if (progressive != null) coordinates.materialOrigin * plan.scaleFactor else Offset.Zero,
    materialSize = if (progressive != null) coordinates.materialSize * plan.scaleFactor else Size.Zero,
  )
}

internal data class GlassOpticalEffectKey(
  val coordinates: GlassCoordinates,
  val refractionStrength: Float,
  val ambientResponse: Float,
  val tint: Color,
  val edgeSoftnessPx: Float,
  val refractionHeightPx: Float,
  val chromaticAberrationStrength: Float,
  val surfaceProfile: Float,
  val chromaticAberrationMode: Float,
  val contrast: Float,
  val whitePoint: Float,
  val chromaMultiplier: Float,
  val refractionScalePx: Float,
  val contentNormalBlend: Float,
  val fresnelExponent: Float,
  val geometryToneGain: Float,
  val geometryNeutralLift: Float,
  val cornerRadii: CornerRadii,
  val sampleStepPx: Float,
)

internal fun GlassRenderParams.opticalEffectKey() = GlassOpticalEffectKey(
  coordinates = coordinates,
  refractionStrength = refractionStrength,
  ambientResponse = ambientResponse,
  tint = tint,
  edgeSoftnessPx = edgeSoftnessPx,
  refractionHeightPx = refractionHeightPx,
  chromaticAberrationStrength = chromaticAberrationStrength,
  surfaceProfile = surfaceProfile,
  chromaticAberrationMode = chromaticAberrationMode,
  contrast = contrast,
  whitePoint = whitePoint,
  chromaMultiplier = chromaMultiplier,
  refractionScalePx = refractionScalePx,
  contentNormalBlend = contentNormalBlend,
  fresnelExponent = fresnelExponent,
  geometryToneGain = geometryToneGain,
  geometryNeutralLift = geometryNeutralLift,
  cornerRadii = cornerRadii,
  sampleStepPx = sampleStepPx,
)

internal data class GlassRefractionDetailEffectKey(
  val sampleSize: Size,
  val materialOrigin: Offset,
  val materialSize: Size,
  val refractionStrength: Float,
  val refractionHeightPx: Float,
  val refractionScalePx: Float,
  val surfaceProfile: Float,
  val edgeSoftnessPx: Float,
  val cornerRadii: CornerRadii,
  val detailWidthPx: Float,
  val detailIntensity: Float,
  val detailVisibility: Float,
)

internal fun GlassRenderParams.refractionDetailEffectKey(
  detailIntensity: Float = GLASS_REFRACTION_DETAIL_INTENSITY,
) = GlassRefractionDetailEffectKey(
  sampleSize = coordinates.sampleSize,
  materialOrigin = coordinates.materialOrigin,
  materialSize = coordinates.materialSize,
  refractionStrength = refractionStrength,
  refractionHeightPx = refractionHeightPx,
  refractionScalePx = refractionScalePx,
  surfaceProfile = surfaceProfile,
  edgeSoftnessPx = edgeSoftnessPx,
  cornerRadii = cornerRadii,
  detailWidthPx = calculateRefractionDetailWidthPx(
    refractionHeightPx = refractionHeightPx,
    edgeSoftnessPx = edgeSoftnessPx,
    sampleStepPx = sampleStepPx,
  ),
  detailIntensity = detailIntensity,
  detailVisibility = calculateRefractionDetailVisibility(
    refractionStrength = refractionStrength,
    refractionScalePx = refractionScalePx,
    sampleStepPx = sampleStepPx,
  ),
)

internal fun GlassRenderParams.activeRefractionDetailEffectKey(
  detailIntensity: Float = GLASS_REFRACTION_DETAIL_INTENSITY,
): GlassRefractionDetailEffectKey? = refractionDetailEffectKey(detailIntensity).takeIf { key ->
  key.refractionStrength > 0f && key.refractionScalePx > 0f &&
    key.detailWidthPx > 0f && key.detailIntensity * key.detailVisibility > 1f / 255f
}

private const val GLASS_REFRACTION_DETAIL_INTENSITY = 0.76f

internal data class GlassRimEffectKey(
  val coordinates: GlassCoordinates,
  val specularIntensity: Float,
  val specularExponent: Float,
  val edgeSoftnessPx: Float,
  val cornerRadii: CornerRadii,
  val lightPosition: Offset,
  val sampleStepPx: Float,
)

internal fun GlassRenderParams.rimEffectKey() = GlassRimEffectKey(
  coordinates = coordinates,
  specularIntensity = specularIntensity,
  specularExponent = specularExponent,
  edgeSoftnessPx = edgeSoftnessPx,
  cornerRadii = cornerRadii,
  lightPosition = lightPosition,
  sampleStepPx = sampleStepPx,
)
