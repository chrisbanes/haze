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

/** Default values used by `hazeGlass` and [GlassStyle] resolution. */
@ExperimentalHazeApi
@Suppress("ConstPropertyName", "ktlint:standard:property-naming")
public object GlassDefaults {
  /** Default interaction light radius as a fraction of the material's shortest side. */
  public const val interactionLightRadiusFraction: Float = 0.7f

  /** Default animation used for focus and [InteractionSource]-derived positions. */
  public val positionAnimationSpec: FiniteAnimationSpec<Offset> = spring(
    dampingRatio = 1f,
    stiffness = Spring.StiffnessMedium,
  )

  /** Default size-aware Haze optical material. */
  public val optics: GlassOptics = GlassOptics(
    // Compact controls retain refracted detail; larger surfaces fully diffuse the backdrop.
    depth = OpticalSizeValue.Responsive(
      OpticalSizePoint(64.dp, 0.65f),
      OpticalSizePoint(176.dp, 1f),
      OpticalSizePoint(220.dp, 1f),
    ),
    blurRadius = OpticalSizeValue.Responsive(
      OpticalSizePoint(64.dp, 20.dp),
      OpticalSizePoint(176.dp, 24.dp),
      OpticalSizePoint(220.dp, 25.dp),
    ),
    refractionDisplacement = 48.dp,
    refractionProfile = RefractionProfile.Edge(20.dp),
    refractionHeightFraction = 0.15f,
    refractionFoldStrength = 0f,
    refractionDetailIntensity = 0f,
  )

  /** Default intensity of specular highlights, in the range `0f..1f`. */
  public const val specularIntensity: Float = 0.48f

  /** Default dark edge composited with the directional specular highlight. */
  public val edgeShadow: Color = Color.Black.copy(alpha = 0.14f)

  /** Default strength of the ambient lighting response and Fresnel accent, in `0f..1f`. */
  public const val ambientResponse: Float = 0.15f

  /** Default color composited behind captured content before Glass optics are applied. */
  public val backgroundColor: Color = Color.Transparent

  /** Default tint applied to refracted content. */
  public val tint: Color = Color.Transparent

  /** Default softening distance for the glass boundary. */
  public val edgeSoftness: Dp = 1.dp

  /** Default chromatic aberration strength, where `0f` disables dispersion. */
  public const val chromaticAberrationStrength: Float = 0f

  /** Default rounded-rectangle boundary used for refraction and masking. */
  public val shape: RoundedCornerShape = RoundedCornerShape(16.dp)

  /** Default cross-section profile used by the refraction bezel. */
  public val surfaceProfile: SurfaceProfile = SurfaceProfile.Circle

  /** Default quality mode used to render chromatic aberration. */
  public val chromaticAberrationMode: ChromaticAberrationMode = ChromaticAberrationMode.Simple

  /** Default overall opacity, in the range `0f..1f`. */
  public const val alpha: Float = 1f

  /** Default contrast adjustment, in the range `-1f..1f`. */
  public const val contrast: Float = 0f

  /** Default white-point adjustment, in the range `-1f..1f`. */
  public const val whitePoint: Float = 0.55f

  /** Default chroma multiplier, in the range `0f..2f`. */
  public const val chromaMultiplier: Float = 1.5f

  /** Default blend between generated surface normals and captured content normals. */
  public const val contentNormalBlend: Float = 0.15f

  /** Default exponent controlling the concentration of specular highlights. */
  public const val specularExponent: Float = 24f

  /** Default exponent controlling the falloff of the Fresnel response. */
  public const val fresnelExponent: Float = 3f
}
