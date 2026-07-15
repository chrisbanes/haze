// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.HazeProgressive
import kotlin.math.abs
import kotlin.math.pow

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

internal data class RegularGeometryProfile(
  val blurScale: Float,
  val profileReachPx: Float,
  val toneGain: Float,
  val neutralLiftWeight: Float,
)

internal data class ResolvedRegularGeometryOptics(
  val blurScale: Float,
  val refractionStrength: Float,
  val profileReachPx: Float,
  val toneGain: Float,
  val neutralLiftWeight: Float,
)

internal fun RegularGeometryProfile.resolve(refractionStrength: Float): ResolvedRegularGeometryOptics {
  val strength = refractionStrength.coerceIn(0f, 1f)
  return ResolvedRegularGeometryOptics(
    blurScale = 1f + (blurScale - 1f) * strength,
    refractionStrength = strength,
    profileReachPx = profileReachPx,
    toneGain = 1f + (toneGain - 1f) * strength,
    neutralLiftWeight = neutralLiftWeight * strength,
  )
}

internal const val MAX_REGULAR_GEOMETRY_BLUR_SCALE = 1.03f

internal fun calculateRegularGeometryProfile(
  materialSize: Size,
  cornerRadii: CornerRadii,
  blurRadiusPx: Float,
  refractionHeight: Float,
): RegularGeometryProfile {
  require(materialSize.width > 0f && materialSize.height > 0f) {
    "Regular geometry profile requires positive material dimensions"
  }
  val minDimension = materialSize.minDimension
  val normalizedRadii = listOf(
    cornerRadii.topLeft,
    cornerRadii.topRight,
    cornerRadii.bottomRight,
    cornerRadii.bottomLeft,
  ).map { (it / (minDimension * .5f)).coerceIn(0f, 1f) }
  // Global material response increases only when the whole surface supports it. The least-rounded
  // corner is the symmetric support bound; local SDF normals still shape each corner independently.
  val symmetricSupport = normalizedRadii.min()
  val lensConcentration = symmetricSupport.pow(8)
  val sizeToBlur = minDimension / (minDimension + maxOf(blurRadiusPx, 0f) * 8f)
  val lift = (1f - symmetricSupport).pow(4) * sizeToBlur.pow(4)
  return RegularGeometryProfile(
    blurScale = 1f + (MAX_REGULAR_GEOMETRY_BLUR_SCALE - 1f) * (1f - lensConcentration) -
      (.325f / GlassDefaults.refractionStrength) * lensConcentration,
    profileReachPx = minDimension * refractionHeight.coerceIn(0f, 1f),
    toneGain = 1f +
      (.036f / GlassDefaults.refractionStrength) *
      symmetricSupport.pow(.5f) * (1f - sizeToBlur).pow(.7f),
    neutralLiftWeight = minOf(lift / GlassDefaults.refractionStrength, .12f),
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
