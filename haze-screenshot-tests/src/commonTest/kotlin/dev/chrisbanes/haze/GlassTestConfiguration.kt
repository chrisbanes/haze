// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassInteractionScope
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformPivot
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.SurfaceProfile
import dev.chrisbanes.haze.glass.hazeGlass as typedHazeGlass

/**
 * Mutable screenshot-test fixture which emits only the public, typed Glass API.
 *
 * The fixture keeps existing multi-frame test setup concise while production and sample code use
 * immutable [GlassStyle] values directly. It owns no renderer, lifecycle, or cache state.
 */
internal class GlassTestConfiguration {
  var style: GlassStyle by mutableStateOf(GlassStyle)

  private var hasOpticsOverride by mutableStateOf(false)
  private var opticsOverride by mutableStateOf(GlassDefaults.optics)
  var optics: GlassOptics
    get() = opticsOverride
    set(value) {
      hasOpticsOverride = true
      opticsOverride = value
    }

  private var hasSpecularIntensityOverride by mutableStateOf(false)
  private var specularIntensityOverride by mutableStateOf(GlassDefaults.specularIntensity)
  var specularIntensity: Float
    get() = specularIntensityOverride
    set(value) {
      hasSpecularIntensityOverride = true
      specularIntensityOverride = value
    }

  private var hasAmbientResponseOverride by mutableStateOf(false)
  private var ambientResponseOverride by mutableStateOf(GlassDefaults.ambientResponse)
  var ambientResponse: Float
    get() = ambientResponseOverride
    set(value) {
      hasAmbientResponseOverride = true
      ambientResponseOverride = value
    }

  private var hasTintOverride by mutableStateOf(false)
  private var tintOverride by mutableStateOf(GlassDefaults.tint)
  var tint: Color
    get() = tintOverride
    set(value) {
      hasTintOverride = true
      tintOverride = value
    }

  private var hasEdgeSoftnessOverride by mutableStateOf(false)
  private var edgeSoftnessOverride by mutableStateOf(GlassDefaults.edgeSoftness)
  var edgeSoftness: Dp
    get() = edgeSoftnessOverride
    set(value) {
      hasEdgeSoftnessOverride = true
      edgeSoftnessOverride = value
    }

  private var hasLightPositionOverride by mutableStateOf(false)
  private var lightPositionOverride by mutableStateOf(Offset.Unspecified)
  var lightPosition: Offset
    get() = lightPositionOverride
    set(value) {
      hasLightPositionOverride = true
      lightPositionOverride = value
    }

  private var hasChromaticAberrationStrengthOverride by mutableStateOf(false)
  private var chromaticAberrationStrengthOverride by mutableStateOf(
    GlassDefaults.chromaticAberrationStrength,
  )
  var chromaticAberrationStrength: Float
    get() = chromaticAberrationStrengthOverride
    set(value) {
      hasChromaticAberrationStrengthOverride = true
      chromaticAberrationStrengthOverride = value
    }

  private var hasSurfaceProfileOverride by mutableStateOf(false)
  private var surfaceProfileOverride by mutableStateOf(GlassDefaults.surfaceProfile)
  var surfaceProfile: SurfaceProfile
    get() = surfaceProfileOverride
    set(value) {
      hasSurfaceProfileOverride = true
      surfaceProfileOverride = value
    }

  private var hasChromaticAberrationModeOverride by mutableStateOf(false)
  private var chromaticAberrationModeOverride by mutableStateOf(
    GlassDefaults.chromaticAberrationMode,
  )
  var chromaticAberrationMode: ChromaticAberrationMode
    get() = chromaticAberrationModeOverride
    set(value) {
      hasChromaticAberrationModeOverride = true
      chromaticAberrationModeOverride = value
    }

  private var hasShapeOverride by mutableStateOf(false)
  private var shapeOverride by mutableStateOf(GlassDefaults.shape)
  var shape: RoundedCornerShape
    get() = shapeOverride
    set(value) {
      hasShapeOverride = true
      shapeOverride = value
    }

