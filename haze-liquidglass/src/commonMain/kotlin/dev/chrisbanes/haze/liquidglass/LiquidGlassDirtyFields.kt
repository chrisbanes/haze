// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.InternalHazeApi

@Suppress("ConstPropertyName", "ktlint:standard:property-naming")
@OptIn(InternalHazeApi::class)
internal object LiquidGlassDirtyFields {
  const val RefractionStrength: Int = 0b1
  const val SpecularIntensity: Int = RefractionStrength shl 1
  const val Depth: Int = SpecularIntensity shl 1
  const val AmbientResponse: Int = Depth shl 1
  const val Tint: Int = AmbientResponse shl 1
  const val EdgeSoftness: Int = Tint shl 1
  const val LightPosition: Int = EdgeSoftness shl 1
  const val BlurRadius: Int = LightPosition shl 1
  const val RefractionHeight: Int = BlurRadius shl 1
  const val ChromaticAberration: Int = RefractionHeight shl 1
  const val Shape: Int = ChromaticAberration shl 1
  const val Alpha: Int = Shape shl 1
  const val Style: Int = Alpha shl 1

  const val InvalidateFlags: Int =
    RefractionStrength or
      SpecularIntensity or
      Depth or
      AmbientResponse or
      Tint or
      EdgeSoftness or
      LightPosition or
      BlurRadius or
      RefractionHeight or
      ChromaticAberration or
      Shape or
      Alpha or
      Style

  fun stringify(dirtyTracker: Bitmask): String {
    val params = buildList {
      if (RefractionStrength in dirtyTracker) add("RefractionStrength")
      if (SpecularIntensity in dirtyTracker) add("SpecularIntensity")
      if (Depth in dirtyTracker) add("Depth")
      if (AmbientResponse in dirtyTracker) add("AmbientResponse")
      if (Tint in dirtyTracker) add("Tint")
      if (EdgeSoftness in dirtyTracker) add("EdgeSoftness")
      if (LightPosition in dirtyTracker) add("LightPosition")
      if (BlurRadius in dirtyTracker) add("BlurRadius")
      if (RefractionHeight in dirtyTracker) add("RefractionHeight")
      if (ChromaticAberration in dirtyTracker) add("ChromaticAberration")
      if (Shape in dirtyTracker) add("Shape")
      if (Alpha in dirtyTracker) add("Alpha")
      if (Style in dirtyTracker) add("Style")
    }
    return params.joinToString(separator = ", ", prefix = "[", postfix = "]")
  }
}
