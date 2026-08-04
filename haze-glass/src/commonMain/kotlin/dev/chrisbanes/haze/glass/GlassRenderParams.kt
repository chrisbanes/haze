// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.HazeProgressive
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MAX_REFRACTION_DISPLACEMENT_PX = 16_384f

internal data class GlassCoordinates(
  val sampleSize: Size,
  val materialOrigin: Offset,
  val materialSize: Size,
  val scaleFactor: Float,
)

internal fun Size.isDrawable(): Boolean =
  width.isFinite() && height.isFinite() && width > 0f && height > 0f

internal fun Offset.clampTo(size: Size): Offset = Offset(
  x = x.coerceIn(0f, size.width),
  y = y.coerceIn(0f, size.height),
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
  val safeBlurRadius = blurRadiusPx.finiteOrZero()
  val safeRefractionScale = refractionScale.finiteOrZero()
  val safeRefractionStrength = refractionStrength.finiteOrZero()
  val safeChromaticAberration = chromaticAberrationStrength.finiteOrZero()
  val safeEdgeSoftness = edgeSoftnessPx.finiteOrZero()
  val safeForegroundOutset = foregroundOutsetPx.finiteOrZero()
  val displacement = (
    safeRefractionScale * safeRefractionStrength *
      (1f + 0.5f * safeChromaticAberration)
    ).finiteOrZero()
  return (safeBlurRadius + displacement + maxOf(safeEdgeSoftness, safeForegroundOutset))
    .finiteOrZero()
    .coerceAtLeast(0f)
}

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

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

  val size = smoothstepFeature(shortestSideDp, 48f, 176f)
  val aspect = smoothstepFeature(aspectRatio, 1f, 3.5f)
  val roundness = smoothstepFeature(symmetricRoundness, 0f, 1f)
  return AdaptiveGeometryResponse(
    blurScale = lerp(0.3f, 1.1f, size),
    displacementScale = 4f * lerp(1f, 1.1f, aspect),
    reachScale = 3f * lerp(0.95f, 1.05f, roundness),
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

private val AdaptiveOpticsBaseline = GlassOptics.Fixed()

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
  val refractionDetailIntensity: Float,
)

