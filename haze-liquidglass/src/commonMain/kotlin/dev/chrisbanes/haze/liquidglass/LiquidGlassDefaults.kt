// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

public object LiquidGlassDefaults {
  // Based on Apple's Liquid Glass and https://kube.io/blog/liquid-glass-css-svg/
  public fun refractionStrength(): Float = 0.7f // Typical range: 0.7-1.0
  public fun specularIntensity(): Float = 0.4f // Typical range: 0.2-0.5
  public fun depth(): Float = 0.4f // Blur/depth mixing
  public fun ambientResponse(): Float = 0.5f // Fresnel edge glow
  public fun tint(): Color = Color.White.copy(alpha = 0.12f) // Glass tint opacity
  public fun edgeSoftness(): Dp = 12.dp // Smooth edge falloff
  public fun blurRadius(): Dp = 4.dp // Blur radius for glass depth effect
}
