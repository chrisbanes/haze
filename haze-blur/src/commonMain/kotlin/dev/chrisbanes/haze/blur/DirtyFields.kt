// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.InternalHazeApi

@Suppress("ConstPropertyName", "ktlint:standard:property-naming")
@OptIn(InternalHazeApi::class)
internal object DirtyFields {
  const val BlurEnabled: Int = 0b1
  const val BlurRadius: Int = BlurEnabled shl 1
  const val NoiseFactor: Int = BlurRadius shl 1
  const val Mask: Int = NoiseFactor shl 1
  const val BackgroundColor: Int = Mask shl 1
  const val Tints: Int = BackgroundColor shl 1
  const val FallbackTint: Int = Tints shl 1
  const val Alpha: Int = FallbackTint shl 1
  const val Progressive: Int = Alpha shl 1
  const val BlurredEdgeTreatment: Int = Progressive shl 1

  const val RenderEffectAffectingFlags: Int =
    BlurEnabled or
      BlurRadius or
      NoiseFactor or
      Mask or
      Tints or
      FallbackTint or
      Progressive or
      BlurredEdgeTreatment

  const val InvalidateFlags: Int =
    RenderEffectAffectingFlags or
      BlurEnabled or
      BackgroundColor or
      Progressive or
      Alpha or
      BlurredEdgeTreatment

  fun stringify(dirtyTracker: Bitmask): String {
    val params = buildList {
      if (BlurEnabled in dirtyTracker) add("BlurEnabled")
      if (BlurRadius in dirtyTracker) add("BlurRadius")
      if (NoiseFactor in dirtyTracker) add("NoiseFactor")
      if (Mask in dirtyTracker) add("Mask")
      if (BackgroundColor in dirtyTracker) add("BackgroundColor")
      if (Tints in dirtyTracker) add("Tints")
      if (FallbackTint in dirtyTracker) add("FallbackTint")
      if (Alpha in dirtyTracker) add("Alpha")
      if (Progressive in dirtyTracker) add("Progressive")
      if (BlurredEdgeTreatment in dirtyTracker) add("BlurredEdgeTreatment")
    }
    return params.joinToString(separator = ", ", prefix = "[", postfix = "]")
  }
}
