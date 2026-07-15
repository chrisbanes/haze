// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi

@ExperimentalHazeApi
@Suppress("ConstPropertyName", "ktlint:standard:property-naming")
public object GlassDefaults {
  // Tuned defaults for the project's Glass effect.
  public const val refractionStrength: Float = 0.7f // Regular geometry calibration input
  public const val specularIntensity: Float = 0.4f // Typical range: 0.2-0.5
  public const val depth: Float = 1f // Full semantic blur; geometry controls its radius
  public const val ambientResponse: Float = 0.46f // Fresnel edge glow
  public val tint: Color = Color.Transparent // Untinted Regular glass
  public val edgeSoftness: Dp = 2.dp // Near-hard boundary
  public val blurRadius: Dp = 14.dp // Semantic Regular blur
  public const val refractionHeight: Float = 0.25f // Fraction of min dimension used for refraction
  public const val chromaticAberrationStrength: Float = 0f // 0 = off, 1 = strong dispersion
  public val shape: RoundedCornerShape = RoundedCornerShape(16.dp)
  public val surfaceProfile: SurfaceProfile = SurfaceProfile.Circle
  public val chromaticAberrationMode: ChromaticAberrationMode = ChromaticAberrationMode.Simple
  public const val alpha: Float = 1f // Fully opaque
  public const val contrast: Float = 0f // -1..1 range
  public const val whitePoint: Float = 0f // -1..1 range
  public const val chromaMultiplier: Float = 1f // 0..2 range
  public const val refractionScale: Float = 15f
  public const val contentNormalBlend: Float = 0.15f
  public const val specularExponent: Float = 24f
  public const val fresnelExponent: Float = 3f

  /**
   * Default [dev.chrisbanes.haze.glass.GlassStyle] for usage with [GlassVisualEffect].
   */
  public val style: GlassStyle = GlassStyle(
    tint = tint,
    shape = shape,
    optics = GlassOptics(
      refractionStrength = refractionStrength,
      refractionHeight = refractionHeight,
      refractionScale = refractionScale,
      depth = depth,
      blurRadius = blurRadius,
    ),
    lighting = GlassLighting(
      specularIntensity = specularIntensity,
      specularExponent = specularExponent,
      fresnelExponent = fresnelExponent,
      ambientResponse = ambientResponse,
    ),
    color = GlassColor(
      alpha = alpha,
      contrast = contrast,
      whitePoint = whitePoint,
      chromaMultiplier = chromaMultiplier,
    ),
    rendering = GlassRendering(
      edgeSoftness = edgeSoftness,
      contentNormalBlend = contentNormalBlend,
      surfaceProfile = surfaceProfile,
      chromaticAberrationStrength = chromaticAberrationStrength,
      chromaticAberrationMode = chromaticAberrationMode,
    ),
  )
}
