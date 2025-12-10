// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Dp.Companion.Unspecified

@Immutable
public data class LiquidGlassStyle(
  val tint: Color = Color.Unspecified,
  val refractionStrength: Float = Float.NaN,
  val specularIntensity: Float = Float.NaN,
  val depth: Float = Float.NaN,
  val ambientResponse: Float = Float.NaN,
  val edgeSoftness: Dp = Dp.Unspecified,
  val lightPosition: Offset = Offset.Unspecified,
  val blurRadius: Dp = Dp.Unspecified,
) {
  public companion object {
    public val Unspecified: LiquidGlassStyle = LiquidGlassStyle()
  }
}
