// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.BlendMode
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Converts a [HazeBlendMode] to Android's [BlendMode].
 */
@RequiresApi(Build.VERSION_CODES.Q)
@InternalHazeApi
public fun HazeBlendMode.toAndroidBlendMode(): BlendMode = when (this) {
  HazeBlendMode.Clear -> BlendMode.CLEAR
  HazeBlendMode.Src -> BlendMode.SRC
  HazeBlendMode.Dst -> BlendMode.DST
  HazeBlendMode.SrcOver -> BlendMode.SRC_OVER
  HazeBlendMode.DstOver -> BlendMode.DST_OVER
  HazeBlendMode.SrcIn -> BlendMode.SRC_IN
  HazeBlendMode.DstIn -> BlendMode.DST_IN
  HazeBlendMode.SrcOut -> BlendMode.SRC_OUT
  HazeBlendMode.DstOut -> BlendMode.DST_OUT
  HazeBlendMode.SrcAtop -> BlendMode.SRC_ATOP
  HazeBlendMode.DstAtop -> BlendMode.DST_ATOP
  HazeBlendMode.Xor -> BlendMode.XOR
  HazeBlendMode.Plus -> BlendMode.PLUS
  HazeBlendMode.Modulate -> BlendMode.MODULATE
  HazeBlendMode.Screen -> BlendMode.SCREEN
  HazeBlendMode.Overlay -> BlendMode.OVERLAY
  HazeBlendMode.Darken -> BlendMode.DARKEN
  HazeBlendMode.Lighten -> BlendMode.LIGHTEN
  HazeBlendMode.ColorDodge -> BlendMode.COLOR_DODGE
  HazeBlendMode.ColorBurn -> BlendMode.COLOR_BURN
  HazeBlendMode.Hardlight -> BlendMode.HARD_LIGHT
  HazeBlendMode.Softlight -> BlendMode.SOFT_LIGHT
  HazeBlendMode.Difference -> BlendMode.DIFFERENCE
  HazeBlendMode.Exclusion -> BlendMode.EXCLUSION
  HazeBlendMode.Multiply -> BlendMode.MULTIPLY
  HazeBlendMode.Hue -> BlendMode.HUE
  HazeBlendMode.Saturation -> BlendMode.SATURATION
  HazeBlendMode.Color -> BlendMode.COLOR
  HazeBlendMode.Luminosity -> BlendMode.LUMINOSITY
}
