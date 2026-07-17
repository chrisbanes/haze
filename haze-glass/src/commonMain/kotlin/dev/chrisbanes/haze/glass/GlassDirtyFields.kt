// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.InternalHazeApi

@Suppress("ConstPropertyName", "ktlint:standard:property-naming")
@OptIn(InternalHazeApi::class)
internal object GlassDirtyFields {
  const val Optics: Int = 0b1
  const val SpecularIntensity: Int = Optics shl 1
  const val AmbientResponse: Int = SpecularIntensity shl 1
  const val Tint: Int = AmbientResponse shl 1
  const val EdgeSoftness: Int = Tint shl 1
  const val LightPosition: Int = EdgeSoftness shl 1
  const val ChromaticAberration: Int = LightPosition shl 1
  const val Shape: Int = ChromaticAberration shl 1
  const val SurfaceProfile: Int = Shape shl 1
  const val ChromaticAberrationMode: Int = SurfaceProfile shl 1
  const val Alpha: Int = ChromaticAberrationMode shl 1
  const val Contrast: Int = Alpha shl 1
  const val WhitePoint: Int = Contrast shl 1
  const val ChromaMultiplier: Int = WhitePoint shl 1
  const val ContentNormalBlend: Int = ChromaMultiplier shl 1
  const val SpecularExponent: Int = ContentNormalBlend shl 1
  const val FresnelExponent: Int = SpecularExponent shl 1
  const val Style: Int = FresnelExponent shl 1

  const val InvalidateFlags: Int =
    Optics or
      SpecularIntensity or
      AmbientResponse or
      Tint or
      EdgeSoftness or
      LightPosition or
      ChromaticAberration or
      Shape or
      SurfaceProfile or
      ChromaticAberrationMode or
      Alpha or
      Contrast or
      WhitePoint or
      ChromaMultiplier or
      ContentNormalBlend or
      SpecularExponent or
      FresnelExponent or
      Style

  const val LayerBoundsFlags: Int =
    Optics or ChromaticAberration or EdgeSoftness or Shape

  fun stringify(dirtyTracker: Bitmask): String {
    val params = buildList {
      if (Optics in dirtyTracker) add("Optics")
      if (SpecularIntensity in dirtyTracker) add("SpecularIntensity")
      if (AmbientResponse in dirtyTracker) add("AmbientResponse")
      if (Tint in dirtyTracker) add("Tint")
      if (EdgeSoftness in dirtyTracker) add("EdgeSoftness")
      if (LightPosition in dirtyTracker) add("LightPosition")
      if (ChromaticAberration in dirtyTracker) add("ChromaticAberration")
      if (Shape in dirtyTracker) add("Shape")
      if (SurfaceProfile in dirtyTracker) add("SurfaceProfile")
      if (ChromaticAberrationMode in dirtyTracker) add("ChromaticAberrationMode")
      if (Alpha in dirtyTracker) add("Alpha")
      if (Contrast in dirtyTracker) add("Contrast")
      if (WhitePoint in dirtyTracker) add("WhitePoint")
      if (ChromaMultiplier in dirtyTracker) add("ChromaMultiplier")
      if (ContentNormalBlend in dirtyTracker) add("ContentNormalBlend")
      if (SpecularExponent in dirtyTracker) add("SpecularExponent")
      if (FresnelExponent in dirtyTracker) add("FresnelExponent")
      if (Style in dirtyTracker) add("Style")
    }
    return params.joinToString(separator = ", ", prefix = "[", postfix = "]")
  }
}
