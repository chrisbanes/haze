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
import dev.chrisbanes.haze.ExperimentalHazeApi

/**
 * A [ProvidableCompositionLocal] which provides the default [LiquidGlassStyle] for all
 * [dev.chrisbanes.haze.hazeEffect] layout nodes placed within this composition local's content.
 */
@ExperimentalHazeApi
public val LocalLiquidGlassStyle: ProvidableCompositionLocal<LiquidGlassStyle> =
  compositionLocalOf { LiquidGlassDefaults.style }

@ExperimentalHazeApi
@Immutable
public data class LiquidGlassStyle(
  val tint: Color = Color.Unspecified,
  val shape: RoundedCornerShape? = null,
  val optics: LiquidGlassOptics = LiquidGlassOptics.Unspecified,
  val lighting: LiquidGlassLighting = LiquidGlassLighting.Unspecified,
  val color: LiquidGlassColor = LiquidGlassColor.Unspecified,
  val rendering: LiquidGlassRendering = LiquidGlassRendering.Unspecified,
) {
  public companion object {
    public val Unspecified: LiquidGlassStyle = LiquidGlassStyle()
  }
}

@ExperimentalHazeApi
@Immutable
public data class LiquidGlassOptics(
  val refractionStrength: Float = Float.NaN,
  val refractionHeight: Float = Float.NaN,
  val refractionScale: Float = Float.NaN,
  val depth: Float = Float.NaN,
  val blurRadius: Dp = Dp.Unspecified,
) {
  public companion object {
    public val Unspecified: LiquidGlassOptics = LiquidGlassOptics()
  }
}

@ExperimentalHazeApi
@Immutable
public data class LiquidGlassLighting(
  val specularIntensity: Float = Float.NaN,
  val specularExponent: Float = Float.NaN,
  val fresnelExponent: Float = Float.NaN,
  val ambientResponse: Float = Float.NaN,
  val lightPosition: Offset = Offset.Unspecified,
) {
  public companion object {
    public val Unspecified: LiquidGlassLighting = LiquidGlassLighting()
  }
}

@ExperimentalHazeApi
@Immutable
public data class LiquidGlassColor(
  val alpha: Float = Float.NaN,
  val contrast: Float = Float.NaN,
  val whitePoint: Float = Float.NaN,
  val chromaMultiplier: Float = Float.NaN,
) {
  public companion object {
    public val Unspecified: LiquidGlassColor = LiquidGlassColor()
  }
}

@ExperimentalHazeApi
@Immutable
public data class LiquidGlassRendering(
  val edgeSoftness: Dp = Dp.Unspecified,
  val contentNormalBlend: Float = Float.NaN,
  val surfaceProfile: SurfaceProfile? = null,
  val chromaticAberrationStrength: Float = Float.NaN,
  val chromaticAberrationMode: ChromaticAberrationMode? = null,
) {
  public companion object {
    public val Unspecified: LiquidGlassRendering = LiquidGlassRendering()
  }
}
