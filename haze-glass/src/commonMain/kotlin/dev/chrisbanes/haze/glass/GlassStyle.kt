// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeProgressive

/**
 * A [ProvidableCompositionLocal] which provides the default [GlassStyle] for all
 * [dev.chrisbanes.haze.hazeEffect] layout nodes placed within this composition local's content.
 */
@ExperimentalHazeApi
public val LocalGlassStyle: ProvidableCompositionLocal<GlassStyle> =
  compositionLocalOf { GlassDefaults.style }

@ExperimentalHazeApi
@Immutable
public data class GlassStyle(
  val tint: Color = Color.Unspecified,
  val shape: RoundedCornerShape? = null,
  val optics: GlassOptics = GlassOptics.Unspecified,
  val lighting: GlassLighting = GlassLighting.Unspecified,
  val color: GlassColor = GlassColor.Unspecified,
  val rendering: GlassRendering = GlassRendering.Unspecified,
) {
  public companion object {
    public val Unspecified: GlassStyle = GlassStyle()
  }
}

/**
 * Optical properties for [GlassStyle].
 *
 * @param depth Depth perception factor. Values greater than `0f` require drawing an additional
 * blurred sample for the glass content, which has a rendering cost. Use `0f` when depth layering
 * is not needed.
 */
@ExperimentalHazeApi
@Immutable
public data class GlassOptics(
  val refractionStrength: Float = Float.NaN,
  val refractionHeight: Float = Float.NaN,
  val refractionScale: Float = Float.NaN,
  val depth: Float = Float.NaN,
  val blurRadius: Dp = Dp.Unspecified,
  val progressive: HazeProgressive? = null,
) {
  public companion object {
    public val Unspecified: GlassOptics = GlassOptics()
  }
}

@ExperimentalHazeApi
@Immutable
public data class GlassLighting(
  val specularIntensity: Float = Float.NaN,
  val specularExponent: Float = Float.NaN,
  val fresnelExponent: Float = Float.NaN,
  val ambientResponse: Float = Float.NaN,
  val lightPosition: Offset = Offset.Unspecified,
) {
  public companion object {
    public val Unspecified: GlassLighting = GlassLighting()
  }
}

@ExperimentalHazeApi
@Immutable
public data class GlassColor(
  val alpha: Float = Float.NaN,
  val contrast: Float = Float.NaN,
  val whitePoint: Float = Float.NaN,
  val chromaMultiplier: Float = Float.NaN,
) {
  public companion object {
    public val Unspecified: GlassColor = GlassColor()
  }
}

@ExperimentalHazeApi
@Immutable
public data class GlassRendering(
  val edgeSoftness: Dp = Dp.Unspecified,
  val contentNormalBlend: Float = Float.NaN,
  val surfaceProfile: SurfaceProfile? = null,
  val chromaticAberrationStrength: Float = Float.NaN,
  val chromaticAberrationMode: ChromaticAberrationMode? = null,
) {
  public companion object {
    public val Unspecified: GlassRendering = GlassRendering()
  }
}
