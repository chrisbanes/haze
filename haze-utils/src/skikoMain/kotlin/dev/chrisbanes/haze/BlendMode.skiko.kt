// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.BlendMode
import org.jetbrains.skia.BlendMode as SkiaBlendMode

/**
 * Converts a Compose [BlendMode] to Skia's [SkiaBlendMode].
 */
@InternalHazeApi
public fun BlendMode.toSkiaBlendMode(): SkiaBlendMode = when (this) {
  BlendMode.Clear -> SkiaBlendMode.CLEAR
  BlendMode.Src -> SkiaBlendMode.SRC
  BlendMode.Dst -> SkiaBlendMode.DST
  BlendMode.SrcOver -> SkiaBlendMode.SRC_OVER
  BlendMode.DstOver -> SkiaBlendMode.DST_OVER
  BlendMode.SrcIn -> SkiaBlendMode.SRC_IN
  BlendMode.DstIn -> SkiaBlendMode.DST_IN
  BlendMode.SrcOut -> SkiaBlendMode.SRC_OUT
  BlendMode.DstOut -> SkiaBlendMode.DST_OUT
  BlendMode.SrcAtop -> SkiaBlendMode.SRC_ATOP
  BlendMode.DstAtop -> SkiaBlendMode.DST_ATOP
  BlendMode.Xor -> SkiaBlendMode.XOR
  BlendMode.Plus -> SkiaBlendMode.PLUS
  BlendMode.Modulate -> SkiaBlendMode.MODULATE
  BlendMode.Screen -> SkiaBlendMode.SCREEN
  BlendMode.Overlay -> SkiaBlendMode.OVERLAY
  BlendMode.Darken -> SkiaBlendMode.DARKEN
  BlendMode.Lighten -> SkiaBlendMode.LIGHTEN
  BlendMode.ColorDodge -> SkiaBlendMode.COLOR_DODGE
  BlendMode.ColorBurn -> SkiaBlendMode.COLOR_BURN
  BlendMode.Hardlight -> SkiaBlendMode.HARD_LIGHT
  BlendMode.Softlight -> SkiaBlendMode.SOFT_LIGHT
  BlendMode.Difference -> SkiaBlendMode.DIFFERENCE
  BlendMode.Exclusion -> SkiaBlendMode.EXCLUSION
  BlendMode.Multiply -> SkiaBlendMode.MULTIPLY
  BlendMode.Hue -> SkiaBlendMode.HUE
  BlendMode.Saturation -> SkiaBlendMode.SATURATION
  BlendMode.Color -> SkiaBlendMode.COLOR
  BlendMode.Luminosity -> SkiaBlendMode.LUMINOSITY
  else -> SkiaBlendMode.SRC_OVER
}
