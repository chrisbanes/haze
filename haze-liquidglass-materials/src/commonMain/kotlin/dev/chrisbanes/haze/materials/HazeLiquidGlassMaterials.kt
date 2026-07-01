// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.materials

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.liquidglass.LiquidGlassColor
import dev.chrisbanes.haze.liquidglass.LiquidGlassLighting
import dev.chrisbanes.haze.liquidglass.LiquidGlassOptics
import dev.chrisbanes.haze.liquidglass.LiquidGlassRendering
import dev.chrisbanes.haze.liquidglass.LiquidGlassStyle
import dev.chrisbanes.haze.liquidglass.SurfaceProfile

@ExperimentalHazeApi
public object HazeLiquidGlassMaterials {

  @Stable
  public val Card: LiquidGlassStyle = LiquidGlassStyle(
    tint = Color.White.copy(alpha = 0.12f),
    shape = RoundedCornerShape(20.dp),
    optics = LiquidGlassOptics(
      refractionStrength = 0.55f,
      refractionHeight = 0.28f,
      refractionScale = 10f,
      depth = 0.65f,
      blurRadius = 16.dp,
    ),
    lighting = LiquidGlassLighting(
      specularIntensity = 0.35f,
      specularExponent = 24f,
      fresnelExponent = 3f,
      ambientResponse = 0.45f,
    ),
    color = LiquidGlassColor(
      alpha = 1f,
      contrast = 0f,
      whitePoint = 0f,
      chromaMultiplier = 1.05f,
    ),
    rendering = LiquidGlassRendering(
      edgeSoftness = 10.dp,
      contentNormalBlend = 0.12f,
      surfaceProfile = SurfaceProfile.Squircle,
      chromaticAberrationStrength = 0f,
    ),
  )

  @Stable
  public val FloatingControl: LiquidGlassStyle = Card.copy(
    tint = Color.White.copy(alpha = 0.16f),
    shape = RoundedCornerShape(999.dp),
    optics = Card.optics.copy(
      refractionStrength = 0.75f,
      refractionHeight = 0.32f,
      depth = 0.7f,
      blurRadius = 20.dp,
    ),
    lighting = Card.lighting.copy(
      specularIntensity = 0.55f,
      ambientResponse = 0.65f,
    ),
  )

  @Stable
  public val Bar: LiquidGlassStyle = Card.copy(
    tint = Color.White.copy(alpha = 0.10f),
    shape = RoundedCornerShape(0.dp),
    optics = Card.optics.copy(
      refractionStrength = 0.35f,
      refractionHeight = 0.18f,
      depth = 0.85f,
      blurRadius = 24.dp,
    ),
    rendering = Card.rendering.copy(
      edgeSoftness = 0.dp,
      surfaceProfile = SurfaceProfile.Circle,
    ),
  )
}
