// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi

@Suppress("ktlint:standard:property-naming")
internal object RegularGlassStyleValues {
  val optics: GlassOptics = GlassOptics.Adaptive
  const val specularIntensity: Float = 0.4f
  const val ambientResponse: Float = 0.46f
  val edgeSoftness: Dp = 2.dp
  const val chromaticAberrationStrength: Float = 0f
  val surfaceProfile: SurfaceProfile = SurfaceProfile.Circle
  val chromaticAberrationMode: ChromaticAberrationMode = ChromaticAberrationMode.Simple
  const val contrast: Float = 0f
  const val whitePoint: Float = 0f
  const val chromaMultiplier: Float = 1f
  const val contentNormalBlend: Float = 0.15f
  const val specularExponent: Float = 24f
  const val fresnelExponent: Float = 3f
}

/** Default values used by `hazeGlass` and [GlassStyle] resolution. */
@ExperimentalHazeApi
@Suppress("ConstPropertyName", "ktlint:standard:property-naming")
public object GlassDefaults {
  /** Default light radius recorded by [style] as a fraction of the material's shortest side. */
  public const val interactionLightRadiusFraction: Float = 0.7f

  /** Default animation used for focus and [InteractionSource]-derived positions. */
  public val positionAnimationSpec: FiniteAnimationSpec<Offset> = spring(
    dampingRatio = 1f,
    stiffness = Spring.StiffnessMedium,
  )

  /** Default geometry-aware Haze optical material. */
  public val optics: GlassOptics = RegularGlassStyleValues.optics

  /** Default intensity of specular highlights, in the range `0f..1f`. */
  public const val specularIntensity: Float = RegularGlassStyleValues.specularIntensity

  /** Default strength of the ambient lighting response and Fresnel accent, in `0f..1f`. */
  public const val ambientResponse: Float = RegularGlassStyleValues.ambientResponse

  /** Default color composited behind captured content before Glass optics are applied. */
  public val backgroundColor: Color = Color.Transparent

  /** Default tint applied to refracted content. */
  public val tint: Color = Color.Transparent

  /** Default softening distance for the glass boundary. */
  public val edgeSoftness: Dp = RegularGlassStyleValues.edgeSoftness

  /** Default chromatic aberration strength, where `0f` disables dispersion. */
  public const val chromaticAberrationStrength: Float =
    RegularGlassStyleValues.chromaticAberrationStrength

  /** Default rounded-rectangle boundary used for refraction and masking. */
  public val shape: RoundedCornerShape = RoundedCornerShape(16.dp)

  /** Default cross-section profile used by the refraction bezel. */
  public val surfaceProfile: SurfaceProfile = RegularGlassStyleValues.surfaceProfile

  /** Default quality mode used to render chromatic aberration. */
  public val chromaticAberrationMode: ChromaticAberrationMode =
    RegularGlassStyleValues.chromaticAberrationMode

  /** Default overall opacity, in the range `0f..1f`. */
  public const val alpha: Float = 1f

  /** Default contrast adjustment, in the range `-1f..1f`. */
  public const val contrast: Float = RegularGlassStyleValues.contrast

  /** Default white-point adjustment, in the range `-1f..1f`. */
  public const val whitePoint: Float = RegularGlassStyleValues.whitePoint

  /** Default chroma multiplier, in the range `0f..2f`. */
  public const val chromaMultiplier: Float = RegularGlassStyleValues.chromaMultiplier

  /** Default blend between generated surface normals and captured content normals. */
  public const val contentNormalBlend: Float = RegularGlassStyleValues.contentNormalBlend

  /** Default exponent controlling the concentration of specular highlights. */
  public const val specularExponent: Float = RegularGlassStyleValues.specularExponent

  /** Default exponent controlling the falloff of the Fresnel response. */
  public const val fresnelExponent: Float = RegularGlassStyleValues.fresnelExponent

  /**
   * Complete default [GlassStyle].
   *
   * Glass evaluates this Style before [LocalGlassStyle] and an explicit modifier Style.
   */
  public val style: GlassStyle = GlassStyle.regular.then {
    interactionLightRadiusFraction(interactionLightRadiusFraction)
    interactionPositionAnimationSpec(positionAnimationSpec)
    backgroundColor(backgroundColor)
    tint(tint)
    shape(shape)
    alpha(alpha)
  }
}