  private var hasAlphaOverride by mutableStateOf(false)
  private var alphaOverride by mutableStateOf(GlassDefaults.alpha)
  var alpha: Float
    get() = alphaOverride
    set(value) {
      hasAlphaOverride = true
      alphaOverride = value
    }

  private var hasContrastOverride by mutableStateOf(false)
  private var contrastOverride by mutableStateOf(GlassDefaults.contrast)
  var contrast: Float
    get() = contrastOverride
    set(value) {
      hasContrastOverride = true
      contrastOverride = value
    }

  private var hasWhitePointOverride by mutableStateOf(false)
  private var whitePointOverride by mutableStateOf(GlassDefaults.whitePoint)
  var whitePoint: Float
    get() = whitePointOverride
    set(value) {
      hasWhitePointOverride = true
      whitePointOverride = value
    }

  private var hasChromaMultiplierOverride by mutableStateOf(false)
  private var chromaMultiplierOverride by mutableStateOf(GlassDefaults.chromaMultiplier)
  var chromaMultiplier: Float
    get() = chromaMultiplierOverride
    set(value) {
      hasChromaMultiplierOverride = true
      chromaMultiplierOverride = value
    }

  private var hasContentNormalBlendOverride by mutableStateOf(false)
  private var contentNormalBlendOverride by mutableStateOf(GlassDefaults.contentNormalBlend)
  var contentNormalBlend: Float
    get() = contentNormalBlendOverride
    set(value) {
      hasContentNormalBlendOverride = true
      contentNormalBlendOverride = value
    }

  private var hasSpecularExponentOverride by mutableStateOf(false)
  private var specularExponentOverride by mutableStateOf(GlassDefaults.specularExponent)
  var specularExponent: Float
    get() = specularExponentOverride
    set(value) {
      hasSpecularExponentOverride = true
      specularExponentOverride = value
    }

  private var hasFresnelExponentOverride by mutableStateOf(false)
  private var fresnelExponentOverride by mutableStateOf(GlassDefaults.fresnelExponent)
  var fresnelExponent: Float
    get() = fresnelExponentOverride
    set(value) {
      hasFresnelExponentOverride = true
      fresnelExponentOverride = value
    }

  var interactionSource: InteractionSource? by mutableStateOf(null)
  var interactionLightRadiusFraction: Float by mutableStateOf(
    GlassDefaults.interactionLightRadiusFraction,
  )
  var interactionTransformTarget: GlassTransformTarget by mutableStateOf(
    GlassTransformTarget.MaterialOnly,
  )
  var interactionTransformPivot: GlassTransformPivot by mutableStateOf(
    GlassTransformPivot.Pointer,
  )
  var interactionPositionAnimationSpec: FiniteAnimationSpec<Offset> by mutableStateOf(
    GlassDefaults.positionAnimationSpec,
  )
  var interactionReducedMotionPolicy: GlassReducedMotionPolicy by mutableStateOf(
    GlassReducedMotionPolicy.System,
  )

