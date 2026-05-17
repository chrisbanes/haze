// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi

@ExperimentalHazeApi
@Suppress("ConstPropertyName", "ktlint:standard:property-naming")
public object LiquidGlassDefaults {
  // Tuned defaults for the project's Liquid Glass effect.
  public const val refractionStrength: Float = 0.7f // Typical range: 0.7-1.0
  public const val specularIntensity: Float = 0.4f // Typical range: 0.2-0.5
  public const val depth: Float = 0.4f // Blur/depth mixing
  public const val ambientResponse: Float = 0.5f // Fresnel edge glow
  public val tint: Color = Color.White.copy(alpha = 0.12f) // Glass tint opacity
  public val edgeSoftness: Dp = 12.dp // Smooth edge falloff
  public val blurRadius: Dp = 4.dp // Blur radius for glass depth effect
  public const val refractionHeight: Float = 0.25f // Fraction of min dimension used for refraction
  public const val chromaticAberrationStrength: Float = 0f // 0 = off, 1 = strong dispersion
  public val shape: RoundedCornerShape = RoundedCornerShape(16.dp)
  public val surfaceProfile: SurfaceProfile = SurfaceProfile.Circle
  public val chromaticAberrationMode: ChromaticAberrationMode = ChromaticAberrationMode.Simple
  public const val alpha: Float = 1f // Fully opaque
  public const val contrast: Float = 0f // -1..1 range
  public const val whitePoint: Float = 0f // -1..1 range
  public const val chromaMultiplier: Float = 1f // 0..2 range
  public const val refractionScale: Float = 12f
  public const val contentNormalBlend: Float = 0.15f
  public const val specularExponent: Float = 24f
  public const val fresnelExponent: Float = 3f

  /**
   * Default [dev.chrisbanes.haze.liquidglass.LiquidGlassStyle] for usage with [LiquidGlassVisualEffect].
   */
  public val style: LiquidGlassStyle = LiquidGlassStyle(
    tint = tint,
    refractionStrength = refractionStrength,
    specularIntensity = specularIntensity,
    depth = depth,
    ambientResponse = ambientResponse,
    edgeSoftness = edgeSoftness,
    blurRadius = blurRadius,
    refractionHeight = refractionHeight,
    chromaticAberrationStrength = chromaticAberrationStrength,
    alpha = alpha,
    contrast = contrast,
    whitePoint = whitePoint,
    chromaMultiplier = chromaMultiplier,
    refractionScale = refractionScale,
    contentNormalBlend = contentNormalBlend,
    specularExponent = specularExponent,
    fresnelExponent = fresnelExponent,
    shape = shape,
    surfaceProfile = surfaceProfile,
    chromaticAberrationMode = chromaticAberrationMode,
  )
}
