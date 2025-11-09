// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.graphics.BlendMode as PlatformBlendMode
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.BlendMode
import dev.chrisbanes.haze.InternalHazeApi

@InternalHazeApi
@RequiresApi(29)
public fun BlendMode.toAndroidBlendMode(): PlatformBlendMode = when {
  this == BlendMode.Clear -> PlatformBlendMode.CLEAR
  this == BlendMode.Color -> PlatformBlendMode.COLOR
  this == BlendMode.ColorBurn -> PlatformBlendMode.COLOR_BURN
  this == BlendMode.ColorDodge -> PlatformBlendMode.COLOR_DODGE
  this == BlendMode.Darken -> PlatformBlendMode.DARKEN
  this == BlendMode.Difference -> PlatformBlendMode.DIFFERENCE
  this == BlendMode.Dst -> PlatformBlendMode.DST
  this == BlendMode.DstAtop -> PlatformBlendMode.DST_ATOP
  this == BlendMode.DstIn -> PlatformBlendMode.DST_IN
  this == BlendMode.DstOut -> PlatformBlendMode.DST_OUT
  this == BlendMode.DstOver -> PlatformBlendMode.DST_OVER
  this == BlendMode.Exclusion -> PlatformBlendMode.EXCLUSION
  this == BlendMode.Hardlight -> PlatformBlendMode.HARD_LIGHT
  this == BlendMode.Hue -> PlatformBlendMode.HUE
  this == BlendMode.Lighten -> PlatformBlendMode.LIGHTEN
  this == BlendMode.Luminosity -> PlatformBlendMode.LUMINOSITY
  this == BlendMode.Modulate -> PlatformBlendMode.MODULATE
  this == BlendMode.Multiply -> PlatformBlendMode.MULTIPLY
  this == BlendMode.Overlay -> PlatformBlendMode.OVERLAY
  this == BlendMode.Saturation -> PlatformBlendMode.SATURATION
  this == BlendMode.Screen -> PlatformBlendMode.SCREEN
  this == BlendMode.Softlight -> PlatformBlendMode.SOFT_LIGHT
  this == BlendMode.Src -> PlatformBlendMode.SRC
  this == BlendMode.SrcAtop -> PlatformBlendMode.SRC_ATOP
  this == BlendMode.SrcIn -> PlatformBlendMode.SRC_IN
  this == BlendMode.SrcOut -> PlatformBlendMode.SRC_OUT
  this == BlendMode.SrcOver -> PlatformBlendMode.SRC_OVER
  else -> PlatformBlendMode.SRC_IN
}