  val resolvedStyle: GlassStyle
    get() {
      val baseStyle = style
      val resolvedOpticsOverride = opticsOverride.takeIf { hasOpticsOverride }
      val resolvedSpecularIntensityOverride =
        specularIntensityOverride.takeIf { hasSpecularIntensityOverride }
      val resolvedAmbientResponseOverride =
        ambientResponseOverride.takeIf { hasAmbientResponseOverride }
      val resolvedTintOverride = tintOverride.takeIf { hasTintOverride }
      val resolvedEdgeSoftnessOverride = edgeSoftnessOverride.takeIf { hasEdgeSoftnessOverride }
      val resolvedLightPositionOverride = lightPositionOverride.takeIf { hasLightPositionOverride }
      val resolvedChromaticAberrationStrengthOverride =
        chromaticAberrationStrengthOverride.takeIf { hasChromaticAberrationStrengthOverride }
      val resolvedSurfaceProfileOverride =
        surfaceProfileOverride.takeIf { hasSurfaceProfileOverride }
      val resolvedChromaticAberrationModeOverride =
        chromaticAberrationModeOverride.takeIf { hasChromaticAberrationModeOverride }
      val resolvedShapeOverride = shapeOverride.takeIf { hasShapeOverride }
      val resolvedAlphaOverride = alphaOverride.takeIf { hasAlphaOverride }
      val resolvedContrastOverride = contrastOverride.takeIf { hasContrastOverride }
      val resolvedWhitePointOverride = whitePointOverride.takeIf { hasWhitePointOverride }
      val resolvedChromaMultiplierOverride =
        chromaMultiplierOverride.takeIf { hasChromaMultiplierOverride }
      val resolvedContentNormalBlendOverride =
        contentNormalBlendOverride.takeIf { hasContentNormalBlendOverride }
      val resolvedSpecularExponentOverride =
        specularExponentOverride.takeIf { hasSpecularExponentOverride }
      val resolvedFresnelExponentOverride =
        fresnelExponentOverride.takeIf { hasFresnelExponentOverride }
      val resolvedInteractionLightRadiusFraction = interactionLightRadiusFraction
      val resolvedInteractionPositionAnimationSpec = interactionPositionAnimationSpec
      return baseStyle.then {
        interactionLightRadiusFraction(resolvedInteractionLightRadiusFraction)
        interactionPositionAnimationSpec(resolvedInteractionPositionAnimationSpec)
        resolvedOpticsOverride?.let(::optics)
        resolvedSpecularIntensityOverride?.let(::specularIntensity)
        resolvedAmbientResponseOverride?.let(::ambientResponse)
        resolvedTintOverride?.let(::tint)
        resolvedEdgeSoftnessOverride?.let(::edgeSoftness)
        resolvedLightPositionOverride?.let(::lightPosition)
        resolvedChromaticAberrationStrengthOverride?.let(::chromaticAberrationStrength)
        resolvedSurfaceProfileOverride?.let(::surfaceProfile)
        resolvedChromaticAberrationModeOverride?.let(::chromaticAberrationMode)
        resolvedShapeOverride?.let(::shape)
        resolvedAlphaOverride?.let(::alpha)
        resolvedContrastOverride?.let(::contrast)
        resolvedWhitePointOverride?.let(::whitePoint)
        resolvedChromaMultiplierOverride?.let(::chromaMultiplier)
        resolvedContentNormalBlendOverride?.let(::contentNormalBlend)
        resolvedSpecularExponentOverride?.let(::specularExponent)
        resolvedFresnelExponentOverride?.let(::fresnelExponent)
      }
    }

  fun hovered(block: GlassInteractionScope.() -> Unit) {
    style = style.then { hovered(block) }
  }

  fun focused(block: GlassInteractionScope.() -> Unit) {
    style = style.then { focused(block) }
  }

  fun pressed(block: GlassInteractionScope.() -> Unit) {
    style = style.then { pressed(block) }
  }
}

internal fun GlassTestConfiguration.applyTestHoverAndPressResponses() {
  hovered {
    lightingIntensity(0.35f)
    refractionMultiplier(1.02f)
    whitePointDelta(0.01f)
    scale(1f)
  }
  pressed {
    lightingIntensity(1f)
    refractionMultiplier(1.08f)
    whitePointDelta(0.04f)
    scale(0.98f)
  }
}

internal fun Modifier.hazeGlass(
  input: HazeInput,
  configuration: GlassTestConfiguration,
  sampling: HazeSampling = HazeSampling.Default,
): Modifier = typedHazeGlass(
  input = input,
  style = configuration.resolvedStyle,
  sampling = sampling,
  interactionSource = configuration.interactionSource,
  interactionTransformTarget = configuration.interactionTransformTarget,
  interactionTransformPivot = configuration.interactionTransformPivot,
  interactionReducedMotionPolicy = configuration.interactionReducedMotionPolicy,
)