internal fun resolveGlassOptics(
  optics: GlassOptics,
  materialSizePx: Size,
  density: Density,
  cornerRadiiPx: CornerRadii,
): ResolvedGlassOptics {
  val fixed = when (optics) {
    GlassOptics.Adaptive -> AdaptiveOpticsBaseline
    is GlassOptics.Fixed -> optics
  }
  val response = when (optics) {
    GlassOptics.Adaptive -> calculateAdaptiveGeometryResponse(
      materialSizePx = materialSizePx,
      density = density,
      cornerRadiiPx = cornerRadiiPx,
    )
    is GlassOptics.Fixed -> AdaptiveGeometryResponse.Identity
  }
  val resolved = resolveAdaptiveGeometryOptics(
    response = response,
    refractionStrength = fixed.refractionStrength,
    shortestSidePx = materialSizePx.minDimension,
    blurRadiusPx = effectiveSemanticBlurRadiusPx(with(density) { fixed.blurRadius.toPx() }),
    refractionScalePx = with(density) { fixed.refractionDisplacement.toPx() },
    refractionHeight = fixed.refractionHeightFraction,
  )
  return ResolvedGlassOptics(
    refractionStrength = fixed.refractionStrength,
    refractionHeightPx = resolved.refractionHeightPx,
    refractionScalePx = resolved.refractionScalePx
      .coerceIn(0f, MAX_REFRACTION_DISPLACEMENT_PX)
      .finiteOrZero(),
    depth = fixed.depth,
    blurRadiusPx = resolved.blurRadiusPx,
    blurSigmaPx = resolved.blurSigmaPx,
    progressive = fixed.progressive,
    toneGain = resolved.toneGain,
    neutralLiftWeight = resolved.neutralLiftWeight,
    refractionDetailIntensity = when (optics) {
      GlassOptics.Adaptive -> 0f
      is GlassOptics.Fixed -> GLASS_REFRACTION_DETAIL_INTENSITY
    },
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
  /** Light position resolved from authored Alignment into material-local pixels. */
  val lightPosition: Offset,
  val sampleStepPx: Float,
  val refractionDetailIntensity: Float = GLASS_REFRACTION_DETAIL_INTENSITY,
)

internal data class GlassBlurEffectKey(
  val plan: SemanticBlurPlan,
  val progressive: HazeProgressive?,
  val maskOrigin: Offset,
  val maskSize: Size,
  val maskCoordinateScale: Float,
)

internal fun GlassRenderParams.blurEffectKey(): GlassBlurEffectKey {
  val sampleSize = coordinates.sampleSize.roundToIntSize()
  val plan = SemanticBlurPlan.createForSigma(
    sampleWidth = sampleSize.width.coerceAtLeast(1),
    sampleHeight = sampleSize.height.coerceAtLeast(1),
    effectiveRadiusPx = blurRadiusPx,
    sigmaPx = blurSigmaPx,
    allowMultiscale = progressive == null,
  )
  return GlassBlurEffectKey(
    plan = plan,
    progressive = progressive,
    maskOrigin = if (progressive != null) coordinates.materialOrigin * plan.scaleFactor else Offset.Zero,
    maskSize = if (progressive != null) coordinates.materialSize / coordinates.scaleFactor else Size.Zero,
    maskCoordinateScale = if (progressive != null) {
      1f / (coordinates.scaleFactor * plan.scaleFactor)
    } else {
      1f
    },
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

internal data class GlassInteractionLightingKey(
  val coordinates: GlassCoordinates,
  val edgeSoftnessPx: Float,
  val cornerRadii: CornerRadii,
)

internal data class GlassInteractionUniforms(
  val position: Offset,
  val radiusPx: Float,
  val lightingIntensity: Float,
  val refractionMultiplier: Float,
  val whitePointDelta: Float,
) {
  val hasLighting: Boolean get() = lightingIntensity > 0f && radiusPx > 0f
  val hasOptics: Boolean get() =
    radiusPx > 0f && (refractionMultiplier != 1f || whitePointDelta != 0f)
}

internal data class ResolvedGlassInteraction(
  val position: Offset,
  val radiusFraction: Float,
  val lightingIntensity: Float,
  val refractionMultiplier: Float,
  val whitePointDelta: Float,
) {
  val hasLighting: Boolean get() = lightingIntensity > 0f && radiusFraction > 0f
  val hasOptics: Boolean get() =
    radiusFraction > 0f && (refractionMultiplier != 1f || whitePointDelta != 0f)

  fun uniforms(coordinates: GlassCoordinates): GlassInteractionUniforms {
    val materialCenter = coordinates.materialOrigin + coordinates.materialSize.center
    val scaledPosition = position * coordinates.scaleFactor + coordinates.materialOrigin
    return GlassInteractionUniforms(
      position = scaledPosition.takeIf { it.x.isFinite() && it.y.isFinite() } ?: materialCenter,
      radiusPx = (coordinates.materialSize.minDimension * radiusFraction).finiteOrZero()
        .coerceAtLeast(0f),
      lightingIntensity = lightingIntensity,
      refractionMultiplier = refractionMultiplier,
      whitePointDelta = whitePointDelta,
    )
  }
}

internal fun resolveGlassInteraction(
  state: GlassInteractionRenderState,
  radiusFraction: Float,
): ResolvedGlassInteraction = ResolvedGlassInteraction(
  position = state.position,
  radiusFraction = radiusFraction
    .finiteOr(GlassDefaults.interactionLightRadiusFraction)
    .coerceIn(0f, 2f),
  lightingIntensity = state.lightingIntensity.finiteOr(0f).coerceIn(0f, 1f),
  refractionMultiplier = state.refractionMultiplier.finiteOr(1f).coerceIn(0f, 2f),
  whitePointDelta = state.whitePointDelta.finiteOr(0f).coerceIn(-1f, 1f),
)

internal fun GlassRenderParams.interactionUniforms(
  state: GlassInteractionRenderState,
  radiusFraction: Float,
): GlassInteractionUniforms = resolveGlassInteraction(state, radiusFraction).uniforms(coordinates)

internal data class ResolvedGlassStyle(
  val resolvedOptics: ResolvedGlassOptics,
  val specularIntensity: Float,
  val ambientResponse: Float,
  val tint: Color,
  val edgeSoftnessPx: Float,
  val lightPosition: Offset,
  val chromaticAberrationStrength: Float,
  val surfaceProfile: Float,
  val chromaticAberrationMode: Float,
  val alpha: Float,
  val contrast: Float,
  val whitePoint: Float,
  val chromaMultiplier: Float,
  val contentNormalBlend: Float,
  val specularExponent: Float,
  val fresnelExponent: Float,
  val cornerRadii: CornerRadii,
)

internal fun resolveGlassStyle(
  effect: GlassRuntimeEffect,
  materialSizePx: Size,
  density: Density,
  layoutDirection: LayoutDirection,
): ResolvedGlassStyle {
  val requestedRadii = effect.shape.toValidCornerRadiiPxOrNull(
    materialSizePx,
    density,
    layoutDirection,
  )
  val defaultRadii = GlassDefaults.shape.toCornerRadiiPx(materialSizePx, density, layoutDirection)
  val defaultEdgeSoftnessPx = with(density) { GlassDefaults.edgeSoftness.toPx() }
    .finiteOrZero()
    .coerceAtLeast(0f)
  val cornerRadii = when {
    requestedRadii?.isFiniteAndNonNegative() == true -> requestedRadii
    defaultRadii.isFiniteAndNonNegative() -> defaultRadii
    else -> CornerRadii.zero
  }
  val alignedLightPosition = effect.lightPosition.resolveLightPosition(
    materialSizePx = materialSizePx,
    layoutDirection = layoutDirection,
  )
  return ResolvedGlassStyle(
    resolvedOptics = resolveGlassOptics(effect.optics, materialSizePx, density, cornerRadii),
    specularIntensity = effect.specularIntensity
      .finiteOr(GlassDefaults.specularIntensity).coerceIn(0f, 1f),
    ambientResponse = effect.ambientResponse
      .finiteOr(GlassDefaults.ambientResponse).coerceIn(0f, 1f),
    tint = effect.tint,
    edgeSoftnessPx = with(density) { effect.edgeSoftness.toPx() }
      .finiteOr(defaultEdgeSoftnessPx)
      .coerceAtLeast(0f),
    lightPosition = alignedLightPosition,
    chromaticAberrationStrength = effect.chromaticAberrationStrength
      .finiteOr(GlassDefaults.chromaticAberrationStrength).coerceIn(0f, 1f),
    surfaceProfile = effect.surfaceProfile.ordinal.toFloat(),
    chromaticAberrationMode = effect.chromaticAberrationMode.ordinal.toFloat(),
    alpha = effect.alpha.finiteOr(GlassDefaults.alpha).coerceIn(0f, 1f),
    contrast = effect.contrast.finiteOr(GlassDefaults.contrast).coerceIn(-1f, 1f),
    whitePoint = effect.whitePoint.finiteOr(GlassDefaults.whitePoint).coerceIn(-1f, 1f),
    chromaMultiplier = effect.chromaMultiplier
      .finiteOr(GlassDefaults.chromaMultiplier).coerceIn(0f, 2f),
    contentNormalBlend = effect.contentNormalBlend
      .finiteOr(GlassDefaults.contentNormalBlend).coerceIn(0f, 1f),
    specularExponent = effect.specularExponent
      .finiteOr(GlassDefaults.specularExponent).coerceAtLeast(0f),
    fresnelExponent = effect.fresnelExponent
      .finiteOr(GlassDefaults.fresnelExponent).coerceAtLeast(0f),
    cornerRadii = cornerRadii,
  )
}

private fun Alignment.resolveLightPosition(
  materialSizePx: Size,
  layoutDirection: LayoutDirection,
): Offset = when (this) {
  is BiasAlignment -> resolveBiasPosition(
    center = materialSizePx.center,
    horizontalBias = if (layoutDirection == LayoutDirection.Ltr) {
      horizontalBias
    } else {
      -horizontalBias
    },
    verticalBias = verticalBias,
  )
  is BiasAbsoluteAlignment -> resolveBiasPosition(
    center = materialSizePx.center,
    horizontalBias = horizontalBias,
    verticalBias = verticalBias,
  )
  else -> align(
    size = IntSize.Zero,
    space = materialSizePx.roundToIntSize(),
    layoutDirection = layoutDirection,
  ).let { Offset(it.x.toFloat(), it.y.toFloat()) }
}

private fun resolveBiasPosition(
  center: Offset,
  horizontalBias: Float,
  verticalBias: Float,
): Offset = Offset(
  x = center.x * (1f + horizontalBias),
  y = center.y * (1f + verticalBias),
)

internal fun buildGlassRenderParams(
  style: ResolvedGlassStyle,
  coordinates: GlassCoordinates,
): GlassRenderParams {
  val scaleFactor = coordinates.scaleFactor
  val resolvedOptics = style.resolvedOptics
  val blurRadiusPx = resolvedOptics.blurRadiusPx.finiteOr(0f).coerceAtLeast(0f) * scaleFactor
  return GlassRenderParams(
    coordinates = coordinates,
    refractionStrength = resolvedOptics.refractionStrength.finiteOr(0f).coerceIn(0f, 1f),
    specularIntensity = style.specularIntensity,
    depth = resolvedOptics.depth.finiteOr(0f).coerceIn(0f, 1f),
    ambientResponse = style.ambientResponse,
    tint = style.tint,
    edgeSoftnessPx = style.edgeSoftnessPx * scaleFactor,
    blurRadiusPx = blurRadiusPx,
    blurSigmaPx = SemanticBlurKernel.radiusToSigma(blurRadiusPx),
    progressive = resolvedOptics.progressive,
    refractionHeightPx = resolvedOptics.refractionHeightPx.finiteOr(0f).coerceAtLeast(0f) * scaleFactor,
    chromaticAberrationStrength = style.chromaticAberrationStrength,
    surfaceProfile = style.surfaceProfile,
    chromaticAberrationMode = style.chromaticAberrationMode,
    contrast = style.contrast,
    whitePoint = style.whitePoint,
    chromaMultiplier = style.chromaMultiplier,
    refractionScalePx = resolvedOptics.refractionScalePx.finiteOr(0f).coerceAtLeast(0f) * scaleFactor,
    contentNormalBlend = style.contentNormalBlend,
    specularExponent = style.specularExponent,
    fresnelExponent = style.fresnelExponent,
    geometryToneGain = resolvedOptics.toneGain.finiteOr(1f),
    geometryNeutralLift = resolvedOptics.neutralLiftWeight.finiteOr(0f),
    cornerRadii = style.cornerRadii * scaleFactor,
    lightPosition = style.lightPosition * scaleFactor,
    sampleStepPx = 2f * scaleFactor,
    refractionDetailIntensity = resolvedOptics.refractionDetailIntensity,
  )
}

internal fun buildGlassRetainedLayerPlan(
  params: GlassRenderParams,
  interaction: GlassInteractionUniforms,
  interactionTopology: GlassInteractionTopology = GlassInteractionTopology(
    hasOptics = interaction.hasOptics,
    hasLighting = interaction.hasLighting,
    maxRefractionMultiplier = interaction.refractionMultiplier,
  ),
): GlassRetainedLayerPlan {
  val sampleSize = params.coordinates.sampleSize.roundToIntSize()
  val blurActive = params.depth > 0f && params.blurRadiusPx > 0f
  val blurPlan = if (blurActive) params.blurEffectKey().plan else null
  val interactionPatchSize = calculateGlassInteractionPatchSize(
    params,
    radiusFraction = interaction.radiusPx / params.coordinates.materialSize.minDimension,
    topology = interactionTopology,
  )
  val interactionLayersActive = interactionPatchSize.width > 0 && interactionPatchSize.height > 0
  return buildGlassRetainedLayerPlan(
    sampleSize = sampleSize,
    blurWorkingSize = blurPlan?.workingSize,
    blurRequiresPrefilter = blurPlan?.requiresPrefilter == true,
    depthMixActive = blurActive && params.depth < 1f,
    refractionDetailActive = params.isRefractionDetailActive(),
    rimActive = params.specularIntensity > 0f,
    interactionPatchSize = interactionPatchSize,
    interactionOpticsActive = interactionLayersActive && interactionTopology.hasOptics,
    interactionLightingActive = interactionLayersActive && interactionTopology.hasLighting,
    groupCompositeSize = null,
  )
}

internal fun buildGlassBudgetLayerPlan(
  sampleSize: IntSize,
  groupCompositeSize: IntSize? = null,
  blurRadiusPx: Float,
  depth: Float,
  allowMultiscaleBlur: Boolean,
  refractionDetailActive: Boolean,
  rimActive: Boolean,
  interactionPatchSize: IntSize = sampleSize,
  interactionOpticsActive: Boolean,
  interactionLightingActive: Boolean,
): GlassRetainedLayerPlan {
  val interactionLayersActive =
    interactionPatchSize.width > 0 && interactionPatchSize.height > 0
  if (supportsFusedGlassRenderEffect) {
    return buildGlassFusedLayerPlan(
      sampleSize = sampleSize,
      rimActive = rimActive,
      interactionPatchSize = interactionPatchSize,
      interactionLightingActive = interactionLayersActive && interactionLightingActive,
      groupCompositeSize = groupCompositeSize,
    )
  }
  val blurActive = depth > 0f && blurRadiusPx > 0f
  val blurScale = if (
    blurActive && allowMultiscaleBlur &&
    blurRadiusPx > SemanticBlurPlan.DOWNSAMPLE_RADIUS_THRESHOLD_PX
  ) {
    0.5f
  } else {
    1f
  }
  val blurWorkingSize = if (blurActive) {
    IntSize(
      width = (sampleSize.width * blurScale).roundToInt().coerceAtLeast(1),
      height = (sampleSize.height * blurScale).roundToInt().coerceAtLeast(1),
    )
  } else {
    null
  }
  return buildGlassRetainedLayerPlan(
    sampleSize = sampleSize,
    blurWorkingSize = blurWorkingSize,
    blurRequiresPrefilter = blurActive && blurScale < 1f,
    depthMixActive = blurActive && depth < 1f,
    refractionDetailActive = refractionDetailActive,
    rimActive = rimActive,
    interactionPatchSize = interactionPatchSize,
    interactionOpticsActive = interactionLayersActive && interactionOpticsActive,
    interactionLightingActive = interactionLayersActive && interactionLightingActive,
    groupCompositeSize = groupCompositeSize,
  )
}

private fun buildGlassRetainedLayerPlan(
  sampleSize: IntSize,
  blurWorkingSize: IntSize?,
  blurRequiresPrefilter: Boolean,
  depthMixActive: Boolean,
  refractionDetailActive: Boolean,
  rimActive: Boolean,
  interactionPatchSize: IntSize,
  interactionOpticsActive: Boolean,
  interactionLightingActive: Boolean,
  groupCompositeSize: IntSize?,
): GlassRetainedLayerPlan = GlassRetainedLayerPlan(
  buildList {
    add(GlassRetainedLayer(GlassRetainedLayerKind.Source, sampleSize))
    if (blurWorkingSize != null) {
      if (blurRequiresPrefilter) {
        add(GlassRetainedLayer(GlassRetainedLayerKind.BlurPrefilter, sampleSize))
      }
      add(GlassRetainedLayer(GlassRetainedLayerKind.BlurHorizontal, blurWorkingSize))
      add(GlassRetainedLayer(GlassRetainedLayerKind.Blurred, blurWorkingSize))
      if (depthMixActive) {
        add(GlassRetainedLayer(GlassRetainedLayerKind.DepthMixed, sampleSize))
      }
    }
    add(GlassRetainedLayer(GlassRetainedLayerKind.Optical, sampleSize))
    if (refractionDetailActive) {
      add(GlassRetainedLayer(GlassRetainedLayerKind.RefractionDetail, sampleSize))
      add(GlassRetainedLayer(GlassRetainedLayerKind.RefractionDetailCoverage, sampleSize))
      add(GlassRetainedLayer(GlassRetainedLayerKind.RefractionComposite, sampleSize))
    }
    if (rimActive) add(GlassRetainedLayer(GlassRetainedLayerKind.Rim, sampleSize))
    if (interactionOpticsActive) {
      add(GlassRetainedLayer(GlassRetainedLayerKind.InteractionOptical, interactionPatchSize))
      if (refractionDetailActive) {
        add(GlassRetainedLayer(GlassRetainedLayerKind.InteractionDetail, interactionPatchSize))
        add(
          GlassRetainedLayer(
            GlassRetainedLayerKind.InteractionDetailCoverage,
            interactionPatchSize,
          ),
        )
        add(GlassRetainedLayer(GlassRetainedLayerKind.InteractionComposite, interactionPatchSize))
      }
    }
    if (interactionLightingActive) {
      add(GlassRetainedLayer(GlassRetainedLayerKind.InteractionLighting, interactionPatchSize))
    }
    if (groupCompositeSize != null) {
      add(GlassRetainedLayer(GlassRetainedLayerKind.GroupComposite, groupCompositeSize))
    }
  },
)

private fun buildGlassFusedLayerPlan(
  sampleSize: IntSize,
  rimActive: Boolean,
  interactionPatchSize: IntSize = IntSize.Zero,
  interactionLightingActive: Boolean = false,
  groupCompositeSize: IntSize?,
): GlassRetainedLayerPlan = GlassRetainedLayerPlan(
  buildList {
    add(GlassRetainedLayer(GlassRetainedLayerKind.Source, sampleSize))
    add(GlassRetainedLayer(GlassRetainedLayerKind.Optical, sampleSize))
    if (rimActive) add(GlassRetainedLayer(GlassRetainedLayerKind.Rim, sampleSize))
    if (interactionLightingActive) {
      add(GlassRetainedLayer(GlassRetainedLayerKind.InteractionLighting, interactionPatchSize))
    }
    if (groupCompositeSize != null) {
      add(GlassRetainedLayer(GlassRetainedLayerKind.GroupComposite, groupCompositeSize))
    }
  },
)

private fun GlassRenderParams.isRefractionDetailActive(): Boolean = isGlassRefractionDetailActive(
  refractionStrength = refractionStrength,
  refractionScalePx = refractionScalePx,
  refractionHeightPx = refractionHeightPx,
  edgeSoftnessPx = edgeSoftnessPx,
  sampleStepPx = sampleStepPx,
  detailIntensity = refractionDetailIntensity,
)

internal fun isGlassRefractionDetailActive(
  refractionStrength: Float,
  refractionScalePx: Float,
  refractionHeightPx: Float,
  edgeSoftnessPx: Float,
  sampleStepPx: Float,
  detailIntensity: Float = GLASS_REFRACTION_DETAIL_INTENSITY,
): Boolean {
  if (refractionStrength <= 0f || refractionScalePx <= 0f) return false
  val detailWidthPx = calculateRefractionDetailWidthPx(
    refractionHeightPx = refractionHeightPx,
    edgeSoftnessPx = edgeSoftnessPx,
    sampleStepPx = sampleStepPx,
  )
  val detailVisibility = calculateRefractionDetailVisibility(
    refractionStrength = refractionStrength,
    refractionScalePx = refractionScalePx,
    sampleStepPx = sampleStepPx,
  )
  return detailWidthPx > 0f && detailIntensity * detailVisibility > 1f / 255f
}

internal data class GlassPreparedRender(
  val params: GlassRenderParams,
  val interactionUniforms: GlassInteractionUniforms,
  val interactionTopology: GlassInteractionTopology,
  val interactionPatchSize: IntSize,
  val plan: GlassRetainedLayerPlan,
  val alpha: Float,
  val groupCompositeSize: IntSize?,
  val blurKey: GlassBlurEffectKey?,
  val opticalKey: GlassOpticalEffectKey,
  val refractionDetailKey: GlassRefractionDetailEffectKey?,
  val rimKey: GlassRimEffectKey?,
)

internal fun resolveGlassGroupCompositeSize(
  outputSize: IntSize,
  alpha: Float,
  interactionLayersActive: Boolean,
  interactionTopology: GlassInteractionTopology,
): IntSize? = outputSize.takeIf {
  requiresGlassGroupAlpha(alpha) ||
    !supportsFusedGlassRenderEffect &&
    interactionLayersActive &&
    interactionTopology.hasOptics
}

internal fun buildGlassPreparedRender(
  params: GlassRenderParams,
  interactionUniforms: GlassInteractionUniforms,
  interactionTopology: GlassInteractionTopology = GlassInteractionTopology(
    hasOptics = interactionUniforms.hasOptics,
    hasLighting = interactionUniforms.hasLighting,
    maxRefractionMultiplier = interactionUniforms.refractionMultiplier,
  ),
  interactionRadiusFraction: Float =
    interactionUniforms.radiusPx / params.coordinates.materialSize.minDimension,
  alpha: Float,
  outputSize: IntSize,
  previous: GlassPreparedRender? = null,
): GlassPreparedRender {
  val interactionPatchSize = if (
    supportsFusedGlassRenderEffect && !interactionTopology.hasLighting
  ) {
    IntSize.Zero
  } else {
    calculateGlassInteractionPatchSize(
      params = params,
      radiusFraction = interactionRadiusFraction,
      topology = interactionTopology,
    )
  }
  val interactionLayersActive = interactionPatchSize.width > 0 && interactionPatchSize.height > 0
  val groupCompositeSize = resolveGlassGroupCompositeSize(
    outputSize = outputSize,
    alpha = alpha,
    interactionLayersActive = interactionLayersActive,
    interactionTopology = interactionTopology,
  )
  val blurKey = if (params.depth > 0f && params.blurRadiusPx > 0f) {
    if (previous != null && previous.params.hasSameBlurEffectInputs(params)) {
      previous.blurKey ?: params.blurEffectKey()
    } else {
      params.blurEffectKey()
    }
  } else {
    null
  }
  val opticalKey = if (previous != null && previous.params.hasSameOpticalEffectInputs(params)) {
    previous.opticalKey
  } else {
    params.opticalEffectKey()
  }
  val refractionDetailKey = if (
    previous != null && previous.params.hasSameRefractionDetailEffectInputs(params)
  ) {
    previous.refractionDetailKey
  } else {
    params.activeRefractionDetailEffectKey(params.refractionDetailIntensity)
  }
  val rimKey = if (previous != null && previous.params.hasSameRimEffectInputs(params)) {
    previous.rimKey
  } else {
    params.rimEffectKey().takeIf { params.specularIntensity > 0f }
  }
  val plan = if (
    previous != null && previous.hasSameRetainedLayerPlanInputs(
      params = params,
      interactionTopology = interactionTopology,
      interactionPatchSize = interactionPatchSize,
      blurKey = blurKey,
      refractionDetailKey = refractionDetailKey,
      rimKey = rimKey,
      groupCompositeSize = groupCompositeSize,
    )
  ) {
    previous.plan
  } else {
    if (supportsFusedGlassRenderEffect) {
      buildGlassFusedLayerPlan(
        sampleSize = params.coordinates.sampleSize.roundToIntSize(),
        rimActive = rimKey != null,
        interactionPatchSize = interactionPatchSize,
        interactionLightingActive = interactionLayersActive && interactionTopology.hasLighting,
        groupCompositeSize = groupCompositeSize,
      )
    } else {
      buildGlassRetainedLayerPlan(
        sampleSize = params.coordinates.sampleSize.roundToIntSize(),
        blurWorkingSize = blurKey?.plan?.workingSize,
        blurRequiresPrefilter = blurKey?.plan?.requiresPrefilter == true,
        depthMixActive = blurKey != null && params.depth < 1f,
        refractionDetailActive = refractionDetailKey != null,
        rimActive = rimKey != null,
        interactionPatchSize = interactionPatchSize,
        interactionOpticsActive = interactionLayersActive && interactionTopology.hasOptics,
        interactionLightingActive = interactionLayersActive && interactionTopology.hasLighting,
        groupCompositeSize = groupCompositeSize,
      )
    }
  }
  return GlassPreparedRender(
    params = params,
    interactionUniforms = interactionUniforms,
    interactionTopology = interactionTopology,
    interactionPatchSize = interactionPatchSize,
    plan = plan,
    alpha = alpha,
    groupCompositeSize = groupCompositeSize,
    blurKey = blurKey,
    opticalKey = opticalKey,
    refractionDetailKey = refractionDetailKey,
    rimKey = rimKey,
  )
}

private fun GlassRenderParams.hasSameBlurEffectInputs(other: GlassRenderParams): Boolean =
  coordinates == other.coordinates &&
    blurRadiusPx == other.blurRadiusPx &&
    blurSigmaPx == other.blurSigmaPx &&
    progressive == other.progressive

private fun GlassRenderParams.hasSameOpticalEffectInputs(other: GlassRenderParams): Boolean =
  coordinates == other.coordinates &&
    refractionStrength == other.refractionStrength &&
    ambientResponse == other.ambientResponse &&
    tint == other.tint &&
    edgeSoftnessPx == other.edgeSoftnessPx &&
    refractionHeightPx == other.refractionHeightPx &&
    chromaticAberrationStrength == other.chromaticAberrationStrength &&
    surfaceProfile == other.surfaceProfile &&
    chromaticAberrationMode == other.chromaticAberrationMode &&
    contrast == other.contrast &&
    whitePoint == other.whitePoint &&
    chromaMultiplier == other.chromaMultiplier &&
    refractionScalePx == other.refractionScalePx &&
    contentNormalBlend == other.contentNormalBlend &&
    fresnelExponent == other.fresnelExponent &&
    geometryToneGain == other.geometryToneGain &&
    geometryNeutralLift == other.geometryNeutralLift &&
    cornerRadii == other.cornerRadii &&
    sampleStepPx == other.sampleStepPx

private fun GlassRenderParams.hasSameRefractionDetailEffectInputs(
  other: GlassRenderParams,
): Boolean =
  coordinates == other.coordinates &&
    refractionStrength == other.refractionStrength &&
    refractionHeightPx == other.refractionHeightPx &&
    refractionScalePx == other.refractionScalePx &&
    surfaceProfile == other.surfaceProfile &&
    edgeSoftnessPx == other.edgeSoftnessPx &&
    cornerRadii == other.cornerRadii &&
    sampleStepPx == other.sampleStepPx &&
    refractionDetailIntensity == other.refractionDetailIntensity

private fun GlassRenderParams.hasSameRimEffectInputs(other: GlassRenderParams): Boolean =
  coordinates == other.coordinates &&
    specularIntensity == other.specularIntensity &&
    specularExponent == other.specularExponent &&
    edgeSoftnessPx == other.edgeSoftnessPx &&
    cornerRadii == other.cornerRadii &&
    lightPosition == other.lightPosition &&
    sampleStepPx == other.sampleStepPx

private fun GlassPreparedRender.hasSameRetainedLayerPlanInputs(
  params: GlassRenderParams,
  interactionTopology: GlassInteractionTopology,
  interactionPatchSize: IntSize,
  blurKey: GlassBlurEffectKey?,
  refractionDetailKey: GlassRefractionDetailEffectKey?,
  rimKey: GlassRimEffectKey?,
  groupCompositeSize: IntSize?,
): Boolean =
  this.params.coordinates.sampleSize == params.coordinates.sampleSize &&
    this.blurKey?.plan?.workingSize == blurKey?.plan?.workingSize &&
    this.blurKey?.plan?.requiresPrefilter == blurKey?.plan?.requiresPrefilter &&
    (this.blurKey != null && this.params.depth < 1f) ==
    (blurKey != null && params.depth < 1f) &&
    (this.refractionDetailKey != null) == (refractionDetailKey != null) &&
    (this.rimKey != null) == (rimKey != null) &&
    this.interactionTopology == interactionTopology &&
    this.interactionPatchSize == interactionPatchSize &&
    this.groupCompositeSize == groupCompositeSize
