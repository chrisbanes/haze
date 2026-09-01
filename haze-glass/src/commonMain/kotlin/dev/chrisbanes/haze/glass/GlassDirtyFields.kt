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
  const val BackgroundColor: Int = AmbientResponse shl 1
  const val Tint: Int = BackgroundColor shl 1
  const val EdgeSoftness: Int = Tint shl 1
  const val EdgeShadow: Int = EdgeSoftness shl 1
  const val LightPosition: Int = EdgeShadow shl 1
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
  const val InteractionLayerBounds: Int = Style shl 1
  const val Interaction: Int = InteractionLayerBounds shl 1
  const val RuntimeEffectFactory: Int = Interaction shl 1
  const val PerformanceMode: Int = RuntimeEffectFactory shl 1
  const val Accessibility: Int = PerformanceMode shl 1

  const val InvalidateFlags: Int =
    Optics or
      SpecularIntensity or
      AmbientResponse or
      BackgroundColor or
      Tint or
      EdgeSoftness or
      EdgeShadow or
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
      Style or
      Interaction or
      RuntimeEffectFactory or
      PerformanceMode or
      Accessibility

  const val LayerBoundsFlags: Int =
    Optics or ChromaticAberration or EdgeSoftness or Shape or InteractionLayerBounds or Accessibility

  const val All: Int = InvalidateFlags or LayerBoundsFlags

  const val StyleResolutionFlags: Int = InvalidateFlags and Interaction.inv()
  const val ClipDecisionFlags: Int = Shape or EdgeSoftness

  fun stringify(dirtyTracker: Bitmask): String {
    val params = buildList {
      if (Optics in dirtyTracker) add("Optics")
      if (SpecularIntensity in dirtyTracker) add("SpecularIntensity")
      if (AmbientResponse in dirtyTracker) add("AmbientResponse")
      if (BackgroundColor in dirtyTracker) add("BackgroundColor")
      if (Tint in dirtyTracker) add("Tint")
      if (EdgeSoftness in dirtyTracker) add("EdgeSoftness")
      if (EdgeShadow in dirtyTracker) add("EdgeShadow")
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
      if (InteractionLayerBounds in dirtyTracker) add("InteractionLayerBounds")
      if (Interaction in dirtyTracker) add("Interaction")
      if (RuntimeEffectFactory in dirtyTracker) add("RuntimeEffectFactory")
      if (PerformanceMode in dirtyTracker) add("PerformanceMode")
      if (Accessibility in dirtyTracker) add("Accessibility")
    }
    return params.joinToString(separator = ", ", prefix = "[", postfix = "]")
  }
}
