// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Suppress("ConstPropertyName", "ktlint:standard:property-naming")
public object LiquidGlassDefaults {
  // Based on Apple's Liquid Glass and https://kube.io/blog/liquid-glass-css-svg/
  public const val refractionStrength: Float = 0.7f // Typical range: 0.7-1.0
  public const val specularIntensity: Float = 0.4f // Typical range: 0.2-0.5
  public const val depth: Float = 0.4f // Blur/depth mixing
  public const val ambientResponse: Float = 0.5f // Fresnel edge glow
  public val tint: Color = Color.White.copy(alpha = 0.12f) // Glass tint opacity
  public val edgeSoftness: Dp = 12.dp // Smooth edge falloff
  public val blurRadius: Dp = 4.dp // Blur radius for glass depth effect
}
