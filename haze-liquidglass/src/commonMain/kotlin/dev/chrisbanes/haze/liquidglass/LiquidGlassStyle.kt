// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * A [ProvidableCompositionLocal] which provides the default [LiquidGlassStyle] for all
 * [dev.chrisbanes.haze.hazeEffect] layout nodes placed within this composition local's content.
 */
public val LocalLiquidGlassStyle: ProvidableCompositionLocal<LiquidGlassStyle> =
  compositionLocalOf { LiquidGlassDefaults.style }

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
  val refractionHeight: Float = Float.NaN,
  val chromaticAberrationStrength: Float = Float.NaN,
  val shape: RoundedCornerShape? = null,
) {
  public companion object {
    public val Unspecified: LiquidGlassStyle = LiquidGlassStyle()
  }
}
