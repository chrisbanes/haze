// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.BlendMode as AndroidBlendMode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.BlendMode

/**
 * Converts a Compose [BlendMode] to Android's [AndroidBlendMode].
 */
@RequiresApi(Build.VERSION_CODES.Q)
@InternalHazeApi
public fun BlendMode.toAndroidBlendMode(): AndroidBlendMode = when (this) {
  BlendMode.Clear -> AndroidBlendMode.CLEAR
  BlendMode.Src -> AndroidBlendMode.SRC
  BlendMode.Dst -> AndroidBlendMode.DST
  BlendMode.SrcOver -> AndroidBlendMode.SRC_OVER
  BlendMode.DstOver -> AndroidBlendMode.DST_OVER
  BlendMode.SrcIn -> AndroidBlendMode.SRC_IN
  BlendMode.DstIn -> AndroidBlendMode.DST_IN
  BlendMode.SrcOut -> AndroidBlendMode.SRC_OUT
  BlendMode.DstOut -> AndroidBlendMode.DST_OUT
  BlendMode.SrcAtop -> AndroidBlendMode.SRC_ATOP
  BlendMode.DstAtop -> AndroidBlendMode.DST_ATOP
  BlendMode.Xor -> AndroidBlendMode.XOR
  BlendMode.Plus -> AndroidBlendMode.PLUS
  BlendMode.Modulate -> AndroidBlendMode.MODULATE
  BlendMode.Screen -> AndroidBlendMode.SCREEN
  BlendMode.Overlay -> AndroidBlendMode.OVERLAY
  BlendMode.Darken -> AndroidBlendMode.DARKEN
  BlendMode.Lighten -> AndroidBlendMode.LIGHTEN
  BlendMode.ColorDodge -> AndroidBlendMode.COLOR_DODGE
  BlendMode.ColorBurn -> AndroidBlendMode.COLOR_BURN
  BlendMode.Hardlight -> AndroidBlendMode.HARD_LIGHT
  BlendMode.Softlight -> AndroidBlendMode.SOFT_LIGHT
  BlendMode.Difference -> AndroidBlendMode.DIFFERENCE
  BlendMode.Exclusion -> AndroidBlendMode.EXCLUSION
  BlendMode.Multiply -> AndroidBlendMode.MULTIPLY
  BlendMode.Hue -> AndroidBlendMode.HUE
  BlendMode.Saturation -> AndroidBlendMode.SATURATION
  BlendMode.Color -> AndroidBlendMode.COLOR
  BlendMode.Luminosity -> AndroidBlendMode.LUMINOSITY
  else -> AndroidBlendMode.SRC_OVER
}
