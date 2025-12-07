// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.BlendMode

/**
 * Sealed interface representing supported blend modes for platform operations.
 */
@InternalHazeApi
public sealed interface HazeBlendMode {
  public data object Clear : HazeBlendMode
  public data object Src : HazeBlendMode
  public data object Dst : HazeBlendMode
  public data object SrcOver : HazeBlendMode
  public data object DstOver : HazeBlendMode
  public data object SrcIn : HazeBlendMode
  public data object DstIn : HazeBlendMode
  public data object SrcOut : HazeBlendMode
  public data object DstOut : HazeBlendMode
  public data object SrcAtop : HazeBlendMode
  public data object DstAtop : HazeBlendMode
  public data object Xor : HazeBlendMode
  public data object Plus : HazeBlendMode
  public data object Modulate : HazeBlendMode
  public data object Screen : HazeBlendMode
  public data object Overlay : HazeBlendMode
  public data object Darken : HazeBlendMode
  public data object Lighten : HazeBlendMode
  public data object ColorDodge : HazeBlendMode
  public data object ColorBurn : HazeBlendMode
  public data object Hardlight : HazeBlendMode
  public data object Softlight : HazeBlendMode
  public data object Difference : HazeBlendMode
  public data object Exclusion : HazeBlendMode
  public data object Multiply : HazeBlendMode
  public data object Hue : HazeBlendMode
  public data object Saturation : HazeBlendMode
  public data object Color : HazeBlendMode
  public data object Luminosity : HazeBlendMode

  public companion object {
    /**
     * Converts a Compose [BlendMode] to a [HazeBlendMode].
     */
    public fun from(blendMode: BlendMode): HazeBlendMode = when (blendMode) {
      BlendMode.Clear -> Clear
      BlendMode.Src -> Src
      BlendMode.Dst -> Dst
      BlendMode.SrcOver -> SrcOver
      BlendMode.DstOver -> DstOver
      BlendMode.SrcIn -> SrcIn
      BlendMode.DstIn -> DstIn
      BlendMode.SrcOut -> SrcOut
      BlendMode.DstOut -> DstOut
      BlendMode.SrcAtop -> SrcAtop
      BlendMode.DstAtop -> DstAtop
      BlendMode.Xor -> Xor
      BlendMode.Plus -> Plus
      BlendMode.Modulate -> Modulate
      BlendMode.Screen -> Screen
      BlendMode.Overlay -> Overlay
      BlendMode.Darken -> Darken
      BlendMode.Lighten -> Lighten
      BlendMode.ColorDodge -> ColorDodge
      BlendMode.ColorBurn -> ColorBurn
      BlendMode.Hardlight -> Hardlight
      BlendMode.Softlight -> Softlight
      BlendMode.Difference -> Difference
      BlendMode.Exclusion -> Exclusion
      BlendMode.Multiply -> Multiply
      BlendMode.Hue -> Hue
      BlendMode.Saturation -> Saturation
      BlendMode.Color -> Color
      BlendMode.Luminosity -> Luminosity
      else -> SrcOver
    }
  }
}

/**
 * Converts a Compose [BlendMode] to a [HazeBlendMode].
 */
@InternalHazeApi
public fun BlendMode.toHazeBlendMode(): HazeBlendMode = HazeBlendMode.from(this)
